
package com.neonide.studio.app.lsp

import com.neonide.studio.app.lsp.server.LspServerIds
import java.io.File

object LspUtils {
    fun getServerId(file: File): String? = when (file.extension.lowercase()) {
        "java" -> LspServerIds.JAVA
        "kt", "kts" -> LspServerIds.KOTLIN
        "xml" -> LspServerIds.XML
        "yaml", "yml" -> LspServerIds.YAML
        "json" -> LspServerIds.JSON
        "js", "ts", "jsx", "tsx" -> LspServerIds.JAVASCRIPT
        "sh", "bash", "zsh" -> LspServerIds.BASH
        else -> null
    }
}
