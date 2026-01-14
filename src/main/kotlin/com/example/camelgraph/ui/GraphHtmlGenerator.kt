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
                    
                    /* Zoom Controls */
                    .zoom-controls {
                        position: absolute;
                        top: 20px;
                        right: 20px;
                        z-index: 1000;
                        display: flex;
                        flex-direction: column;
                        gap: 5px;
                    }
                    
                    .zoom-btn {
                        width: 40px;
                        height: 40px;
                        background-color: #3c3c3c;
                        border: 2px solid #555;
                        border-radius: 5px;
                        color: #a9b7c6;
                        font-size: 20px;
                        font-weight: bold;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-family: "Comic Sans MS", "Comic Sans", cursive, sans-serif;
                        transition: all 0.2s;
                    }
                    
                    .zoom-btn:hover {
                        background-color: #4c4c4c;
                        border-color: #666;
                        color: #fff;
                    }
                    
                    .zoom-btn:active {
                        background-color: #2c2c2c;
                        transform: scale(0.95);
                    }
                    
                    .zoom-btn:disabled {
                        opacity: 0.5;
                        cursor: not-allowed;
                    }
                    
                    .zoom-reset {
                        width: 40px;
                        height: 30px;
                        background-color: #3c3c3c;
                        border: 2px solid #555;
                        border-radius: 5px;
                        color: #a9b7c6;
                        font-size: 12px;
                        font-weight: bold;
                        cursor: pointer;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-family: "Comic Sans MS", "Comic Sans", cursive, sans-serif;
                        transition: all 0.2s;
                        margin-top: 5px;
                    }
                    
                    .zoom-reset:hover {
                        background-color: #4c4c4c;
                        border-color: #666;
                        color: #fff;
                    }
                    
                    .zoom-reset:active {
                        background-color: #2c2c2c;
                        transform: scale(0.95);
                    }
                </style>
            </head>
            <body>
                <div id="cy"></div>
                <div class="zoom-controls">
                    <button class="zoom-btn" id="zoom-in" title="Zoom In">+</button>
                    <button class="zoom-btn" id="zoom-out" title="Zoom Out">−</button>
                    <button class="zoom-reset" id="zoom-fit" title="Fit to Screen">Fit</button>
                </div>
                <script>
                    var cy = cytoscape({
                        container: document.getElementById('cy'),
                        elements: $elementsString,
                        minZoom: 0.1,
                        maxZoom: 3.0,
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
                    
                    // Zoom controls
                    var zoomInBtn = document.getElementById('zoom-in');
                    var zoomOutBtn = document.getElementById('zoom-out');
                    var zoomFitBtn = document.getElementById('zoom-fit');
                    
                    // Zoom in
                    zoomInBtn.addEventListener('click', function() {
                        var currentZoom = cy.zoom();
                        var newZoom = Math.min(currentZoom + 0.2, 3.0);
                        cy.zoom(newZoom);
                        updateZoomButtons();
                    });
                    
                    // Zoom out
                    zoomOutBtn.addEventListener('click', function() {
                        var currentZoom = cy.zoom();
                        var newZoom = Math.max(currentZoom - 0.2, 0.1);
                        cy.zoom(newZoom);
                        updateZoomButtons();
                    });
                    
                    // Fit to screen
                    zoomFitBtn.addEventListener('click', function() {
                        cy.fit(cy.elements(), 50);
                        updateZoomButtons();
                    });
                    
                    // Update button states based on current zoom
                    function updateZoomButtons() {
                        var currentZoom = cy.zoom();
                        zoomInBtn.disabled = (currentZoom >= 3.0);
                        zoomOutBtn.disabled = (currentZoom <= 0.1);
                    }
                    
                    // Enforce zoom limits on wheel and pinch events
                    cy.on('zoom', function() {
                        var currentZoom = cy.zoom();
                        if (currentZoom < 0.1) {
                            cy.zoom(0.1);
                        } else if (currentZoom > 3.0) {
                            cy.zoom(3.0);
                        }
                        updateZoomButtons();
                    });
                    
                    // Initialize button states
                    updateZoomButtons();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
