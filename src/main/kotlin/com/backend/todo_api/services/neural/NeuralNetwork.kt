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
    private val leafIndicesMap = mutableMapOf<String, List<Int>>() // Speichert die Indizes pro Blatt
    private val createdNeuronsMap = mutableMapOf<String, Neuron>()
    private val objectMapper = ObjectMapper()

    val learningRate: Double = 0.2
     var steps = 0

    @PostConstruct
    fun initNetwork() {
        val resource = ClassPathResource(configFileName)
        val topology = objectMapper.readValue(resource.inputStream, NetworkTopology::class.java)

        val existingEntities = neuronRepository.findAll().associateBy { it.id }.toMutableMap()

        // 🔍 TOPOLOGIE-VALIDIERUNG: Fehlt eine Entity ODER passt die Gewichtsanzahl nicht?
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

        // 1. Entities laden und Indizes für Blätter merken
        topology.nodes.forEach { config ->
            val weightCount = if (config.type == NodeType.LEAF) config.inputCount else config.children.size

            val entity = existingEntities[config.id] ?: NeuronEntity(
                id = config.id,
                bias = Random.nextDouble(-1.0, 1.0),
                weights = MutableList(weightCount) { Random.nextDouble(-1.5, 1.5) }
            )
            allEntitiesMap[config.id] = entity

            if (config.type == NodeType.LEAF) {
                leafIndicesMap[config.id] = config.inputIndices
            }
        }

        // 2. Blätter erzeugen
        topology.nodes.filter { config -> config.type == NodeType.LEAF }.forEach { config ->
            val entity = allEntitiesMap[config.id]!!
            createdNeuronsMap[config.id] = Neuron(entity = entity)
        }

        // 3. Äste und Wurzel erzeugen
        topology.nodes.filter { config -> config.type != NodeType.LEAF }.forEach { config ->
            val entity = allEntitiesMap[config.id]!!
            val childNeurons = config.children.map { childId ->
                createdNeuronsMap[childId]
                    ?: throw IllegalStateException("Child-Neuron '$childId' nicht gefunden!")
            }
            createdNeuronsMap[config.id] = Neuron(entity = entity, inputNeurons = childNeurons)
        }

        val rootConfig = topology.nodes.firstOrNull { config -> config.type == NodeType.ROOT }
            ?: throw IllegalStateException("Kein ROOT-Node gefunden!")

        rootNeuron = createdNeuronsMap[rootConfig.id]!!

        // 🎓 SCHULUNG / INITIAL-TRAINING BEI NEUSTART BZW. TOPOLOGIE-WECHSEL
        if (isFreshStart) {
            println("🎓 Starte automatisches Grund-Training für das neue Netz...")
            runBaselineTraining()
            println("✅ Grund-Training abgeschlossen! Gewichte wurden in der DB gespeichert.")
        } else {
            println("✅ Bestehendes Netz mit passender Topologie aus der Datenbank geladen.")
        }
    }

    /**
     * Führt ein automatisches Grund-Training aus, um dem Netz nach einem Topologie-Wechsel
     * eine erste solide Basis/Tendenz mitzugeben.
     */
    private fun runBaselineTraining() {
        resetTrainingSteps()
        val inputSize = getRequiredInputSize()

        repeat(1000) {
            val batch = listOf(
                DoubleArray(inputSize) { Random.nextDouble(0.7, 1.0) } to 0.9,
                DoubleArray(inputSize) { Random.nextDouble(0.0, 0.3) } to 0.1
            )
            for ((sample, target) in batch) {
                train(sample, target)
            }
        }
    }

    /**
     * Führt den Forward-Pass aus, indem es die globalen Inputs für die Blätter passend zuschneidet.
     */
    fun predict(inputs: DoubleArray): Double {
        // Wir können die Blätter vorab mit ihren spezifischen Slices versorgen oder beim Forward übergeben.
        // Am einfachsten: Wir leiten die zugeschnittenen Arrays beim Aufruf durch.
        return forwardRecursive(rootNeuron, inputs)
    }

    private fun forwardRecursive(neuron: Neuron, rawInputs: DoubleArray): Double {
        // Wenn das Neuron ein Blatt ist (keine inputNeurons hat), schneiden wir das Array anhand der JSON-Indizes zu!
        val actualInputs = if (neuron.inputNeurons.isEmpty()) {
            val indices = leafIndicesMap[neuron.id] ?: rawInputs.indices.toList()
            DoubleArray(indices.size) { i -> rawInputs[indices[i]] }
        } else {
            rawInputs
        }
        return neuron.forward(actualInputs)
    }

    fun train(inputs: DoubleArray, target: Double) {
        steps++
        val output = predict(inputs)
        val outputError = target - output
        val rootDelta = outputError * rootNeuron.getActivationDerivative()

        backpropagateRecursive(rootNeuron, inputs, rootDelta, learningRate)

        neuronRepository.saveAll(allEntitiesMap.values)
    }

    private fun backpropagateRecursive(neuron: Neuron, rawInputs: DoubleArray, delta: Double, learningRate: Double) {
        if (neuron.inputNeurons.isEmpty()) {
            // 🍃 BLATT: Hier schneiden wir die Inputs exakt wie beim Forward-Pass zu!
            val indices = leafIndicesMap[neuron.id] ?: rawInputs.indices.toList()
            val actualInputs = DoubleArray(indices.size) { i -> rawInputs[indices[i]] }
            neuron.backpropagate(actualInputs, delta, learningRate)
        } else {
            // 🌳 AST / WURZEL: Rekursion durch die Kinder
            for (i in neuron.inputNeurons.indices) {
                val child = neuron.inputNeurons[i]
                val childDelta = delta * neuron.entity.weights[i] * child.getActivationDerivative()
                backpropagateRecursive(child, rawInputs, childDelta, learningRate)
                neuron.entity.weights[i] += learningRate * delta * child.lastOutput
            }
            neuron.entity.bias += learningRate * delta * (0.005) // biasDivider
        }
    }

    fun resetTrainingSteps() {
        steps = 0
    }
    /**
     * Ermittelt dynamisch aus der Topologie, wie viele Roh-Inputs (Array-Größe)
     * das Netz mindestens benötigt (höchster Index + 1).
     */
    fun getRequiredInputSize(): Int {
        val maxIndex = leafIndicesMap.values.flatten().maxOrNull() ?: 0
        return maxIndex + 1
    }
}