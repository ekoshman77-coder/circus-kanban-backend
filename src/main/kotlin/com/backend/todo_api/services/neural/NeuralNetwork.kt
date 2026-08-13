package com.backend.todo_api.services.neural

import org.springframework.stereotype.Component

@Component
class NeuralNetwork(
    inputSize: Int = 4,
    hiddenSize: Int = 4
) {
    // 1. Die 4 Input-Blätter
    private val inputNodes = List(inputSize) { Neuron() }

    // 2. Hidden Layer verbindet sich mit allen Input-Blättern
    private val hiddenLayer = List(hiddenSize) { Neuron() }

    // 3. Output-Neuron (Wurzel) verbindet sich mit allen Hidden-Neuronen
    private val outputNeuron = Neuron()

    fun predict(inputs: DoubleArray): Double {
        // Welchen Rohwert hat welcher Input-Knoten? Direkt und sauber zuweisen:
        for (i in inputs.indices) {
            inputNodes[i].lastOutput = inputs[i]
        }

        // Rekursiver Durchlauf von der Wurzel gestartet
        return outputNeuron.forward(inputs)
    }

    fun train(inputs: DoubleArray, target: Double, learningRate: Double = 0.1) {
        val output = predict(inputs)

        // Delta an der Wurzel berechnen
        val outputError = target - output
        val rootDelta = outputError * output * (1.0 - output)

        // 🚀 Welle der Rekursion auslösen
        outputNeuron.backpropagate(rootDelta, learningRate)
    }
}