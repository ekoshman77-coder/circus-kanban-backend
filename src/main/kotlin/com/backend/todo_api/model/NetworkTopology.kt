package com.backend.todo_api.model

enum class NodeType { LEAF, BRANCH, ROOT }

// Beschreibt EIN Element im "nodes"-Array aus dem JSON
data class NodeConfig(
    val id: String = "",
    val type: NodeType = NodeType.LEAF,
    val inputCount: Int = 1,
    val inputIndices: List<Int> = emptyList(), // 🌟 NEU: Welche Indizes aus rawInputs?
    val children: List<String> = emptyList()
)

// Beschreibt das gesamte JSON-Objekt
data class NetworkTopology(
    val name: String = "",
    val nodes: List<NodeConfig> = emptyList()
)