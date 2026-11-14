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
        every { neuronRepository.deleteAll() } answers { dbStorage.clear() }

        every { neuronRepository.saveAll(any<Iterable<NeuronEntity>>()) } answers {
            val entities = firstArg<Iterable<NeuronEntity>>().toList()
            entities.forEach { dbStorage[it.id] = it }
            entities
        }

        // 🌟 Wir laden hier nun unseren neuen Gewinner: Die Drei-Äste-Spezial-Topologie
        neuralNetwork = NeuralNetwork(neuronRepository, "neural-topology.json")
        neuralNetwork.initNetwork()
    }

    @Test
    fun `neural network should learn category-aware preferences stably`() {
        neuralNetwork.resetTrainingSteps()
        val inputSize = neuralNetwork.getRequiredInputSize() // Sollte 5 sein

        // 🚀 Training mit Fokus auf das 5. Feature (Kategorie)
        repeat(1500) {
            val batch = mutableListOf<Pair<DoubleArray, Double>>()

            repeat(5) {
                // Good Sample: Hohe Energie/Effort-Passung UND bevorzugte Kategorie (z.B. Sensorik = 0.2)
                val goodSample = doubleArrayOf(
                    Random.nextDouble(0.7, 1.0),
                    Random.nextDouble(0.7, 1.0),
                    Random.nextDouble(0.7, 1.0),
                    Random.nextDouble(0.6, 1.0),
                    0.2 // Bevorzugte Kategorie (z.B. Pause/Sensorisch)
                )

                // Bad Sample: Schlechte Passung ODER ungewollte Kategorie (z.B. Kognitiv = 0.9 bei niedriger Energie)
                val badSample = doubleArrayOf(
                    Random.nextDouble(0.0, 0.3),
                    Random.nextDouble(0.0, 0.3),
                    Random.nextDouble(0.0, 0.3),
                    Random.nextDouble(0.0, 0.4),
                    0.9 // Ungematchte Kategorie
                )

                batch.add(goodSample to 0.9)
                batch.add(badSample to 0.1)
            }

            batch.shuffle()

            for ((sample, target) in batch) {
                neuralNetwork.train(sample, target)
            }
        }

        // 📊 Evaluierung mit gezielten Kategorie-Unterschieden
        val testGood = doubleArrayOf(0.8, 0.8, 0.8, 0.7, 0.2) // Gute Werte + Sensorisch
        val testBad  = doubleArrayOf(0.2, 0.2, 0.2, 0.3, 0.9) // Schlechte Werte + Kognitiv

        val scoreGood = neuralNetwork.predict(testGood)
        val scoreBad  = neuralNetwork.predict(testBad)

        println("----------------------------------------")
        println("🟢 Finaler Good-Test-Score (Sensorisch/Gut): $scoreGood")
        println("🔴 Finaler Bad-Test-Score  (Kognitiv/Schlecht): $scoreBad")

        assertTrue(scoreGood > scoreBad, "Good Score ($scoreGood) muss höher sein als Bad Score ($scoreBad)")
    }

    @Test
    fun `neural network robustness test with 5D category inputs`() {
        val runs = 10
        var successCount = 0

        println("🧪 Starte Robustheits-Test über $runs Durchläufe mit 5 Inputs...")

        repeat(runs) { run ->
            dbStorage.clear()
            neuralNetwork.initNetwork()
            neuralNetwork.resetTrainingSteps()

            repeat(5000) {
                val batch = mutableListOf<Pair<DoubleArray, Double>>()
                repeat(5) {
                    val goodSample = doubleArrayOf(0.8, 0.8, 0.8, 0.7, 0.2)
                    val badSample  = doubleArrayOf(0.2, 0.2, 0.2, 0.3, 0.9)
                    batch.add(goodSample to 0.9)
                    batch.add(badSample to 0.1)
                }
                batch.shuffle()
                for ((sample, target) in batch) {
                    neuralNetwork.train(sample, target)
                }
            }

            val scoreGood = neuralNetwork.predict(doubleArrayOf(0.8, 0.8, 0.8, 0.7, 0.2))
            val scoreBad  = neuralNetwork.predict(doubleArrayOf(0.2, 0.2, 0.2, 0.3, 0.9))

            val passed = scoreGood > scoreBad
            if (passed) successCount++

            println("Run ${run + 1}: Good=$scoreGood, Bad=$scoreBad -> ${if (passed) "✅ BESTANDEN" else "❌ GESCHEITERT"}")
        }

        println("----------------------------------------")
        println("🎯 Ergebnis: $successCount von $runs Durchläufen erfolgreich.")

        assertTrue(successCount >= (runs * 0.8).toInt(), "Das Netz ist zu instabil ($successCount/$runs erfolgreich).")
    }
}