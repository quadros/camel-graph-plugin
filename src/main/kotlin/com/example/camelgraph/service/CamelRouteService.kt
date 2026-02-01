package com.example.camelgraph.service

import com.example.camelgraph.model.CamelRouteGraph
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil

@Service(Service.Level.PROJECT)
class CamelRouteService(private val project: Project) {

    private val parser = CamelPsiParser(project)

    fun buildGraph(): CamelRouteGraph {
        // Clear parser state before building a new graph
        // This prevents state from previous builds from interfering
        parser.clearState()
        
        val graph = CamelRouteGraph()
        val parsedFilePaths = mutableSetOf<String>()

        var totalConfigureMethods = 0
        var totalEntrypoints = 0

        fun parseOnce(file: PsiJavaFile) {
            val path = file.virtualFile.path
            if (parsedFilePaths.add(path)) {
                val beforeNodes = graph.nodes.size
                val stats = parser.parseFile(file, graph)
                val afterNodes = graph.nodes.size
                totalConfigureMethods += stats.configureMethods
                totalEntrypoints += stats.entrypoints
            }
        }
        
        // Use a scope that includes both project files and library dependencies
        // This is necessary because RouteBuilder is in a library dependency
        val projectScope = GlobalSearchScope.projectScope(project)
        // Use allScope to include libraries, or use projectScope with allScope for RouteBuilder lookup
        val allScope = GlobalSearchScope.allScope(project)
        
        // Try to find RouteBuilder class (it's in a library dependency)
        // Use allScope to find the class in libraries
        val routeBuilderClass = JavaPsiFacade.getInstance(project)
            .findClass("org.apache.camel.builder.RouteBuilder", allScope)
        
        if (routeBuilderClass != null) {
            // Find all classes that extend RouteBuilder
            // Use projectScope for inheritors (we only want classes in the project, not in libraries)
            val inheritors = ClassInheritorsSearch.search(routeBuilderClass, projectScope, true)
            
            inheritors.forEach { psiClass ->
                val containingFile = psiClass.containingFile
                if (containingFile is PsiJavaFile) {
                    parseOnce(containingFile)
                }
            }

            // Spring projects often declare routes as anonymous classes returned from @Bean methods:
            //   return new RouteBuilder() { public void configure() { from(...); } };
            // These may not appear in ClassInheritorsSearch results, so we scan for PsiAnonymousClass too.
            findAnonymousRouteBuilders(projectScope).forEach { parseOnce(it) }

            // If indices-based inheritor search fails (common during/after indexing issues),
            // fall back to manual file scanning so top-level `extends RouteBuilder` classes are still detected.
            if (graph.nodes.isEmpty()) {
                findRouteBuilderClassesManually(graph, projectScope, ::parseOnce)
            }
        } else {
            // Fallback: if RouteBuilder class is not found, try alternative search
            // This might happen if the project hasn't been fully indexed yet
            // or if Camel dependencies are not properly configured
            // If RouteBuilder isn't resolvable yet (indexing / classpath issues), fall back to heuristics.
            findRouteBuilderClassesManually(graph, projectScope, ::parseOnce)
        }
        
        return graph
    }
    
    /**
     * Fallback method to find RouteBuilder classes by searching for classes
     * that have "RouteBuilder" in their superclass name
     */
    private fun findRouteBuilderClassesManually(
        graph: CamelRouteGraph,
        scope: GlobalSearchScope,
        parseOnce: (PsiJavaFile) -> Unit
    ) {
        // Get all Java files in the project
        val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)
        val psiManager = PsiManager.getInstance(project)
        
        for (vf in javaFiles) {
            val psiFile = psiManager.findFile(vf) ?: continue
            val javaFile = psiFile as? PsiJavaFile ?: continue

            // Include inner + anonymous classes too (common in Spring @Bean route declarations)
            val classes = PsiTreeUtil.findChildrenOfType(javaFile, PsiClass::class.java)
            for (clazz in classes) {
                // Fast path: resolved superclass chain (works when Camel dependency is indexed)
                val superClass = clazz.superClass
                if (superClass != null && isRouteBuilder(superClass)) {
                    parseOnce(javaFile)
                    break
                }

                // Fallback: textual extends check (works even when dependencies are not resolved yet)
                val extendsList = clazz.extendsListTypes
                val extendsRouteBuilder = extendsList.any { type ->
                    val canonical = type.canonicalText
                    canonical == "org.apache.camel.builder.RouteBuilder" ||
                        canonical.endsWith(".RouteBuilder") ||
                        canonical == "RouteBuilder"
                }

                if (extendsRouteBuilder) {
                    parseOnce(javaFile)
                    break
                }
            }
        }
    }

    private fun findAnonymousRouteBuilders(scope: GlobalSearchScope): List<PsiJavaFile> {
        val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)
        val psiManager = PsiManager.getInstance(project)
        val result = mutableListOf<PsiJavaFile>()

        for (vf in javaFiles) {
            val psiFile = psiManager.findFile(vf) ?: continue
            val javaFile = psiFile as? PsiJavaFile ?: continue

            val anonymousClasses = PsiTreeUtil.findChildrenOfType(javaFile, PsiAnonymousClass::class.java)
            val hasRouteBuilderAnonymous = anonymousClasses.any { anon ->
                val base = anon.baseClassType.resolve()
                base != null && isRouteBuilder(base)
            }

            if (hasRouteBuilderAnonymous) {
                result.add(javaFile)
            }
        }

        return result
    }
    
    /**
     * Check if a class is RouteBuilder or extends RouteBuilder
     */
    private fun isRouteBuilder(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == "org.apache.camel.builder.RouteBuilder") {
            return true
        }
        
        // Check superclass recursively
        val superClass = psiClass.superClass
        return superClass != null && isRouteBuilder(superClass)
    }
}
