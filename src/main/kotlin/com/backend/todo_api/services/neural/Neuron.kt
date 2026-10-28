package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import kotlin.math.exp
import kotlin.random.Random

class Neuron(
    val entity: NeuronEntity,
    val inputNeurons: List<Neuron> = emptyList()
) {
    // Nimmt automatisch die feste ID aus der Entity (z. B. "ROOT", "LEAF_1", "BRANCH_1")
    val id: String get() = entity.id

    var lastOutput: Double = 0.0
    private var lastSum: Double = 0.0

    // Alpha für Leaky ReLU (0.01 sorgt dafür, dass 1% des Signals im Negativen weiterfließt)
    private val leakyAlpha = 0.01
    private val biasDivider = 0.005

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    private fun leakyRelu(x: Double): Double = if (x > 0.0) x else leakyAlpha * x

    val isRoot: Boolean get() = id.startsWith("ROOT", ignoreCase = true)

    /**
     * Gibt die Ableitung der Aktivierungsfunktion basierend auf dem letzten Output / der ID zurück.
     */
    fun getActivationDerivative(): Double {
        return if (id == "ROOT") {
            // Sigmoid-Ableitung: f'(x) = f(x) * (1 - f(x))
            lastOutput * (1.0 - lastOutput)
        } else {
            // Leaky-ReLU-Ableitung: 1.0 für x > 0, sonst leakyAlpha (0.01)
            if (lastOutput > 0.0) 1.0 else leakyAlpha
        }
    }

    fun forward(rawInputs: DoubleArray): Double {
        var sum = 0.0

        if (inputNeurons.isEmpty()) {
            while (entity.weights.size < rawInputs.size) {
                entity.weights.add(Random.nextDouble(-0.7, 0.7))
            }
            for (i in rawInputs.indices) {
                val weight = if (i < entity.weights.size) entity.weights[i] else 0.0
                sum += rawInputs[i] * weight
            }
        } else {
            for (i in inputNeurons.indices) {
                sum += inputNeurons[i].forward(rawInputs) * entity.weights[i]
            }
        }

        sum += entity.bias
        lastSum = sum // Summe vor der Aktivierung speichern!

        // 🔀 ARCHITEKTUR-SPLIT: Nur die Wurzel nutzt Sigmoid, alles andere Leaky ReLU!
        lastOutput = if (isRoot) {
            sigmoid(sum)
        } else {
            leakyRelu(sum)
        }

        return lastOutput
    }

    fun backpropagate(rawInputs: DoubleArray, delta: Double, learningRate: Double) {
        if (inputNeurons.isEmpty()) {
            val leafDelta = delta * getActivationDerivative()
            while (entity.weights.size < rawInputs.size) {
                entity.weights.add(Random.nextDouble(-0.7, 0.7))
            }
            for (i in rawInputs.indices) {
                if (i < entity.weights.size) {
                    entity.weights[i] += learningRate * leafDelta * rawInputs[i]
                }
            }
            entity.bias += learningRate * leafDelta * biasDivider
        } else {
            for (i in inputNeurons.indices) {
                val child = inputNeurons[i]
                val childDelta = delta * entity.weights[i] * child.getActivationDerivative()
                child.backpropagate(rawInputs, childDelta, learningRate)
                entity.weights[i] += learningRate * delta * child.lastOutput
            }
            entity.bias += learningRate * delta * biasDivider
        }
    }
}