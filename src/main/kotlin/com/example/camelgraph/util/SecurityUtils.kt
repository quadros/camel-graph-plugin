package com.example.camelgraph.util

/**
 * Utility functions for security and sanitization
 */
object SecurityUtils {
    
    /**
     * Escapes HTML special characters to prevent XSS attacks
     */
    fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }
    
    /**
     * Sanitizes file paths by converting absolute paths to relative ones
     * and removing sensitive information like usernames
     */
    fun sanitizeFilePath(filePath: String?, projectBasePath: String? = null): String? {
        if (filePath == null) return null
        
        var sanitized = filePath
        
        // If project base path is provided, convert to relative
        if (projectBasePath != null && sanitized.startsWith(projectBasePath)) {
            sanitized = sanitized.removePrefix(projectBasePath).removePrefix("/")
        }
        
        // Remove common sensitive patterns (usernames in paths)
        // Pattern: /Users/username/ or C:\Users\username\
        sanitized = sanitized.replace(Regex("(?:^|[/\\\\])(Users|home|Documents)[/\\\\][^/\\\\]+[/\\\\]"), "/")
        
        // Limit path length to prevent DoS
        if (sanitized.length > 500) {
            sanitized = "..." + sanitized.takeLast(497)
        }
        
        return sanitized
    }

    /**
     * Debug-only helper. In production code, avoid writing logs to disk unless strictly necessary.
     * Kept intentionally minimal to reduce accidental sensitive data exposure.
     */
    // (no-op placeholder)
    
    /**
     * Validates and sanitizes URI strings
     */
    fun sanitizeUri(uri: String): String {
        // Limit length to prevent DoS
        val maxLength = 1000
        val sanitized = if (uri.length > maxLength) {
            uri.take(maxLength)
        } else {
            uri
        }
        
        // Remove potentially dangerous characters while preserving valid URI characters
        return sanitized
            .replace(Regex("[<>\"'`]"), "") // Remove dangerous characters
            .trim()
    }
    
    /**
     * Validates and sanitizes labels for display
     */
    fun sanitizeLabel(label: String): String {
        val maxLength = 200
        val sanitized = if (label.length > maxLength) {
            label.take(maxLength) + "..."
        } else {
            label
        }
        
        return escapeHtml(sanitized)
    }
}
