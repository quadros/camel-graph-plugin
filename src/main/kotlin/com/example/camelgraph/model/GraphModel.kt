package com.example.camelgraph.model

data class CamelNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val parentId: String? = null,
    val filePath: String? = null,
    val lineNumber: Int? = -1
)

enum class NodeType {
    CONTAINER,   // visual grouping (e.g., per class/file)
    ROUTE_START, // from(...)
    ENDPOINT,    // to(...)
    BEAN,        // bean(...)
    PROCESSOR,   // process(...)
    CHOICE,      // choice() / when() / otherwise()
    PARALLEL_PROCESSING, // parallelProcessing()
    DO_CATCH,    // doCatch(...)
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
        val existing = nodes[node.id]
        if (existing == null) {
            nodes[node.id] = node
            return
        }

        // Merge strategy (important for cross-file route linking / container assignment):
        // - Prefer ROUTE_START over ENDPOINT when the same URI is seen in both from() and to()
        // - Prefer non-null parentId/filePath/lineNumber when provided
        val mergedType = when {
            existing.type == NodeType.ROUTE_START || node.type == NodeType.ROUTE_START -> NodeType.ROUTE_START
            existing.type == NodeType.CONTAINER || node.type == NodeType.CONTAINER -> NodeType.CONTAINER
            existing.type == NodeType.ENDPOINT || node.type == NodeType.ENDPOINT -> NodeType.ENDPOINT
            else -> node.type
        }

        val mergedParentId = when {
            node.type == NodeType.ROUTE_START && node.parentId != null -> node.parentId
            existing.type == NodeType.ROUTE_START && existing.parentId != null -> existing.parentId
            else -> node.parentId ?: existing.parentId
        }
        val mergedFilePath = node.filePath ?: existing.filePath
        val mergedLineNumber = when {
            node.lineNumber != null && node.lineNumber != -1 -> node.lineNumber
            else -> existing.lineNumber
        }

        nodes[node.id] = existing.copy(
            label = if (existing.label.isNotBlank()) existing.label else node.label,
            type = mergedType,
            parentId = mergedParentId,
            filePath = mergedFilePath,
            lineNumber = mergedLineNumber
        )
    }

    fun addEdge(sourceId: String, targetId: String, label: String = "") {
        edges.add(CamelEdge(sourceId, targetId, label))
    }
}
