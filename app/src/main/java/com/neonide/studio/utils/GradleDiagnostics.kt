package com.neonide.studio.utils

/**
 * Extremely lightweight diagnostics extractor.
 *
 * We cannot build a full Gradle/AGP model here, but we can still show helpful
 * actionable lines (similar to what users look for in Android Studio's Build panel).
 */
object GradleDiagnostics {

    private val interestingPrefixes = listOf(
        "* What went wrong:",
        "* Exception is:",
        "FAILURE:",
        "> ",
        "Caused by:",
        "Execution failed for task",
        "A problem occurred",
        "Could not resolve",
        "Could not find",
        "SDK location not found",
        "No matching variant",
        "Android SDK",
        "Build file",
    )

    private const val MAX_DIAGNOSTICS = 80
    private const val TAIL_LINES = 30
    private const val INITIAL_CAPACITY = 64

    /**
     * Extract a concise list of error-ish lines.
     * Returns an empty list if nothing obvious was found.
     */
    fun extract(fullOutput: String): List<String> {
        if (fullOutput.isBlank()) return emptyList()

        val lines = fullOutput
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')

        val out = lines.asSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .filter { trimmed ->
                interestingPrefixes.any { p -> trimmed.startsWith(p, ignoreCase = true) } ||
                    (trimmed.contains("error", ignoreCase = true) && !trimmed.contains("warning", ignoreCase = true))
            }
            .take(MAX_DIAGNOSTICS)
            .toMutableList()

        // If nothing found, show last few lines (often includes the failure summary)
        if (out.isEmpty()) {
            val tail = lines.takeLast(TAIL_LINES).map { it.trimEnd() }.filter { it.isNotBlank() }
            out.addAll(tail)
        }

        // Deduplicate consecutive duplicates
        return deduplicate(out)
    }

    private fun deduplicate(items: List<String>): List<String> {
        val dedup = ArrayList<String>(items.size)
        var prev: String? = null
        for (s in items) {
            if (s != prev) {
                dedup.add(s)
                prev = s
            }
        }
        return dedup
    }
}
