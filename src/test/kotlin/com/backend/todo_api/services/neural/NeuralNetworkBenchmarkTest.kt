package com.backend.todo_api.services.neural

import com.backend.todo_api.data.repository.NeuronRepository
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
    fun `triathlon comparison of 5D topologies`() {
        val topologiesToTest = mapOf(
             "1. Drei Blätter / Drei Äste" to "neural-topology-three-branches.json",
             "2. Vier Blätter with 2 inputs und kat-blat/ 3 Äste" to "neural-topology-4-leaves-with-2-inputs-3-branches.json"
        )

        for ((name, fileName) in topologiesToTest) {
            neuronRepository.deleteAll()

            println("\n==================================================")
            println("🏁 Wettbewerb: $name ($fileName)")
            println("==================================================")

            val network = NeuralNetwork(neuronRepository, fileName)
            network.initNetwork()
            val inputSize = network.getRequiredInputSize()

            // 800 Epochen Training
            repeat(800) {
                val batch = create5DBalancedBatch()
                for ((sample, target) in batch) {
                    network.train(sample, target)
                }
            }

            val goodInput = doubleArrayOf(0.8, 0.7, 0.9, 0.6, 0.2)
            val badInput  = doubleArrayOf(0.2, 0.3, 0.1, 0.4, 0.9)

            val goodScore = network.predict(goodInput)
            val badScore  = network.predict(badInput)
            val margin = goodScore - badScore

            println("📊 Endergebnis [$name]:")
            println("   - Good Score: $goodScore")
            println("   - Bad Score:  $badScore")
            println("   - Trennschärfe (Margin): $margin")
        }
    }

    private fun create5DBalancedBatch(): List<Pair<DoubleArray, Double>> {
        return listOf(
            Pair(doubleArrayOf(0.8, 0.8, 0.9, 0.7, 0.2), 1.0),
            Pair(doubleArrayOf(0.2, 0.2, 0.1, 0.3, 0.9), 0.0),
            Pair(doubleArrayOf(0.85, 0.75, 0.8, 0.6, 0.2), 1.0),
            Pair(doubleArrayOf(0.15, 0.25, 0.2, 0.2, 0.9), 0.0)
        )
    }
}