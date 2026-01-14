package com.example.camelgraph.service

import com.example.camelgraph.model.CamelRouteGraph
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch

@Service(Service.Level.PROJECT)
class CamelRouteService(private val project: Project) {

    private val parser = CamelPsiParser(project)

    fun buildGraph(): CamelRouteGraph {
        // Clear parser state before building a new graph
        // This prevents state from previous builds from interfering
        parser.clearState()
        
        val graph = CamelRouteGraph()
        
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
                    parser.parseFile(containingFile, graph)
                }
            }
        } else {
            // Fallback: if RouteBuilder class is not found, try alternative search
            // This might happen if the project hasn't been fully indexed yet
            // or if Camel dependencies are not properly configured
            findRouteBuilderClassesManually(graph, projectScope)
        }
        
        // After parsing all files, create connections for to() -> from() relationships
        parser.createToFromConnections(graph)
        
        return graph
    }
    
    /**
     * Fallback method to find RouteBuilder classes by searching for classes
     * that have "RouteBuilder" in their superclass name
     */
    private fun findRouteBuilderClassesManually(graph: CamelRouteGraph, scope: GlobalSearchScope) {
        // Get all Java files in the project
        val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)
        
        for (file in javaFiles) {
            if (file is PsiJavaFile) {
                for (clazz in file.classes) {
                    // Check if this class extends RouteBuilder
                    val superClass = clazz.superClass
                    if (superClass != null && isRouteBuilder(superClass)) {
                        parser.parseFile(file, graph)
                        break // Found a RouteBuilder, parse the file and move to next
                    }
                }
            }
        }
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
