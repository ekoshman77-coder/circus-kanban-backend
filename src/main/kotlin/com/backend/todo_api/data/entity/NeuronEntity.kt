package com.backend.todo_api.data.entity


import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "neurons")
class NeuronEntity(
    @Id
    val id: String = UUID.randomUUID().toString(), // "N1_ENERGY", "N2_WORKTIME", ..., "N5_ROOT"

    @Column(nullable = false)
    var bias: Double = 0.0,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "neuron_weights", joinColumns = [JoinColumn(name = "neuron_id")])
    @OrderColumn(name = "weight_index")
    @Column(name = "weight_value")
    var weights: MutableList<Double> = mutableListOf()
)