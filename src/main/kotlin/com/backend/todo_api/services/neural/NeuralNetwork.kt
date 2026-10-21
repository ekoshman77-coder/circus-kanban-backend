package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import com.backend.todo_api.data.repository.NeuronRepository
import com.backend.todo_api.model.NetworkTopology
import com.backend.todo_api.model.NodeType
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class NeuralNetwork(
    private val neuronRepository: NeuronRepository
) {
    private lateinit var rootNeuron: Neuron
    private val allEntitiesMap = mutableMapOf<String, NeuronEntity>()
    private val objectMapper = ObjectMapper()

    val learningRate: Double = 0.35

    @PostConstruct
    fun initNetwork() {
        // 1. JSON-Topologie aus resources/neural-topology.json einlesen
        val resource = ClassPathResource("neural-topology.json")
        val topology = objectMapper.readValue(resource.inputStream, NetworkTopology::class.java)

        // 2. BATCH-LOAD: Alle existierenden Entities mit EINEM Query aus der DB holen
        val existingEntities = neuronRepository.findAll().associateBy { it.id }

        // 3. Fehlende Entities initialisieren (für neue Knoten aus dem JSON)
        topology.nodes.forEach { config ->
            val weightCount = if (config.type == NodeType.LEAF) config.inputCount else config.children.size

            val entity = existingEntities[config.id] ?: NeuronEntity(
                id = config.id,
                bias = Random.nextDouble(-0.5, 0.5),
                weights = MutableList(weightCount) { Random.nextDouble(-0.7, 0.7) }
            )
            allEntitiesMap[config.id] = entity
        }

        // 4. In-Memory Neuronen instanziieren und verdrahten
        val createdNeuronsMap = mutableMapOf<String, Neuron>()

        // 4a. Erst die Blätter erzeugen
        topology.nodes.filter { it.type == NodeType.LEAF }.forEach { config ->
            val entity = allEntitiesMap[config.id]!!
            createdNeuronsMap[config.id] = Neuron(entity = entity)
        }

        // 4b. Dann Äste und Wurzel mit ihren Kindern verdrahten
        topology.nodes.filter { it.type != NodeType.LEAF }.forEach { config ->
            val entity = allEntitiesMap[config.id]!!
            val childNeurons = config.children.map { childId ->
                createdNeuronsMap[childId]
                    ?: throw IllegalStateException("Child-Neuron '$childId' für Node '${config.id}' nicht in Topologie gefunden!")
            }
            createdNeuronsMap[config.id] = Neuron(entity = entity, inputNeurons = childNeurons)
        }

        // 4c. Die Wurzel identifizieren
        val rootConfig = topology.nodes.firstOrNull { it.type == NodeType.ROOT }
            ?: throw IllegalStateException("Kein Node vom Typ 'ROOT' in neural-topology.json definiert!")

        rootNeuron = createdNeuronsMap[rootConfig.id]!!

        println("🧠 [NEURAL] Netz '${topology.name}' erfolgreich aufgebaut! Wurzel-ID: ${rootNeuron.id}")
    }

    /**
     * Berechnet den Score für 4 Roh-Inputs (z.B. [energy, timeLeft, importance, snoozeHistory])
     */
    fun predict(inputs: DoubleArray): Double {
        return rootNeuron.forward(inputs)
    }

    /**
     * Führt Backpropagation aus und speichert die neuen Gewichte per Batch-Save in der DB.
     */
    fun train(inputs: DoubleArray, target: Double) {
        val output = predict(inputs)

        // Delta an der Wurzel berechnen
        val outputError = target - output
        val rootDelta = outputError * output * (1.0 - output)

        // 🚀 Welle der Backpropagation durch den RAM-Baum senden
        rootNeuron.backpropagate(inputs, rootDelta, learningRate)

        // 💾 BATCH-SAVE: Alle aktualisierten Entities in einem Rutsch in der DB speichern
        neuronRepository.saveAll(allEntitiesMap.values)
        println("💾 [NEURAL] Gewichte von ${allEntitiesMap.size} Neuronen erfolgreich in der DB gespeichert!")
    }
}