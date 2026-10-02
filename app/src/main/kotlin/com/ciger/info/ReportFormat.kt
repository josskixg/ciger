package com.ciger.info

/**
 * Renders `key : value` lines with every colon pushed to the same column.
 *
 * Nested rows (keys starting with two spaces) keep their indent *inside* the
 * padding, otherwise the indent shifts the colon right by two and the block
 * stops lining up.
 */
object ReportFormat {

    /** Full prefix actually printed, indent included. */
    fun prefix(key: String): String =
        (if (key.startsWith("  ")) "  " else "") + key.trim()

    fun aligned(rows: List<Pair<String, String>>, targetWidth: Int? = null): List<String> {
        if (rows.isEmpty()) return emptyList()
        val width = targetWidth ?: rows.maxOf { prefix(it.first).length }
        return rows.flatMap { (k, v) ->
            val p = prefix(k).padEnd(width)
            val lines = v.lines()
            if (lines.size <= 1) {
                listOf("$p : $v")
            } else {
                buildList {
                    add("$p : ${lines[0]}")
                    val indent = " ".repeat(width + 3)
                    lines.drop(1).forEach { add("$indent$it") }
                }
            }
        }
    }

    /** Formats a structured report with a single global column alignment across all sections. */
    fun formatReport(sections: List<Pair<String?, List<Pair<String, String>>>>): String {
        val allRows = sections.flatMap { it.second }
        if (allRows.isEmpty()) return ""
        val globalWidth = allRows.maxOf { prefix(it.first).length }

        val sb = StringBuilder()
        sections.forEachIndexed { index, (title, rows) ->
            if (rows.isNotEmpty()) {
                if (title != null) {
                    if (index > 0) sb.appendLine()
                    sb.appendLine(title)
                }
                aligned(rows, globalWidth).forEach { sb.appendLine(it) }
            }
        }
        return sb.toString()
    }
}
