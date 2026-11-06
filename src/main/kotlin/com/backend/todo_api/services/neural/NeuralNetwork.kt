package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import com.backend.todo_api.data.repository.NeuronRepository
import com.backend.todo_api.model.NetworkTopology
import com.backend.todo_api.model.NodeType
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class NeuralNetwork(
    private val neuronRepository: NeuronRepository,
    @Value("\${neural.topology.file}") private val configFileName: String
) {
    private lateinit var rootNeuron: Neuron
    private val allEntitiesMap = mutableMapOf<String, NeuronEntity>()
    private val createdNeuronsMap = mutableMapOf<String, Neuron>()
    private val objectMapper = ObjectMapper()

    val learningRate: Double = 0.2
    var steps = 0

    @PostConstruct
    fun initNetwork() {
        val resource = ClassPathResource(configFileName)
        val topology = objectMapper.readValue(resource.inputStream, NetworkTopology::class.java)

        val existingEntities = neuronRepository.findAll().associateBy { it.id }.toMutableMap()

        // 🔍 TOPOLOGIE-VALIDIERUNG
        val hasMismatch = topology.nodes.any { config ->
            val expectedWeightCount = if (config.type == NodeType.LEAF) config.inputCount else config.children.size
            val entity = existingEntities[config.id]
            entity == null || entity.weights.size != expectedWeightCount
        }

        var isFreshStart = false

        if (hasMismatch) {
            println("⚠️ Topologie-Änderung oder fehlende Neuronen erkannt! DB wird für '$configFileName' zurückgesetzt...")
            neuronRepository.deleteAll()
            existingEntities.clear()
            isFreshStart = true
        } else if (existingEntities.isEmpty()) {
            isFreshStart = true
        }

        // 1. Entities laden
        topology.nodes.forEach { config ->
            val weightCount = if (config.type == NodeType.LEAF) config.inputCount else config.children.size

            val entity = existingEntities[config.id] ?: NeuronEntity(
                id = config.id,
                bias = Random.nextDouble(-1.0, 1.0),
                weights = MutableList(weightCount) { Random.nextDouble(-0.2, 0.2) }
            )
            allEntitiesMap[config.id] = entity
        }

        // 2. Blätter erzeugen (mit Übergabe ihrer spezifischen inputIndices)
        topology.nodes.filter { config -> config.type == NodeType.LEAF }.forEach { config ->
            val entity = allEntitiesMap[config.id]!!
            createdNeuronsMap[config.id] = Neuron(
                entity = entity,
                inputIndices = config.inputIndices,
            )
        }

        // 3. Äste und Wurzel erzeugen
        topology.nodes.filter { config -> config.type != NodeType.LEAF }.forEach { config ->
            val entity = allEntitiesMap[config.id]!!
            val childNeurons = config.children.map { childId ->
                createdNeuronsMap[childId]
                    ?: throw IllegalStateException("Child-Neuron '$childId' nicht gefunden!")
            }

            createdNeuronsMap[config.id] = Neuron(
                entity = entity,
                inputNeurons = childNeurons
            )
        }

        val rootConfig = topology.nodes.firstOrNull { config -> config.type == NodeType.ROOT }
            ?: throw IllegalStateException("Kein ROOT-Node gefunden!")

        rootNeuron = createdNeuronsMap[rootConfig.id]!!

        // 🎓 SCHULUNG / INITIAL-TRAINING
        if (isFreshStart) {
            println("🎓 Starte automatisches Grund-Training für das neue Netz...")
            runBaselineTraining()
            println("✅ Grund-Training abgeschlossen! Gewichte wurden in der DB gespeichert.")
        } else {
            println("✅ Bestehendes Netz mit passender Topologie aus der Datenbank geladen.")
        }
    }

    /**
     * Führt das automatische Grund-Training aus (10 gute + 10 schlechte Beispiele pro Epoche).
     */
    private fun runBaselineTraining() {
        resetTrainingSteps()
        allEntitiesMap.forEach { (id, entity) ->
            println("🔍 [DB-CHECK] Neuron $id Gewichte: ${entity.weights} | Bias: ${entity.bias}")
        }
        val inputSize = getRequiredInputSize()

        repeat(1000) {
            val batch = mutableListOf<Pair<DoubleArray, Double>>()

            // 10x Gute Beispiele (alle Werte hoch) -> Target 0.9
            repeat(10) {
                batch.add(DoubleArray(inputSize) { Random.nextDouble(0.7, 1.0) } to 0.9)
            }

            // 10x Schlechte Beispiele (alle Werte tief) -> Target 0.1
            repeat(10) {
                batch.add(DoubleArray(inputSize) { Random.nextDouble(0.0, 0.3) } to 0.1)
            }

            // Trainieren im Arbeitsspeicher
            for ((sample, target) in batch) {
                train(sample, target)
            }
        }

        // 💾 Einmalig in der DB persistieren
        neuronRepository.saveAll(allEntitiesMap.values)
        println("✅ Ursprüngliches, scharfes Baseline-Training abgeschlossen!")
    }

    /**
     * Führt den Forward-Pass aus. Das Netz übergibt das rohe Array direkt an die Wurzel,
     * und die Neuronen holen sich autonom ihre benötigten Inputs.
     */
    fun predict(inputs: DoubleArray): Double {
        return rootNeuron.forward(inputs)
    }

    fun train(inputs: DoubleArray, target: Double, isOwnFeedback: Boolean = true) {
        steps++
        val output = predict(inputs)
        val outputError = target - output
        val rootDelta = outputError * rootNeuron.getActivationDerivative()

        backpropagateRecursive(rootNeuron, inputs, rootDelta, if (isOwnFeedback) learningRate else learningRate / 10.0)
    }

    private fun backpropagateRecursive(neuron: Neuron, rawInputs: DoubleArray, delta: Double, learningRate: Double) {
        if (neuron.inputNeurons.isEmpty()) {
            // 🍃 BLATT: Delegiert die Rückwärtspropagation an das Neuron selbst
            neuron.backpropagate(rawInputs, delta, learningRate)
        } else {
            // 🌳 AST / WURZEL: Rekursion durch die Kinder
            for (i in neuron.inputNeurons.indices) {
                val child = neuron.inputNeurons[i]
                val childDelta = delta * neuron.entity.weights[i] * child.getActivationDerivative()
                backpropagateRecursive(child, rawInputs, childDelta, learningRate)
                neuron.entity.weights[i] += learningRate * delta * child.lastOutput
            }
            neuron.entity.bias += learningRate * delta * 0.005 // biasDivider
        }
    }

    fun resetTrainingSteps() {
        steps = 0
    }

    /**
     * Ermittelt dynamisch aus allen registrierten Neuronen bzw. der Topologie,
     * wie viele Roh-Inputs (Array-Größe) das Netz mindestens benötigt.
     */
    fun getRequiredInputSize(): Int {
        // Da die Neuronen jetzt ihre inputIndices kennen, können wir das Maximum direkt ermitteln
        // oder alternativ aus den geladenen Neuronen ableiten.
        // Ein robuster Weg über alle Blatt-Neuronen in der createdNeuronsMap:
        val allIndices = createdNeuronsMap.values.flatMap { it.inputIndices }
        val maxIndex = if (allIndices.isNotEmpty()) allIndices.maxOrNull() ?: 0 else 3
        return maxIndex + 1
    }
}