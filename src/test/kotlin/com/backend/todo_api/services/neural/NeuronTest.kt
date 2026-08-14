package com.backend.todo_api.services.neural

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class NeuronTest {

//    @Test
//    fun `Neuron forward - Single Leaf with deterministic weight`() {
//        val leaf = Neuron()
//        leaf.weights = doubleArrayOf(2.0)
//        leaf.bias = 0.0
//
//        val inputs = doubleArrayOf(0.5)
//        val result = leaf.forward(inputs, myIndex = 0)
//
//        // Expected: Sigmoid(0.5 * 2.0 + 0) = 1 / (1 + e^-1) = 0.7310585786300049
//        assertEquals(0.7310585, result, 0.000001)
//    }
//
//    @Test
//    fun `NeuralNetwork train - Output gets closer to target after one training step`() {
//        val net = NeuralNetwork(inputSize = 4, hiddenSize = 4)
//        val inputs = doubleArrayOf(0.8, 0.2, 0.5, 0.9)
//        val target = 1.0 // Wir sagen dem Netz: Der Output MUSS nahe 1.0 sein!
//
//        val initialOutput = net.predict(inputs)
//        val initialError = Math.abs(target - initialOutput)
//
//        // Ein Trainingsschritt durchführen
//        net.train(inputs, target, learningRate = 0.5)
//
//        val newOutput = net.predict(inputs)
//        val newError = Math.abs(target - newOutput)
//
//        // Der neue Fehler MUSS kleiner sein als der alte Fehler!
//        assertTrue(newError < initialError, "Error should decrease after training step. Old: $initialError, New: $newError")
//    }
//
//    @Test
//    fun `NeuralNetwork train - Learns a pattern and reduces error significantly`() {
//        // Arrange
//        val net = NeuralNetwork(inputSize = 4, hiddenSize = 4)
//        val urgentInputs = doubleArrayOf(1.0, 0.9, 1.0, 0.8)
//        val target = 1.0
//
//        val initialOutput = net.predict(urgentInputs)
//        val initialError = Math.abs(target - initialOutput)
//
//        // Act: 150 Wiederholungen trainieren
//        repeat(150) {
//            net.train(urgentInputs, target, learningRate = 0.3)
//        }
//
//        val finalOutput = net.predict(urgentInputs)
//        val finalError = Math.abs(target - finalOutput)
//
//        // Assert: Der Fehler muss sich drastisch reduziert haben (z.B. geviertelt)
//        assertTrue(
//            finalError < (initialError * 0.25),
//            "Error was not reduced sufficiently. Initial error: $initialError, Final error: $finalError"
//        )
//    }
}