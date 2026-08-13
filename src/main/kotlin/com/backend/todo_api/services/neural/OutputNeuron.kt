package com.backend.todo_api.services.neural

import kotlin.math.exp
import kotlin.random.Random

class OutputNeuron(hiddenCount: Int) {
    // 1. Erstelle die Gewichte (weights) für alle hiddenCount Verbindungen + Bias
    val weights: DoubleArray = DoubleArray(hiddenCount){ Random.nextDouble(-1.0, 1.0) }
    val bias: Double = Random.nextDouble(-1.0, 1.0)

    // 2. Variable für lastOutput speichern (wie beim HiddenNeuron)
    var lastOutput: Double = 0.0
        private set

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    // 3. Methode predict(hiddenOutputs: DoubleArray): Double
    // Hier wird das Ergebnis aus den Werten der Hidden-Neuronen berechnet!
    fun predict(hiddenOutputs: DoubleArray): Double {
        var sum = 0.0
        for (i in 0..hiddenOutputs.size) {
            sum += hiddenOutputs[i] * weights[i]
        }
        sum += bias
        lastOutput = sigmoid(sum)
        return lastOutput
    }
}