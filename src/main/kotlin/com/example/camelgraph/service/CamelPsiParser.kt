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
    
    private val fromUriToContainerId = mutableMapOf<String, String>()
    
    /**
     * Clear the parser state before starting a new parsing session
     * This ensures that multiple calls to buildGraph() don't interfere with each other
     */
    fun clearState() {
        fromUriToContainerId.clear()
    }

    data class ParseStats(
        val configureMethods: Int,
        val entrypoints: Int
    )

    fun parseProject(graph: CamelRouteGraph) {
        // In a real plugin, we would use a more efficient index search.
        // For simplicity/prototype, we'll scan all Java files in the content scope.
        // Ideally: FilenameIndex.getAllFiles(project, ...)
        
        // This part usually requires the plugin to run inside IDEA to have access to indices.
        // For the parser logic itself, we can expose a method to parse a single file.
    }

    fun parseFile(psiFile: PsiJavaFile, graph: CamelRouteGraph): ParseStats {
        // Important: in Spring projects it's common to declare routes using anonymous classes:
        //   @Bean RoutesBuilder routes() { return new RouteBuilder() { public void configure() { ... } }; }
        // `psiFile.classes` only includes top-level classes, so we must traverse inner + anonymous classes too.
        val classes = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)

        var configureMethodsCount = 0
        var entrypointsCount = 0

        for (clazz in classes) {
            // Look for configure() method (RouteBuilder / EndpointRouteBuilder Java DSL)
            val methods = clazz.findMethodsByName("configure", false)
            configureMethodsCount += methods.size
            for (method in methods) {
                entrypointsCount += parseConfigureMethod(method, graph, psiFile.virtualFile.path, clazz)
            }
        }

        return ParseStats(configureMethods = configureMethodsCount, entrypoints = entrypointsCount)
    }
    
    // NOTE: Cross-file route linking is now achieved by using deterministic node IDs for URIs.
    // When `.to("direct:X")` and `from("direct:X")` share the same node id, flows connect naturally.

    private fun parseConfigureMethod(method: PsiMethod, graph: CamelRouteGraph, filePath: String, clazz: PsiClass): Int {
        // We are looking for route "entrypoints" in Camel DSL.
        // Common ones:
        // - from(...)
        // - fromD(...) (dynamic from)
        // - fromF(...) (formatted from)
        // - rest(...) (REST DSL entry)
        val body = method.body ?: return 0

        var entrypoints = 0
        
        body.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val methodName = expression.methodExpression.referenceName
                val isFromEntrypoint = methodName == "from" || methodName == "fromD" || methodName == "fromF"
                val isRestEntrypoint = methodName == "rest"
                if (isFromEntrypoint || isRestEntrypoint) {
                    // Start of a route (Java DSL) or REST DSL chain
                    val (uri, _) = extractUriArgument(expression)
                    if (uri.isNotEmpty()) {
                        entrypoints += 1
                        val sanitizedUri = SecurityUtils.sanitizeUri(uri)
                        val labelPrefix = if (isRestEntrypoint) "rest:" else ""
                        val sanitizedLabel = SecurityUtils.sanitizeLabel(labelPrefix + sanitizedUri)

                        // Use baseDir.path for compatibility with IntelliJ 2023.2+
                        // basePath is available in 2024.1+, but baseDir.path works in all versions
                        val projectBasePath = project.baseDir?.path
                        val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                        val containerId = containerNodeId(sanitizedFilePath, clazz.name ?: "Routes")
                        graph.addNode(
                            CamelNode(
                                id = containerId,
                                label = SecurityUtils.sanitizeLabel(clazz.name ?: (sanitizedFilePath ?: "Routes")),
                                type = NodeType.CONTAINER,
                                parentId = null,
                                filePath = sanitizedFilePath,
                                lineNumber = -1
                            )
                        )

                        val nodeId = uriNodeId(sanitizedUri)
                        val startNode = CamelNode(
                            id = nodeId,
                            label = sanitizedLabel,
                            type = NodeType.ROUTE_START,
                            parentId = containerId,
                            filePath = sanitizedFilePath,
                            lineNumber = getLineNumber(expression)
                        )
                        graph.addNode(startNode)

                        // Only store "from*" URIs for later matching with to() endpoints.
                        // REST DSL is not a direct endpoint URI in the same sense as from("direct:...").
                        if (isFromEntrypoint) {
                            fromUriToContainerId[sanitizedUri] = containerId
                        }
                        
                        // Parse the chain
                        parseRouteChain(expression, startNode, graph, filePath, containerId)
                    }
                }
            }
        })

        return entrypoints
    }

    private fun parseRouteChain(
        startExpression: PsiMethodCallExpression,
        startNode: CamelNode,
        graph: CamelRouteGraph,
        filePath: String,
        currentContainerId: String
    ) {
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
        var insideParallel = false
        var parallelNodeId: String? = null
        
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

                     // Exit parallelProcessing block at end()
                     if (methodName == "end" && insideParallel) {
                         insideParallel = false
                         parallelNodeId = null
                         currentExpr = grandParent
                         continue
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
                     
                     var nodeType = NodeType.UNKNOWN
                     var nodeLabel = label
                     var nodeId: String? = null
                     var parentId: String? = currentContainerId
                     
                     if (methodName == "to" || methodName == "toD") {
                         nodeType = NodeType.ENDPOINT
                         if (sanitizedArg.isNotEmpty()) {
                             nodeId = uriNodeId(sanitizedArg)
                             // If this endpoint is a reference to a route defined elsewhere, pin it to that container
                             parentId = fromUriToContainerId[sanitizedArg] ?: currentContainerId
                         }
                     } else if (methodName == "bean") {
                         nodeType = NodeType.BEAN
                         // Extract method name from bean() call
                         nodeLabel = extractBeanMethodName(grandParent)
                         nodeId = UUID.randomUUID().toString()
                     } else if (methodName == "process") {
                         nodeType = NodeType.PROCESSOR
                         nodeId = UUID.randomUUID().toString()
                     } else if (methodName == "choice") {
                         nodeType = NodeType.CHOICE
                         nodeLabel = "choice"
                         nodeId = UUID.randomUUID().toString()
                     } else if (methodName == "parallelProcessing") {
                         nodeType = NodeType.PARALLEL_PROCESSING
                         nodeLabel = "parallel"
                         nodeId = UUID.randomUUID().toString()
                     } else if (methodName == "doCatch") {
                         nodeType = NodeType.DO_CATCH
                         nodeLabel = extractDoCatchLabel(grandParent)
                         nodeId = UUID.randomUUID().toString()
                     }
                     // Ignore others for now or map to generic
                     
                     if (nodeType != NodeType.UNKNOWN) {
                         // Use baseDir.path for compatibility with IntelliJ 2023.2+
                        // basePath is available in 2024.1+, but baseDir.path works in all versions
                        val projectBasePath = project.baseDir?.path
                        val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                         val finalNodeId = nodeId ?: UUID.randomUUID().toString()
                         val newNode = CamelNode(
                             id = finalNodeId,
                             label = SecurityUtils.sanitizeLabel(nodeLabel),
                             type = nodeType,
                             parentId = parentId,
                             filePath = sanitizedFilePath,
                             lineNumber = getLineNumber(grandParent)
                         )
                         graph.addNode(newNode)

                         // For parallelProcessing block, endpoints should branch from the parallel node
                         val parallelSourceId = if (insideParallel) parallelNodeId else null
                         val sourceId = if (nodeType == NodeType.ENDPOINT && parallelSourceId != null) {
                             parallelSourceId
                         } else {
                             currentPreviousNode.id
                         }
                         graph.addEdge(sourceId, newNode.id)

                         // Do NOT advance the sequential chain when adding endpoints inside parallelProcessing,
                         // otherwise you'd get to(A) -> to(B) instead of parallel -> A/B/...
                         val advancesChain = !(nodeType == NodeType.ENDPOINT && insideParallel)
                         if (advancesChain) {
                             currentPreviousNode = newNode
                         }

                         if (nodeType == NodeType.PARALLEL_PROCESSING) {
                             insideParallel = true
                             parallelNodeId = newNode.id
                         }
                         
                         // If this is a choice, parse its structure AFTER adding the node
                         if (nodeType == NodeType.CHOICE) {
                             parseChoiceStructure(grandParent, newNode.id, graph, filePath)
                             // After processing choice, skip everything until .end()
                             val endExpression = findMatchingEnd(grandParent)
                             if (endExpression != null) {
                                 currentExpr = endExpression
                                 continue
                             }
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
        
        val choiceContainerId = graph.nodes[choiceNodeId]?.parentId

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
                        findDestinationInBranch(grandParent, choiceNodeId, condition, graph, filePath, choiceContainerId)
                    } else if (methodName == "otherwise") {
                        // Find destination within otherwise branch
                        findDestinationInBranch(grandParent, choiceNodeId, "otherwise", graph, filePath, choiceContainerId)
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
    private fun findDestinationInBranch(
        branchExpression: PsiMethodCallExpression,
        choiceNodeId: String,
        edgeLabel: String,
        graph: CamelRouteGraph,
        filePath: String,
        defaultContainerId: String?
    ) {
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
                            
                            val nodeId = uriNodeId(sanitizedUri)
                            val projectBasePath = project.baseDir?.path
                            val sanitizedFilePath = SecurityUtils.sanitizeFilePath(filePath, projectBasePath)
                            val parentId = fromUriToContainerId[sanitizedUri] ?: defaultContainerId
                            graph.addNode(
                                CamelNode(
                                    id = nodeId,
                                    label = label,
                                    type = NodeType.ENDPOINT,
                                    parentId = parentId,
                                    filePath = sanitizedFilePath,
                                    lineNumber = getLineNumber(grandParent)
                                )
                            )
                            
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
                            defaultContainerId,
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
                            defaultContainerId,
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
     * bean(beanInstance) -> beanInstance (nome da variável / expressão) ou nome da classe quando possível
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

        // No explicit method -> use the bean reference/class name (first argument), when available.
        if (args.isNotEmpty()) {
            val firstArg = args[0]

            // Common patterns:
            // - bean(processor) -> "processor"
            // - bean(MyProcessor.class) -> "MyProcessor"
            // - bean(someFactory.getBean()) -> "someFactory.getBean()"
            val text = firstArg.text
                .replace(".class", "")
                .trim()

            if (text.isNotEmpty()) {
                // Try to prefer simple class name when the PSI type is resolvable.
                // NOTE: `resolve()` exists on PsiClassType, not on PsiType.
                val resolvedClassName = try {
                    when (firstArg) {
                        is PsiClassObjectAccessExpression ->
                            (firstArg.operand.type as? PsiClassType)?.resolve()?.name
                        else ->
                            (firstArg.type as? PsiClassType)?.resolve()?.name
                    }
                } catch (_: Exception) {
                    null
                }

                return resolvedClassName ?: text
            }
        }

        // Last resort
        return "bean"
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

    private fun uriNodeId(uri: String): String {
        val sanitized = SecurityUtils.sanitizeUri(uri)
        // Keep IDs stable and safe for Cytoscape: avoid whitespace/control chars
        val safe = sanitized.replace(Regex("\\s+"), "_")
        return "uri:$safe"
    }

    private fun containerNodeId(sanitizedFilePath: String?, className: String): String {
        val raw = (sanitizedFilePath ?: "unknown") + "::" + className
        val safe = raw.replace(Regex("\\s+"), "_")
        return "container:$safe"
    }

    private fun extractDoCatchLabel(doCatchExpression: PsiMethodCallExpression): String {
        val args = doCatchExpression.argumentList.expressions
        if (args.isEmpty()) return "doCatch"
        val first = args.first().text
            .replace("class", "")
            .replace(".class", "")
            .trim()
        return if (first.isNotEmpty()) "doCatch($first)" else "doCatch"
    }
}
