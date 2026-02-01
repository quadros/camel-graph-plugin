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
            val data = mutableMapOf<String, Any>(
                "id" to SecurityUtils.sanitizeUri(it.id),
                "label" to SecurityUtils.sanitizeLabel(it.label),
                "type" to it.type.name
            )
            if (!it.parentId.isNullOrBlank()) {
                data["parent"] = SecurityUtils.sanitizeUri(it.parentId)
            }
            mapOf("data" to data)
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
                                selector: 'node[type="CONTAINER"]',
                                style: {
                                    'background-color': '#3c3c3c',
                                    'background-opacity': 0.15,
                                    'border-width': 2,
                                    'border-color': '#666',
                                    'shape': 'round-rectangle',
                                    'text-valign': 'top',
                                    'text-halign': 'center',
                                    'color': '#ffffff',
                                    'font-size': '20px',
                                    'font-weight': 'bold',
                                    'padding': '26px',
                                    // Title highlight
                                    'text-background-color': '#1976D2',
                                    'text-background-opacity': 1,
                                    'text-background-padding': '6px',
                                    'text-margin-y': 6
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
                                selector: 'node[type="DO_CATCH"]',
                                style: {
                                    'background-color': '#E91E63',
                                    'shape': 'round-rectangle'
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
                                selector: 'node[type="PARALLEL_PROCESSING"]',
                                style: {
                                    'background-color': '#1976D2',
                                    'shape': 'round-rectangle',
                                    'color': '#ffffff'
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
                                    'font-size': '13px',
                                    'font-family': 'Comic Sans MS, Comic Sans, cursive, sans-serif',
                                    // Improve readability of choice conditions on edges
                                    'text-margin-y': -12,
                                    'text-background-color': '#2b2b2b',
                                    'text-background-opacity': 1,
                                    'text-background-padding': '3px',
                                    'text-border-color': '#666',
                                    'text-border-width': 1,
                                    'text-wrap': 'wrap',
                                    'text-max-width': '220px'
                                }
                            }
                        ],
                        layout: {
                            name: 'cose',
                            animate: false,
                            // Keep default COSE params; we rely on a post-layout packing step for containers.
                        }
                    });

                    function packContainers() {
                        // Post-process to ensure container bounding boxes do not overlap.
                        // We translate each container's descendants to a grid layout.
                        var containers = cy.nodes('node[type="CONTAINER"]');
                        if (!containers || containers.length === 0) return;

                        // Use a conservative spacing so boxes don't touch.
                        var spacingX = 140;
                        var spacingY = 140;
                        var padding = 60;

                        // Sort by size (bigger first) for more stable packing.
                        containers = containers.sort(function(a, b) {
                            var ba = a.boundingBox({ includeLabels: true });
                            var bb = b.boundingBox({ includeLabels: true });
                            var areaA = (ba.w || (ba.x2 - ba.x1)) * (ba.h || (ba.y2 - ba.y1));
                            var areaB = (bb.w || (bb.x2 - bb.x1)) * (bb.h || (bb.y2 - bb.y1));
                            return areaB - areaA;
                        });

                        var viewport = cy.extent();
                        var maxRowWidth = Math.max(1200, (viewport.x2 - viewport.x1) || 1200);

                        var cursorX = viewport.x1 + padding;
                        var cursorY = viewport.y1 + padding;
                        var rowHeight = 0;

                        cy.startBatch();
                        try {
                            containers.forEach(function(c) {
                                var bb = c.boundingBox({ includeLabels: true });
                                var w = (bb.x2 - bb.x1);
                                var h = (bb.y2 - bb.y1);

                                if (cursorX + w + padding > viewport.x1 + maxRowWidth) {
                                    cursorX = viewport.x1 + padding;
                                    cursorY = cursorY + rowHeight + spacingY;
                                    rowHeight = 0;
                                }

                                var targetX1 = cursorX;
                                var targetY1 = cursorY;
                                var dx = targetX1 - bb.x1;
                                var dy = targetY1 - bb.y1;

                                // Move descendants; compound bounds will follow.
                                c.descendants().forEach(function(n) {
                                    var p = n.position();
                                    n.position({ x: p.x + dx, y: p.y + dy });
                                });

                                cursorX = cursorX + w + spacingX;
                                rowHeight = Math.max(rowHeight, h);
                            });
                        } finally {
                            cy.endBatch();
                        }
                    }

                    function prioritizeFromNodes() {
                        // Place ROUTE_START nodes at the top of each container for easier scanning.
                        var containers = cy.nodes('node[type="CONTAINER"]');
                        if (!containers || containers.length === 0) return;

                        cy.startBatch();
                        try {
                            containers.forEach(function(c) {
                                var bb = c.boundingBox({ includeLabels: true });
                                var fromNodes = c.children('node[type="ROUTE_START"]');
                                if (!fromNodes || fromNodes.length === 0) return;

                                var topY = bb.y1 + 90;          // leave room for title highlight
                                var startX = bb.x1 + 140;        // small left margin
                                var stepX = 220;                 // horizontal spacing between multiple froms

                                fromNodes.forEach(function(n, idx) {
                                    n.position({
                                        x: startX + (idx * stepX),
                                        y: topY
                                    });
                                });
                            });
                        } finally {
                            cy.endBatch();
                        }
                    }

                    function layoutContainersBreadthfirst() {
                        // Apply a deterministic layout inside each container so spacing between nodes looks consistent.
                        // This avoids the "random" spacing that force-directed layouts can produce.
                        var containers = cy.nodes('node[type="CONTAINER"]');
                        if (!containers || containers.length === 0) return;

                        containers.forEach(function(c) {
                            var nodes = c.descendants();
                            if (!nodes || nodes.length === 0) return;

                            // Only include internal edges (both ends inside this container) for the local layout.
                            var edges = nodes.connectedEdges().filter(function(e) {
                                return nodes.contains(e.source()) && nodes.contains(e.target());
                            });

                            var eles = nodes.union(edges);
                            var roots = c.children('node[type="ROUTE_START"]');

                            try {
                                eles.layout({
                                    name: 'breadthfirst',
                                    directed: true,
                                    padding: 40,
                                    spacingFactor: 1.6,
                                    animate: false,
                                    // Put roots at the top (this satisfies the "FROM on top" requirement)
                                    roots: (roots && roots.length > 0) ? roots : undefined,
                                    circle: false,
                                    grid: true
                                }).run();
                            } catch (e) {
                                // ignore layout errors; graph remains usable
                            }
                        });
                    }

                    function prioritizeParallelNodes() {
                        // Ensure PARALLEL_PROCESSING nodes appear above their derived to()/toD() targets,
                        // but only when both the parallel node and its targets are inside the same container.
                        var containers = cy.nodes('node[type="CONTAINER"]');
                        if (!containers || containers.length === 0) return;

                        var deltaY = 140;   // vertical gap between parallel and its targets
                        var stepX = 220;    // horizontal spacing between targets

                        cy.startBatch();
                        try {
                            containers.forEach(function(c) {
                                var internalNodes = c.descendants();
                                if (!internalNodes || internalNodes.length === 0) return;

                                var parallelNodes = c.children('node[type="PARALLEL_PROCESSING"]')
                                    .union(c.descendants('node[type="PARALLEL_PROCESSING"]'));

                                parallelNodes.forEach(function(pn) {
                                    // Targets inside the same container only
                                    var targets = pn.outgoers('edge').targets().filter(function(t) {
                                        return internalNodes.contains(t);
                                    });
                                    if (!targets || targets.length === 0) return;

                                    // Only adjust if parallel is not already above the minimum target Y
                                    var minTargetY = null;
                                    targets.forEach(function(t) {
                                        var ty = t.position('y');
                                        minTargetY = (minTargetY == null) ? ty : Math.min(minTargetY, ty);
                                    });
                                    var desiredParallelY = (minTargetY != null) ? (minTargetY - deltaY) : pn.position('y');
                                    if (pn.position('y') > desiredParallelY) {
                                        // Center parallel X above its targets
                                        var sumX = 0;
                                        targets.forEach(function(t) { sumX += t.position('x'); });
                                        var centerX = sumX / targets.length;
                                        pn.position({ x: centerX, y: desiredParallelY });

                                        // Place targets on a row below, centered under the parallel node
                                        var startX = centerX - ((targets.length - 1) * stepX) / 2;
                                        var targetY = desiredParallelY + deltaY;
                                        targets.forEach(function(t, idx) {
                                            t.position({ x: startX + (idx * stepX), y: targetY });
                                        });
                                    }
                                });
                            });
                        } finally {
                            cy.endBatch();
                        }
                    }

                    function enableContainerDrag() {
                        // Cytoscape core doesn't automatically move children when dragging a compound node.
                        // IMPORTANT: do NOT use evt.position for compounds. For compound nodes, evt.position may effectively
                        // track the node's own position/bounding box, which changes as we move children -> feedback loop -> teleports.
                        // Use rendered pointer position (evt.renderedPosition) and convert delta to model coordinates via zoom.
                        cy.on('grab', 'node[type="CONTAINER"]', function(evt) {
                            var c = evt.target;

                            // If the node is locked, Cytoscape will not allow dragging.
                            // We temporarily unlock it for the duration of the drag and restore on 'free'.
                            var wasLocked = false;
                            try { wasLocked = !!c.locked(); } catch (e) { wasLocked = false; }
                            if (wasLocked) {
                                try { c.unlock(); } catch (e) { /* ignore */ }
                            }

                            var rp = evt.renderedPosition;
                            if (!rp) {
                                // Fallback: approximate from originalEvent if available
                                var oe = evt.originalEvent;
                                rp = oe ? { x: oe.clientX || 0, y: oe.clientY || 0 } : { x: 0, y: 0 };
                            }
                            c.scratch('_containerDrag', { lastRenderedPos: { x: rp.x, y: rp.y }, restoreLock: wasLocked });
                        });

                        cy.on('drag', 'node[type="CONTAINER"]', function(evt) {
                            var c = evt.target;
                            var data = c.scratch('_containerDrag');
                            if (!data) return;

                            var rp = evt.renderedPosition;
                            if (!rp) return;

                            var last = data.lastRenderedPos || rp;
                            var dxRendered = rp.x - last.x;
                            var dyRendered = rp.y - last.y;

                            // Update reference immediately (incremental)
                            data.lastRenderedPos = { x: rp.x, y: rp.y };
                            c.scratch('_containerDrag', data);

                            // Convert rendered delta to model delta.
                            // Rendered coords scale with zoom; model coords do not.
                            var z = cy.zoom();
                            var dx = dxRendered / (z || 1);
                            var dy = dyRendered / (z || 1);

                            cy.startBatch();
                            try {
                                c.descendants().forEach(function(n) {
                                    var p = n.position();
                                    n.position({ x: p.x + dx, y: p.y + dy });
                                });
                            } finally {
                                cy.endBatch();
                            }
                        });

                        cy.on('free', 'node[type="CONTAINER"]', function(evt) {
                            var c = evt.target;
                            var data = c.scratch('_containerDrag');
                            c.removeScratch('_containerDrag');
                            if (data && data.restoreLock) {
                                try { c.lock(); } catch (e) { /* ignore */ }
                            }
                        });
                    }

                    // Ensure the graph is visible on first render and containers don't overlap.
                    try {
                        cy.ready(function() {
                            // Let the initial layout settle, then pack compounds.
                            setTimeout(function() {
                                layoutContainersBreadthfirst();
                                prioritizeParallelNodes();
                                packContainers();
                                cy.fit(cy.elements(), 80);
                            }, 0);
                        });
                    } catch (e) { /* ignore */ }

                    enableContainerDrag();
                    
                    // Zoom controls
                    var zoomInBtn = document.getElementById('zoom-in');
                    var zoomOutBtn = document.getElementById('zoom-out');
                    var zoomFitBtn = document.getElementById('zoom-fit');

                    // Trackpad pinch-to-zoom (especially on macOS) often arrives as a wheel event with ctrlKey=true.
                    // In embedded Chromium (JCEF), it can be interpreted as "page zoom" and not reach Cytoscape.
                    // Capture at window level (capture=true) and, when the event originates over the graph, zoom Cytoscape.
                    (function enablePinchZoom() {
                        var el = cy.container();
                        if (!el) return;

                        function clampZoom(z) {
                            return Math.min(3.0, Math.max(0.1, z));
                        }

                        function renderedPosFromEvent(e) {
                            var rect = el.getBoundingClientRect();
                            var cx = (typeof e.clientX === 'number') ? e.clientX : rect.left + rect.width / 2;
                            var cyy = (typeof e.clientY === 'number') ? e.clientY : rect.top + rect.height / 2;
                            return { x: cx - rect.left, y: cyy - rect.top };
                        }

                        function zoomByWheelDelta(deltaY, e) {
                            var current = cy.zoom();
                            // Smooth exponential scaling; deltaY > 0 => zoom out, deltaY < 0 => zoom in
                            var factor = Math.exp(-deltaY * 0.002);
                            var newZoom = clampZoom(current * factor);
                            cy.zoom({ level: newZoom, renderedPosition: renderedPosFromEvent(e) });
                            updateZoomButtons();
                        }

                        function isEventOverGraph(e) {
                            var rect = el.getBoundingClientRect();
                            var cx = (typeof e.clientX === 'number') ? e.clientX : null;
                            var cyy = (typeof e.clientY === 'number') ? e.clientY : null;
                            if (cx == null || cyy == null) return false;
                            return (cx >= rect.left && cx <= rect.right && cyy >= rect.top && cyy <= rect.bottom);
                        }

                        function wheelHandler(e) {
                            // In JCEF the event target may not be a child of #cy even when the pointer is over it.
                            // Use coordinates to decide.
                            if (!isEventOverGraph(e)) return;

                            // Pinch gesture commonly sets ctrlKey=true in Chromium
                            if (!e.ctrlKey) return;

                            // Prevent browser/page zoom and stop it from being handled elsewhere
                            e.preventDefault();
                            e.stopPropagation();
                            zoomByWheelDelta(e.deltaY || 0, e);
                        }

                        // Capture true so we can intercept before Chromium applies page zoom.
                        // Use document to improve reliability in embedded browsers.
                        document.addEventListener('wheel', wheelHandler, { passive: false, capture: true });

                        // Fallback for environments that expose gesture events (some WebKit-based implementations)
                        var lastScale = 1;
                        function gestureStart(e) {
                            if (!el.contains(e.target)) return;
                            lastScale = e.scale || 1;
                            e.preventDefault();
                            e.stopPropagation();
                        }
                        function gestureChange(e) {
                            if (!el.contains(e.target)) return;
                            var s = e.scale || 1;
                            var ratio = (lastScale && s) ? (s / lastScale) : 1;
                            lastScale = s;
                            e.preventDefault();
                            e.stopPropagation();
                            var newZoom = clampZoom(cy.zoom() * ratio);
                            cy.zoom({ level: newZoom, renderedPosition: renderedPosFromEvent(e) });
                            updateZoomButtons();
                        }
                        document.addEventListener('gesturestart', gestureStart, { passive: false, capture: true });
                        document.addEventListener('gesturechange', gestureChange, { passive: false, capture: true });
                    })();
                    
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
