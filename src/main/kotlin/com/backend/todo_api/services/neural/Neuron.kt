package com.backend.todo_api.services.neural

import java.util.UUID
import kotlin.math.exp
import kotlin.random.Random

class Neuron(
    val id: String = UUID.randomUUID().toString(), // 👈 Automatische eindeutige UUID
    val inputNeurons: List<Neuron> = emptyList()
) {
    // Hat es Kinder, braucht es pro Kind 1 Gewicht. Ist es ein Blatt, hat es 1 Gewicht.
    val weightCount = if (inputNeurons.isEmpty()) 1 else inputNeurons.size

    var weights: DoubleArray = DoubleArray(weightCount) { Random.nextDouble(-1.0, 1.0) }
    var bias: Double = Random.nextDouble(-1.0, 1.0)

    var lastOutput: Double = 0.0

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    fun forward(rawInputs: DoubleArray, myIndex: Int = 0): Double {
        var sum = 0.0

        if (inputNeurons.isEmpty()) {
            sum = rawInputs[myIndex] * weights[0]
        } else {
            for (i in inputNeurons.indices) {
                sum += inputNeurons[i].forward(rawInputs, i) * weights[i]
            }
        }

        sum += bias
        lastOutput = sigmoid(sum)
        return lastOutput
    }

    fun backpropagate(delta: Double, learningRate: Double) {
        if (inputNeurons.isEmpty()) {
            weights[0] += learningRate * delta * lastOutput
            bias += learningRate * delta
        } else {
            for (i in inputNeurons.indices) {
                val child = inputNeurons[i]
                val childDelta = delta * weights[i] * child.lastOutput * (1.0 - child.lastOutput)
                child.backpropagate(childDelta, learningRate)

                weights[i] += learningRate * delta * child.lastOutput
            }
            bias += learningRate * delta
        }
    }
}