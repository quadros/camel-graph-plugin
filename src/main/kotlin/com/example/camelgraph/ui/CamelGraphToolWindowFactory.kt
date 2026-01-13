package com.example.camelgraph.ui

import com.example.camelgraph.model.CamelNode
import com.example.camelgraph.model.CamelRouteGraph
import com.example.camelgraph.model.NodeType
import com.example.camelgraph.service.CamelRouteService
import com.example.camelgraph.util.SecurityUtils
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
            try {
                // Build graph by scanning the project for RouteBuilder classes
                val graph = graphService.buildGraph()
                
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
                                <p>No classes extending RouteBuilder were found in this project.</p>
                                <p>Make sure your project contains Apache Camel routes.</p>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    browser.loadHTML(emptyHtml)
                } else {
                    val html = GraphHtmlGenerator.generateHtml(graph)
                    browser.loadHTML(html)
                }
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
