package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import com.backend.todo_api.data.repository.NeuronRepository
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
        neuronRepository = mockk()

        every { neuronRepository.findAll() } answers { dbStorage.values.toList() }

        // 🛠️ Hier stubben wir jetzt auch deleteAll() ab, damit der Mock nicht abstürzt!
        every { neuronRepository.deleteAll() } answers { dbStorage.clear() }

        every { neuronRepository.saveAll(any<Iterable<NeuronEntity>>()) } answers {
            val entities = firstArg<Iterable<NeuronEntity>>().toList()
            entities.forEach { dbStorage[it.id] = it }
            entities
        }

        neuralNetwork = NeuralNetwork(neuronRepository, "neural-topology-parallel.json")
        neuralNetwork.initNetwork()
    }

    @Test
    fun `neural network should learn generalized preference thresholds stably`() {
        neuralNetwork.resetTrainingSteps()
        val inputSize = neuralNetwork.getRequiredInputSize()

        // 🚀 Strukturiertes Epochen-Training mit ausbalancierten Batches
        repeat(1500) { epoch ->
            val batch = mutableListOf<Pair<DoubleArray, Double>>()

            repeat(5) {
                val goodSample = DoubleArray(inputSize) { Random.nextDouble(0.7, 1.0) }
                val badSample  = DoubleArray(inputSize) { Random.nextDouble(0.0, 0.3) }

                batch.add(goodSample to 0.9)
                batch.add(badSample to 0.1)
            }

            batch.shuffle()

            for ((sample, target) in batch) {
                neuralNetwork.train(sample, target)
            }
        }

        // 📊 Evaluierung der Kennlinie
        println("🎯 Testergebnisse nach ausbalanciertem Batch-Training:")
        val testValues = listOf(0.1, 0.3, 0.5, 0.7, 0.9)
        for (v in testValues) {
            val sample = DoubleArray(inputSize) { v }
            val score = neuralNetwork.predict(sample)
            println("📥 Input-Wert $v -> 🧠 Netz-Score: $score")
        }

        val scoreGood = neuralNetwork.predict(DoubleArray(inputSize) { 0.80 })
        val scoreBad  = neuralNetwork.predict(DoubleArray(inputSize) { 0.20 })

        println("----------------------------------------")
        println("🟢 Finaler Good-Test-Score (0.80): $scoreGood")
        println("🔴 Finaler Bad-Test-Score  (0.20): $scoreBad")

        assertTrue(scoreGood > scoreBad, "Good Score ($scoreGood) muss höher sein als Bad Score ($scoreBad)")
    }

    @Test
    fun `neural network robustness test over multiple runs`() {
        val runs = 10
        var successCount = 0
        val inputSize = neuralNetwork.getRequiredInputSize()

        println("🧪 Starte Robustheits-Test über $runs Durchläufe...")

        repeat(runs) { run ->
            // 1. Netz komplett zurücksetzen und mit frischen Gewichten initialisieren
            dbStorage.clear()
            neuralNetwork.initNetwork()
            neuralNetwork.resetTrainingSteps()

            // 2. Training durchführen
            repeat(500) {
                val batch = mutableListOf<Pair<DoubleArray, Double>>()
                repeat(5) {
                    val goodSample = DoubleArray(inputSize) { Random.nextDouble(0.7, 1.0) }
                    val badSample  = DoubleArray(inputSize) { Random.nextDouble(0.0, 0.3) }
                    batch.add(goodSample to 0.9)
                    batch.add(badSample to 0.1)
                }
                batch.shuffle()
                for ((sample, target) in batch) {
                    neuralNetwork.train(sample, target)
                }
            }

            // 3. Evaluierung
            val scoreGood = neuralNetwork.predict(DoubleArray(inputSize) { 0.80 })
            val scoreBad  = neuralNetwork.predict(DoubleArray(inputSize) { 0.20 })

            val passed = scoreGood > scoreBad
            if (passed) successCount++

            println("Run ${run + 1}: Good=$scoreGood, Bad=$scoreBad -> ${if (passed) "✅ BESTANDEN" else "❌ GESCHEITERT"}")
        }

        println("----------------------------------------")
        println("🎯 Ergebnis: $successCount von $runs Durchläufen waren erfolgreich.")

        // Das Netz sollte nun dank Leaky ReLU und Batches extrem stabil laufen
        assertTrue(successCount >= (runs * 0.8).toInt(), "Das Netz ist zu instabil ($successCount/$runs erfolgreich).")
    }
}