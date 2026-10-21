package com.backend.todo_api.services.neural

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import com.backend.todo_api.data.entity.NeuronEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NeuronTest {

    @Test
    fun `Neuron forward - Single Leaf with deterministic weight`() {
        // Arrange: Wir erstellen eine passende NeuronEntity mit unseren Test-Werten
        val entity = NeuronEntity(
            id = "LEAF_1",
            weights = mutableListOf(2.0),
            bias = 0.0
        )
        val leaf = Neuron(entity = entity)

        val inputs = doubleArrayOf(0.5)

        // Act
        val result = leaf.forward(inputs)

        // Assert: Ein Blatt nutzt standardmäßig Leaky ReLU (da ID != "ROOT").
        // Rechnung hier: x = (0.5 * 2.0) + 0.0 = 1.0. Da 1.0 > 0, bleibt es 1.0.
        // Wenn es ein Root-Neuron wäre, wäre es Sigmoid. Testen wir hier das Leaky ReLU:
        assertEquals(1.0, result, 0.000001)
    }

    @Test
    fun `Neuron forward - Root with child neurons and sigmoid activation`() {
        // Arrange: Wir bauen ein Kind-Neuron (Blatt) mit einem Gewicht
        val leafEntity = com.backend.todo_api.data.entity.NeuronEntity(
            id = "LEAF_CHILD",
            weights = mutableListOf(1.5),
            bias = 0.0
        )
        val childLeaf = Neuron(entity = leafEntity)

        // Wurzel-Neuron muss explizit die ID "ROOT" haben, damit Sigmoid greift
        val rootEntity = com.backend.todo_api.data.entity.NeuronEntity(
            id = "ROOT",
            weights = mutableListOf(2.0),
            bias = 0.0
        )
        val rootNeuron = Neuron(
            entity = rootEntity,
            inputNeurons = listOf(childLeaf)
        )

        val inputs = doubleArrayOf(0.5)

        // Act
        // 1. Kind verarbeitet Rohinput: 0.5 * 1.5 = 0.75 (Leaky ReLU -> 0.75)
        // 2. Wurzel verarbeitet Kind-Output: 0.75 * 2.0 + 0.0 = 1.5
        // 3. Wurzel wendet Sigmoid an: 1 / (1 + e^-1.5) ≈ 0.817574476
        val result = rootNeuron.forward(inputs)

        // Assert
        assertEquals(0.817574476, result, 0.000001)
    }
}