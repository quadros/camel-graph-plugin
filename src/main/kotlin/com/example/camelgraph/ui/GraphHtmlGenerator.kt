package com.example.camelgraph.ui

import com.example.camelgraph.model.CamelNode
import com.example.camelgraph.model.CamelRouteGraph
import com.example.camelgraph.util.SecurityUtils
import com.google.gson.Gson

object GraphHtmlGenerator {

    private val cytoscapeJs: String by lazy {
        GraphHtmlGenerator::class.java.getResourceAsStream("/js/cytoscape.min.js")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Cytoscape.js not found in resources. Make sure /js/cytoscape.min.js exists.")
    }

    fun generateHtml(graph: CamelRouteGraph): String {
        val gson = Gson()
        // Convert our model to Cytoscape elements JSON with sanitization
        val nodesJson = graph.nodes.values.map { 
            mapOf("data" to mapOf(
                "id" to SecurityUtils.sanitizeUri(it.id),
                "label" to SecurityUtils.sanitizeLabel(it.label),
                "type" to it.type.name
            )) 
        }
        val edgesJson = graph.edges.map { 
            mapOf("data" to mapOf(
                "source" to SecurityUtils.sanitizeUri(it.sourceId),
                "target" to SecurityUtils.sanitizeUri(it.targetId),
                "label" to SecurityUtils.sanitizeLabel(it.label)
            )) 
        }
        
        // Cytoscape expects a flat array of elements, not separate nodes/edges
        val elements = nodesJson + edgesJson
        val elementsString = gson.toJson(elements)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';">
                <title>Camel Route Graph</title>
                <script>
                    $cytoscapeJs
                </script>
                <style>
                    body { 
                        font-family: "Comic Sans MS", "Comic Sans", cursive, sans-serif; 
                        margin: 0; 
                        padding: 0; 
                        background-color: #2b2b2b; 
                        color: #a9b7c6; 
                    }
                    #cy { width: 100vw; height: 100vh; display: block; }
                </style>
            </head>
            <body>
                <div id="cy"></div>
                <script>
                    var cy = cytoscape({
                        container: document.getElementById('cy'),
                        elements: $elementsString,
                        style: [
                            {
                                selector: 'node',
                                style: {
                                    'label': 'data(label)',
                                    'background-color': '#666',
                                    'color': '#fff',
                                    'text-valign': 'center',
                                    'text-halign': 'center',
                                    'width': 'label',
                                    'height': 'label',
                                    'padding': '10px',
                                    'shape': 'round-rectangle',
                                    'font-family': 'Comic Sans MS, Comic Sans, cursive, sans-serif',
                                    'font-size': '12px',
                                    'font-weight': 'bold'
                                }
                            },
                            {
                                selector: 'node[type="ROUTE_START"]',
                                style: {
                                    'background-color': '#4CAF50',
                                    'shape': 'ellipse'
                                }
                            },
                            {
                                selector: 'node[type="CHOICE"]',
                                style: {
                                    'background-color': '#FF9800',
                                    'shape': 'diamond',
                                    'width': '80px',
                                    'height': '80px'
                                }
                            },
                            {
                                selector: 'node[type="BEAN"]',
                                style: {
                                    'background-color': '#2196F3',
                                    'shape': 'ellipse'
                                }
                            },
                            {
                                selector: 'node[type="PROCESSOR"]',
                                style: {
                                    'background-color': '#9C27B0',
                                    'shape': 'ellipse'
                                }
                            },
                            {
                                selector: 'node[type="ENDPOINT"]',
                                style: {
                                    'background-color': '#607D8B',
                                    'shape': 'round-rectangle'
                                }
                            },
                            {
                                selector: 'edge',
                                style: {
                                    'width': 3,
                                    'line-color': '#ccc',
                                    'target-arrow-color': '#ccc',
                                    'target-arrow-shape': 'triangle',
                                    'curve-style': 'bezier',
                                    'label': 'data(label)',
                                    'color': '#aaa',
                                    'font-size': '10px',
                                    'font-family': 'Comic Sans MS, Comic Sans, cursive, sans-serif'
                                }
                            }
                        ],
                        layout: {
                            name: 'cose',
                            animate: false
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
