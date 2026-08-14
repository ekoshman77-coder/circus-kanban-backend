package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import com.backend.todo_api.data.repository.NeuronRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class NeuralNetworkLearningTest {

    private lateinit var neuronRepository: NeuronRepository
    private lateinit var neuralNetwork: NeuralNetwork
    private val dbStorage = mutableMapOf<String, NeuronEntity>()

    @BeforeEach
    fun setUp() {
        // MockK verwenden
        neuronRepository = mockk()

        // DB-Verhalten simulieren: Entities im Speicher behalten
        every { neuronRepository.findAll() } answers { dbStorage.values.toList() }
        every { neuronRepository.saveAll(any<Iterable<NeuronEntity>>()) } answers {
            val entities = firstArg<Iterable<NeuronEntity>>().toList() // 👈 in List umwandeln!
            entities.forEach { dbStorage[it.id] = it }
            entities // gibt jetzt eine List<NeuronEntity> zurück
        }

        neuralNetwork = NeuralNetwork(neuronRepository)
        neuralNetwork.initNetwork()
    }

    @Test
    fun `neural network should learn user preference reproducibly`() {
        // 🎯 Festes, kontrolliertes Trainings-Set (4 Inputs: dueDate, effort, energyMatch, workingTime)
        val trainingData = listOf(
            // Klar GUTE Todos (energyMatch ist hoch) -> Target: 1.0
            doubleArrayOf(0.8, 0.2, 0.9, 0.7) to 1.0,
            doubleArrayOf(0.3, 0.5, 0.8, 0.9) to 1.0,
            doubleArrayOf(0.9, 0.9, 0.7, 0.4) to 1.0,

            // Klar SCHLECHTE Todos (energyMatch ist niedrig) -> Target: 0.0
            doubleArrayOf(0.8, 0.2, 0.1, 0.7) to 0.0,
            doubleArrayOf(0.3, 0.5, 0.3, 0.9) to 0.0,
            doubleArrayOf(0.9, 0.9, 0.2, 0.4) to 0.0
        )

        // 🚀 TRAINING: Wir gehen die festen Beispiele durch
        repeat(50) {
            for ((inputs, target) in trainingData) {
                neuralNetwork.train(inputs, target)
            }
        }

        // 🧪 TEST-PHASE: Feste Evaluierung mit NEUEN (unbekannten) Beispielen

        // Ein neues, GUTES Todo (energyMatch = 0.85)
        val goodTodoInputs = doubleArrayOf(0.5, 0.3, 0.85, 0.6)
        val goodScore = neuralNetwork.predict(goodTodoInputs)

        // Ein neues, SCHLECHTES Todo (energyMatch = 0.15)
        val badTodoInputs = doubleArrayOf(0.5, 0.3, 0.15, 0.6)
        val badScore = neuralNetwork.predict(badTodoInputs)

        println("📊 Good Score: $goodScore | Bad Score: $badScore")

        // Assertions
        assertTrue(goodScore > badScore, "Good Todo Score ($goodScore) sollte höher sein als Bad Score ($badScore)")
        assertTrue(goodScore > 0.6, "Good Score sollte hoch sein")
        assertTrue(badScore < 0.4, "Bad Score sollte niedrig sein")
    }
}