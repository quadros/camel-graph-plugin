package com.example.camelgraph.model

data class CamelNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val filePath: String? = null,
    val lineNumber: Int? = -1
)

enum class NodeType {
    ROUTE_START, // from(...)
    ENDPOINT,    // to(...)
    BEAN,        // bean(...)
    PROCESSOR,   // process(...)
    CHOICE,      // choice() / when() / otherwise()
    UNKNOWN
}

data class CamelEdge(
    val sourceId: String,
    val targetId: String,
    val label: String = ""
)

data class CamelRouteGraph(
    val nodes: MutableMap<String, CamelNode> = mutableMapOf(),
    val edges: MutableList<CamelEdge> = mutableListOf()
) {
    fun addNode(node: CamelNode) {
        if (!nodes.containsKey(node.id)) {
            nodes[node.id] = node
        }
    }

    fun addEdge(sourceId: String, targetId: String, label: String = "") {
        edges.add(CamelEdge(sourceId, targetId, label))
    }
}
