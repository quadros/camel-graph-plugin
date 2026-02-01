package com.example.camelgraph.ui

import com.example.camelgraph.model.CamelNode
import com.example.camelgraph.model.CamelRouteGraph
import com.example.camelgraph.model.NodeType
import com.example.camelgraph.service.CamelRouteService
import com.example.camelgraph.util.SecurityUtils
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class CamelGraphToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val graphService = project.service<CamelRouteService>()
        
        val panel = JPanel(BorderLayout())
        val browser = JBCefBrowser()
        
        val refreshButton = JButton("Refresh Graph")
        refreshButton.addActionListener {
            // Disable button during processing to prevent multiple simultaneous calls
            refreshButton.isEnabled = false
            refreshButton.text = "Refreshing..."
            
            try {
                if (DumbService.isDumb(project)) {
                    val indexingHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <title>Camel Route Graph</title>
                            <style>
                                body { 
                                    font-family: sans-serif; 
                                    margin: 0; 
                                    padding: 50px; 
                                    background-color: #2b2b2b; 
                                    color: #a9b7c6; 
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    height: 100vh;
                                }
                                .message { text-align: center; font-size: 18px; }
                            </style>
                        </head>
                        <body>
                            <div class="message">
                                <h2>Indexing Project...</h2>
                                <p>Please wait for IntelliJ to finish indexing and then refresh.</p>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    browser.loadHTML(indexingHtml)
                    DumbService.getInstance(project).runWhenSmart { refreshButton.doClick() }
                    return@addActionListener
                }

                // Build graph by scanning the project for RouteBuilder classes
                val graph = ReadAction.compute<CamelRouteGraph, RuntimeException> { graphService.buildGraph() }
                
                // If no routes found, show a message
                if (graph.nodes.isEmpty()) {
                    val emptyHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <title>Camel Route Graph</title>
                            <style>
                                body { 
                                    font-family: sans-serif; 
                                    margin: 0; 
                                    padding: 50px; 
                                    background-color: #2b2b2b; 
                                    color: #a9b7c6; 
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    height: 100vh;
                                }
                                .message {
                                    text-align: center;
                                    font-size: 18px;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="message">
                                <h2>No Camel Routes Found</h2>
                                <p>No Camel Java DSL routes were detected.</p>
                                <p>This plugin currently looks for <code>configure()</code> methods in classes that extend <code>RouteBuilder</code> (including anonymous <code>new RouteBuilder(){...}</code> beans).</p>
                                <p>Entry points detected: <code>from</code>, <code>fromD</code>, <code>fromF</code>, <code>rest</code>.</p>
                                <p>If your routes are defined using XML/YAML, Spring DSL, or other builders, they won't be detected yet.</p>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    browser.loadHTML(emptyHtml)
                } else {
                    val html = GraphHtmlGenerator.generateHtml(graph)
                    browser.loadHTML(html)
                }
            } catch (e: IndexNotReadyException) {
                // The project can enter dumb mode while we're building the graph (race).
                // Show indexing message and retry automatically when indices are ready.
                val indexingHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Camel Route Graph</title>
                        <style>
                            body { 
                                font-family: sans-serif; 
                                margin: 0; 
                                padding: 50px; 
                                background-color: #2b2b2b; 
                                color: #a9b7c6; 
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                            }
                            .message { text-align: center; font-size: 18px; }
                        </style>
                    </head>
                    <body>
                        <div class="message">
                            <h2>Indexing Project...</h2>
                            <p>IntelliJ indices are not ready yet. The graph will refresh automatically when indexing finishes.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                browser.loadHTML(indexingHtml)
                DumbService.getInstance(project).runWhenSmart { refreshButton.doClick() }
            } catch (e: ProcessCanceledException) {
                // Often thrown during indexing/cancellations. Treat as "wait for smart mode" instead of error.
                val indexingHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Camel Route Graph</title>
                        <style>
                            body { 
                                font-family: sans-serif; 
                                margin: 0; 
                                padding: 50px; 
                                background-color: #2b2b2b; 
                                color: #a9b7c6; 
                                display: flex;
                                justify-content: center;
                                align-items: center;
                                height: 100vh;
                            }
                            .message { text-align: center; font-size: 18px; }
                        </style>
                    </head>
                    <body>
                        <div class="message">
                            <h2>Indexing Project...</h2>
                            <p>The operation was cancelled by IntelliJ (usually due to indexing). The graph will refresh automatically when ready.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                browser.loadHTML(indexingHtml)
                DumbService.getInstance(project).runWhenSmart { refreshButton.doClick() }
            } catch (e: Exception) {
                val errorHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <title>Error</title>
                        <style>
                            body { 
                                font-family: sans-serif; 
                                margin: 0; 
                                padding: 50px; 
                                background-color: #2b2b2b; 
                                color: #ff6b6b; 
                            }
                        </style>
                    </head>
                    <body>
                        <h2>Error building graph</h2>
                        <pre>${e.message?.let { SecurityUtils.escapeHtml(it) } ?: "Unknown error occurred"}</pre>
                    </body>
                    </html>
                """.trimIndent()
                    browser.loadHTML(errorHtml)
            } finally {
                // Re-enable button after processing completes (success or error)
                refreshButton.isEnabled = true
                refreshButton.text = "Refresh Graph"
            }
        }
        
        panel.add(browser.component, BorderLayout.CENTER)
        panel.add(refreshButton, BorderLayout.NORTH)
        
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // Initial load
        refreshButton.doClick()
    }
}
