package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import com.backend.todo_api.data.repository.NeuronRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class NeuralNetworkBenchmarkTest {

    @Autowired
    private lateinit var neuronRepository: NeuronRepository

    @Test
    fun `compare all three topologies with automatic rollback`() {
        val topologiesToTest = mapOf(
            "1. Spezialisiert (Zeit vs. Energie)" to "neural-topology-specialized.json",
            "2. Vollzugriff (Jedes Blatt sieht alles)" to "neural-topology-full.json",
            "3. Paralleles Ensemble (V6)" to "neural-topology-parallel.json"
        )

        for ((name, fileName) in topologiesToTest) {
            // Tabelle vor jedem Netz-Durchlauf leeren
            neuronRepository.deleteAll()

            println("\n--------------------------------------------------")
            println("🔬 In-Memory DB Benchmark für: $name ($fileName)")
            println("--------------------------------------------------")

            // Netz mit der jeweiligen Topologie erzeugen
            val network = NeuralNetwork(neuronRepository, fileName)
            network.initNetwork()

            // 500 Epochen Training
            repeat(500) {
                val batch = createBalancedBatch(network.getRequiredInputSize())
                for ((sample, target) in batch) {
                    network.train(sample, target)
                }
            }

            // Metriken testen
            val goodInput = DoubleArray(network.getRequiredInputSize()) { 0.8 }
            val badInput  = DoubleArray(network.getRequiredInputSize()) { 0.2 }

            val goodScore = network.predict(goodInput)
            val badScore  = network.predict(badInput)
            val margin = goodScore - badScore

            println("📊 Ergebnis [$name]:")
            println("   - Good Score (0.8): $goodScore")
            println("   - Bad Score  (0.2): $badScore")
            println("   - Trennschärfe (Margin): $margin")
        }
    }

    private fun createBalancedBatch(inputSize: Int): List<Pair<DoubleArray, Double>> {
        return listOf(
            Pair(DoubleArray(inputSize) { 0.8 }, 1.0),
            Pair(DoubleArray(inputSize) { 0.2 }, 0.0),
            Pair(DoubleArray(inputSize) { 0.85 }, 1.0),
            Pair(DoubleArray(inputSize) { 0.15 }, 0.0)
        )
    }
}