package com.backend.todo_api.services.neural

import com.backend.todo_api.data.entity.NeuronEntity
import kotlin.math.exp

class Neuron(
    val entity: NeuronEntity,
    val inputNeurons: List<Neuron> = emptyList()
) {
    // Nimmt automatisch die feste ID aus der Entity (z. B. "ROOT", "LEAF_1", "BRANCH_1")
    val id: String get() = entity.id

    var lastOutput: Double = 0.0

    // Alpha für Leaky ReLU (0.01 sorgt dafür, dass 1% des Signals im Negativen weiterfließt)
    private val leakyAlpha = 0.01
    private val biasDivider = 0.005

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    private fun leakyRelu(x: Double): Double = if (x > 0.0) x else leakyAlpha * x

    /**
     * Gibt die Ableitung der Aktivierungsfunktion basierend auf dem letzten Output / der ID zurück.
     */
    private fun getActivationDerivative(): Double {
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
            // 🍃 BLATT: Verarbeitet ALLE rawInputs mit seinen eigenen Gewichten!
            for (i in rawInputs.indices) {
                sum += rawInputs[i] * entity.weights[i]
            }
        } else {
            // 🌳 WURZEL / AST: Summiert die Ergebnisse seiner Kinder
            for (i in inputNeurons.indices) {
                sum += inputNeurons[i].forward(rawInputs) * entity.weights[i]
            }
        }

        sum += entity.bias

        // 🎯 AKTIVIERUNGSFUNKTION WÄHLEN:
        // Wurzel bekommt Sigmoid (0.0 bis 1.0), alle anderen (Äste & Blätter) Leaky ReLU
        lastOutput = if (id == "ROOT") {
            sigmoid(sum)
        } else {
            leakyRelu(sum)
        }

        return lastOutput
    }

    fun backpropagate(rawInputs: DoubleArray, delta: Double, learningRate: Double) {
        if (inputNeurons.isEmpty()) {
            // 🍃 BLATT: Wendet die Leaky-ReLU-Steigung an
            val leafDelta = delta * getActivationDerivative()

            // Passt ALLE Gewichte basierend auf den jeweiligen Rohinputs an
            for (i in rawInputs.indices) {
                entity.weights[i] += learningRate * leafDelta * rawInputs[i]
            }
            entity.bias += learningRate * leafDelta * biasDivider
        } else {
            // 🌳 WURZEL / AST: Reicht Fehler an Kinder weiter
            for (i in inputNeurons.indices) {
                val child = inputNeurons[i]

                // Fehler für das Kind: delta von oben * Gewicht * Ableitung der Aktivierung des Kindes
                val childDelta = delta * entity.weights[i] * child.getActivationDerivative()
                child.backpropagate(rawInputs, childDelta, learningRate)

                // Eigenes Gewicht für diese Verbindung anpassen
                entity.weights[i] += learningRate * delta * child.lastOutput
            }
            entity.bias += learningRate * delta * biasDivider
        }
    }
}