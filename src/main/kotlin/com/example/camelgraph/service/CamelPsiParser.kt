package com.example.camelgraph.service

import com.example.camelgraph.model.CamelNode
import com.example.camelgraph.model.CamelRouteGraph
import com.example.camelgraph.model.NodeType
import com.example.camelgraph.util.SecurityUtils
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import java.util.UUID

class CamelPsiParser(private val project: Project) {
    
    // Map to store from() URIs for matching with to() endpoints
    private val fromUriToNodeId = mutableMapOf<String, String>()

    fun parseProject(graph: CamelRouteGraph) {
        // In a real plugin, we would use a more efficient index search.
        // For simplicity/prototype, we'll scan all Java files in the content scope.
        // Ideally: FilenameIndex.getAllFiles(project, ...)
        
        // This part usually requires the plugin to run inside IDEA to have access to indices.
        // For the parser logic itself, we can expose a method to parse a single file.
    }

    fun parseFile(psiFile: PsiJavaFile, graph: CamelRouteGraph) {
        val classes = psiFile.classes
        for (clazz in classes) {
            // Check if extends RouteBuilder (simple check by name or inheritance if resolved)
            // Simpler check: look for configure method
            val methods = clazz.findMethodsByName("configure", false)
            for (method in methods) {
                parseConfigureMethod(method, graph, psiFile.virtualFile.path)
            }
        }
    }
    
    /**
     * Create edges for to() -> from() connections after all files are parsed
     * This should be called after parsing all files
     */
    fun createToFromConnections(graph: CamelRouteGraph) {
        // Find all endpoint nodes (to() calls) and check if they match any from() URIs
        val endpointNodes = graph.nodes.values.filter { it.type == NodeType.ENDPOINT }
        
        for (endpointNode in endpointNodes) {
            // Extract URI from label (assuming label contains the URI)
            // Need to decode HTML entities
            val uri = endpointNode.label
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
            
            val targetFromNodeId = fromUriToNodeId[uri]
            
            if (targetFromNodeId != null) {
                // Find the source node (the one that has an edge to this endpoint)
                val sourceEdge = graph.edges.find { it.targetId == endpointNode.id }
                if (sourceEdge != null) {
                    // Create edge from source to the target from() node
                    // Remove the intermediate endpoint node edge and create direct connection
                    graph.edges.removeIf { it.sourceId == sourceEdge.sourceId && it.targetId == endpointNode.id }
                    graph.addEdge(sourceEdge.sourceId, targetFromNodeId, "→ ${endpointNode.label}")
                }
            }
        }
    }

    private fun parseConfigureMethod(method: PsiMethod, graph: CamelRouteGraph, filePath: String) {
        // We are looking for method calls starting with "from"
        val body = method.body ?: return
        
        body.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val methodName = expression.methodExpression.referenceName
                if (methodName == "from") {
                    // Start of a route
                    val (uri, _) = extractUriArgument(expression)
                    if (uri.isNotEmpty()) {
                        val sanitizedUri = SecurityUtils.sanitizeUri(uri)
                        val sanitizedLabel = SecurityUtils.sanitizeLabel(sanitizedUri)
                        val nodeId = UUID.randomUUID().toString()
                        // Use baseDir.path for compatibility with IntelliJ 2023.2+
                        // basePath is available in 2024.1+, but baseDir.path works in all versions
                        val projectBasePath = project.baseDir?.path
                        val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                        val startNode = CamelNode(nodeId, sanitizedLabel, NodeType.ROUTE_START, sanitizedFilePath, getLineNumber(expression))
                        graph.addNode(startNode)
                        
                        // Store the from URI for later matching with to() endpoints
                        fromUriToNodeId[sanitizedUri] = nodeId
                        
                        // Parse the chain
                        parseRouteChain(expression, startNode, graph, filePath)
                    }
                }
            }
        })
    }

    private fun parseRouteChain(startExpression: PsiMethodCallExpression, startNode: CamelNode, graph: CamelRouteGraph, filePath: String) {
        var currentPreviousNode = startNode
        
        // The chain in Java PSI is nested inverse.
        // from("a").to("b").to("c")
        // top expression is .to("c")
        // its qualifier is .to("b")
        // its qualifier is .from("a")
        
        // Wait, the visitor visits top-down or bottom-up?
        // Recursive visitor visits children.
        // If we are at "from", we are at the *root* of the call chain if we look at the text, 
        // BUT in PSI structure:
        // MethodCall: to("c") -> child: Reference: to -> child: MethodCall: to("b") -> child: Reference: to -> child: MethodCall: from("a")
        
        // So if we found "from" inside visitMethodCallExpression, we are at the deepest part of the chain (the start).
        // We need to look up to the *parents* to find the subsequent calls (.to, .bean).
        
        var currentExpr: PsiElement = startExpression
        var insideChoice = false
        var choiceDepth = 0
        
        while (true) {
            // Look for the parent method call
            // The parent of a MethodCallExpression is usually:
            // - ExpressionStatement (end of chain)
            // - ReferenceExpression (part of next call) -> MethodCallExpression
            
            val parent = currentExpr.parent
            
            if (parent is PsiReferenceExpression) {
                val grandParent = parent.parent
                if (grandParent is PsiMethodCallExpression) {
                    // This is the next call in the chain
                     val methodName = grandParent.methodExpression.referenceName ?: ""
                     
                     // Track choice depth to know when we exit a choice
                     if (methodName == "choice") {
                         insideChoice = true
                         choiceDepth = 1
                     } else if (methodName == "when" || methodName == "otherwise") {
                         if (insideChoice) {
                             choiceDepth++
                         }
                     } else if (methodName == "end") {
                         if (insideChoice) {
                             choiceDepth--
                             if (choiceDepth == 0) {
                                 insideChoice = false
                                 // Skip the end() call itself
                                 currentExpr = grandParent
                                 continue
                             }
                         }
                     }
                     
                     // Skip methods that are part of choice structure (they are handled separately)
                     // Also skip methods that don't create nodes
                     if (methodName in listOf("when", "otherwise", "end", "log", "id", "setBody")) {
                         // These are handled inside parseChoiceStructure or ignored
                         // Continue to next in chain
                         currentExpr = grandParent
                         continue
                     }
                     
                     // If we're inside a choice, skip processing (destinations are handled by parseChoiceStructure)
                     if (insideChoice && methodName in listOf("to", "toD", "bean", "process")) {
                         // Skip - these will be processed by parseChoiceStructure
                         currentExpr = grandParent
                         continue
                     }
                     
                     val (argValue, _) = extractUriArgument(grandParent)
                     val sanitizedArg = SecurityUtils.sanitizeUri(argValue)
                     val label = if (sanitizedArg.isNotEmpty()) SecurityUtils.sanitizeLabel(sanitizedArg) else methodName
                     val nodeId = UUID.randomUUID().toString()
                     
                     var nodeType = NodeType.UNKNOWN
                     var nodeLabel = label
                     
                     if (methodName == "to" || methodName == "toD") {
                         nodeType = NodeType.ENDPOINT
                     } else if (methodName == "bean") {
                         nodeType = NodeType.BEAN
                         // Extract method name from bean() call
                         nodeLabel = extractBeanMethodName(grandParent)
                     } else if (methodName == "process") {
                         nodeType = NodeType.PROCESSOR
                     } else if (methodName == "choice") {
                         nodeType = NodeType.CHOICE
                         nodeLabel = "choice"
                     }
                     // Ignore others for now or map to generic
                     
                     if (nodeType != NodeType.UNKNOWN) {
                         // Use baseDir.path for compatibility with IntelliJ 2023.2+
                        // basePath is available in 2024.1+, but baseDir.path works in all versions
                        val projectBasePath = project.baseDir?.path
                        val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                         val newNode = CamelNode(nodeId, SecurityUtils.sanitizeLabel(nodeLabel), nodeType, sanitizedFilePath, getLineNumber(grandParent))
                         graph.addNode(newNode)
                         graph.addEdge(currentPreviousNode.id, newNode.id)
                         currentPreviousNode = newNode
                         
                         // If this is a choice, parse its structure AFTER adding the node
                         if (nodeType == NodeType.CHOICE) {
                             parseChoiceStructure(grandParent, nodeId, graph, filePath)
                             // After processing choice, skip everything until .end()
                             val endExpression = findMatchingEnd(grandParent)
                             if (endExpression != null) {
                                 // Skip to after the end()
                                 currentExpr = endExpression
                                 continue
                             }
                         }
                         
                         // Store endpoint URI for to() -> from() matching
                         if (nodeType == NodeType.ENDPOINT && sanitizedArg.isNotEmpty()) {
                             // Store with a prefix to identify it as a to() endpoint
                             // We'll match this later with from() URIs
                         }
                     }
                     
                     currentExpr = grandParent
                     continue
                }
            }
            // If we reached here, chain ended or structure is different
            break
        }
    }
    
    /**
     * Parse choice structure to find when() and otherwise() branches
     * and create edges from choice node to the destinations in those branches
     */
    private fun parseChoiceStructure(choiceExpression: PsiMethodCallExpression, choiceNodeId: String, graph: CamelRouteGraph, filePath: String) {
        // Find all when() and otherwise() calls within the choice
        // The structure is: choice().when(...).to(...).when(...).to(...).otherwise(...).bean(...).end()
        // We need to navigate up the PSI tree to find when() and otherwise() that are part of this choice chain
        
        // Navigate up from choice() to find when() and otherwise() calls
        var current: PsiElement? = choiceExpression
        var foundEnd = false
        
        while (current != null && !foundEnd) {
            val parent = current.parent
            if (parent is PsiReferenceExpression) {
                val grandParent = parent.parent
                if (grandParent is PsiMethodCallExpression) {
                    val methodName = grandParent.methodExpression.referenceName
                    
                    if (methodName == "when") {
                        // Extract condition from when()
                        val condition = extractWhenCondition(grandParent)
                        // Find destination (to, bean, etc.) within this when branch
                        findDestinationInBranch(grandParent, choiceNodeId, condition, graph, filePath)
                    } else if (methodName == "otherwise") {
                        // Find destination within otherwise branch
                        findDestinationInBranch(grandParent, choiceNodeId, "otherwise", graph, filePath)
                    } else if (methodName == "end") {
                        // Reached the end of choice, stop
                        foundEnd = true
                        break
                    }
                    
                    // Continue navigating up
                    current = grandParent
                    continue
                }
            }
            break
        }
    }
    
    /**
     * Extract condition from when() call
     */
    private fun extractWhenCondition(whenExpression: PsiMethodCallExpression): String {
        val args = whenExpression.argumentList.expressions
        if (args.isNotEmpty()) {
            val firstArg = args[0]
            // Try to get the text representation of the condition
            val conditionText = firstArg.text
            // Remove quotes and simplify
            return conditionText.replace("\"", "").replace("'", "").trim()
        }
        return "when"
    }
    
    /**
     * Find destination (to, bean, etc.) within a when() or otherwise() branch
     * and create edge from choice to that destination with the condition as label
     * Only the LAST meaningful destination in the branch should be used
     * 
     * In PSI structure: when() -> log() -> to() or when() -> to()
     * We need to navigate FORWARD in the chain (following qualifiers) to find to/bean/process
     * We should NOT use a recursive visitor as it will visit elements outside the branch
     */
    private fun findDestinationInBranch(branchExpression: PsiMethodCallExpression, choiceNodeId: String, edgeLabel: String, graph: CamelRouteGraph, filePath: String) {
        // Navigate forward in the chain from when()/otherwise() to find destinations
        // Structure: when() -> log() -> to() or when() -> to()
        // We traverse by following the qualifier chain (parent -> reference -> method call)
        var destinationInfo: DestinationInfo? = null
        var current: PsiElement? = branchExpression
        
        // Traverse forward in the chain until we hit another when/otherwise/end or find a destination
        while (current != null) {
            val parent = current.parent
            if (parent is PsiReferenceExpression) {
                val grandParent = parent.parent
                if (grandParent is PsiMethodCallExpression) {
                    val methodName = grandParent.methodExpression.referenceName
                    
                    // Stop if we hit another branch or end of choice
                    if (methodName in listOf("when", "otherwise", "end")) {
                        break
                    }
                    
                    // Skip non-destination methods
                    if (methodName in listOf("log", "id", "setBody")) {
                        current = grandParent
                        continue
                    }
                    
                    // Check if this is a destination
                    if (methodName == "to" || methodName == "toD") {
                        val (uri, _) = extractUriArgument(grandParent)
                        if (uri.isNotEmpty()) {
                            val sanitizedUri = SecurityUtils.sanitizeUri(uri)
                            val label = SecurityUtils.sanitizeLabel(sanitizedUri)
                            
                            // Check if this to() points to a from() route
                            var nodeId = fromUriToNodeId[sanitizedUri]
                            
                            if (nodeId == null) {
                                // Create endpoint node
                                nodeId = UUID.randomUUID().toString()
                                val projectBasePath = project.baseDir?.path
                                val sanitizedFilePath = SecurityUtils.sanitizeFilePath(null, projectBasePath)
                                val endpointNode = CamelNode(
                                    nodeId,
                                    label,
                                    NodeType.ENDPOINT,
                                    sanitizedFilePath,
                                    getLineNumber(grandParent)
                                )
                                graph.addNode(endpointNode)
                            }
                            
                            // Store the last destination found
                            destinationInfo = DestinationInfo(nodeId, label, NodeType.ENDPOINT)
                        }
                    } else if (methodName == "bean") {
                        val beanMethodName = extractBeanMethodName(grandParent)
                        val nodeId = UUID.randomUUID().toString()
                        val projectBasePath = project.baseDir?.path
                        val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                        val beanNode = CamelNode(
                            nodeId,
                            SecurityUtils.sanitizeLabel(beanMethodName),
                            NodeType.BEAN,
                            sanitizedFilePath,
                            getLineNumber(grandParent)
                        )
                        graph.addNode(beanNode)
                        
                        // Store the last destination found
                        destinationInfo = DestinationInfo(nodeId, beanMethodName, NodeType.BEAN)
                    } else if (methodName == "process") {
                        val nodeId = UUID.randomUUID().toString()
                        val projectBasePath = project.baseDir?.path
                        val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                        val processorNode = CamelNode(
                            nodeId,
                            SecurityUtils.sanitizeLabel("processor"),
                            NodeType.PROCESSOR,
                            sanitizedFilePath,
                            getLineNumber(grandParent)
                        )
                        graph.addNode(processorNode)
                        
                        // Store the last destination found
                        destinationInfo = DestinationInfo(nodeId, "processor", NodeType.PROCESSOR)
                    }
                    
                    // Continue forward in chain
                    current = grandParent
                    continue
                }
            }
            break
        }
        
        // Create edge from choice to destination with condition as label
        if (destinationInfo != null && destinationInfo.nodeId != null && destinationInfo.label != null) {
            graph.addEdge(choiceNodeId, destinationInfo.nodeId!!, SecurityUtils.sanitizeLabel(edgeLabel))
        }
    }
    
    /**
     * Helper class to store destination information
     */
    private data class DestinationInfo(val nodeId: String?, val label: String?, val type: NodeType?)
    
    /**
     * Find the matching end() for a choice() expression
     * Navigates up the PSI tree to find the end() that closes this choice
     */
    private fun findMatchingEnd(choiceExpression: PsiMethodCallExpression): PsiElement? {
        var current: PsiElement = choiceExpression
        var depth = 1
        
        // Navigate up the tree looking for end()
        while (current.parent != null) {
            current = current.parent
            
            if (current is PsiMethodCallExpression) {
                val methodName = current.methodExpression.referenceName
                if (methodName == "end") {
                    depth--
                    if (depth == 0) {
                        return current
                    }
                } else if (methodName == "choice") {
                    depth++
                }
            }
            
            // Stop if we reach the statement level
            if (current is PsiStatement) {
                break
            }
        }
        
        return null
    }
    
    /**
     * Extract method name from bean() call
     * bean(beanInstance, "methodName") -> "methodName"
     * bean(beanInstance) -> "processor"
     */
    private fun extractBeanMethodName(beanExpression: PsiMethodCallExpression): String {
        val args = beanExpression.argumentList.expressions
        if (args.size >= 2) {
            // Second argument should be the method name
            val secondArg = args[1]
            if (secondArg is PsiLiteralExpression) {
                val value = secondArg.value
                if (value is String) {
                    return value
                }
            }
            // Try to extract from text
            val text = secondArg.text.replace("\"", "").replace("'", "").trim()
            if (text.isNotEmpty()) {
                return text
            }
        }
        // Default to "processor" if no method name specified
        return "processor"
    }
    

    private fun extractUriArgument(expression: PsiMethodCallExpression): Pair<String, String> {
        val args = expression.argumentList.expressions
        if (args.isNotEmpty()) {
            val firstArg = args[0]
            if (firstArg is PsiLiteralExpression) {
                val value = firstArg.value
                if (value is String) {
                    // Sanitize and validate URI
                    val sanitized = SecurityUtils.sanitizeUri(value)
                    return sanitized to "String"
                }
            }
            // Handle constants or other expressions if needed? 
            // For now return text representation (sanitized)
            val text = firstArg.text.replace("\"", "")
            val sanitized = SecurityUtils.sanitizeUri(text)
            return sanitized to "Expression"
        }
        return "" to ""
    }
    
    private fun getLineNumber(element: PsiElement): Int {
         val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
         return document?.getLineNumber(element.textOffset)?.plus(1) ?: -1
    }
}
