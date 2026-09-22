package com.github.mydachi.frictionless.narration

/**
 * Deliverable U7: make an identifier speakable.
 *
 * `applyFxMargin` read literally is mush. Split camelCase, expand the initialisms we actually use,
 * and drop the package noise, so the tour sounds deliberate rather than broken.
 */
object Identifiers {

    private val INITIALISMS = mapOf(
        "fx" to "F X", "api" to "A P I", "id" to "I D", "dto" to "D T O",
        "url" to "U R L", "http" to "H T T P", "sql" to "S Q L", "io" to "I O",
        "ui" to "U I", "iban" to "I BAN", "kyc" to "K Y C", "pdf" to "P D F",
    )

    /** `TransferService.applyFxMargin()` -> `apply F X margin`. */
    fun humanise(displayName: String): String {
        val member = displayName.substringAfterLast('.').removeSuffix("()")
        return splitCamelCase(member)
            .map { word -> INITIALISMS[word.lowercase()] ?: word.lowercase() }
            .joinToString(" ")
            .trim()
    }

    internal fun splitCamelCase(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val words = mutableListOf<String>()
        val current = StringBuilder()
        text.forEach { ch ->
            when {
                ch == '_' || ch == '-' || ch == ' ' -> {
                    if (current.isNotEmpty()) { words += current.toString(); current.clear() }
                }
                ch.isUpperCase() && current.isNotEmpty() && !current.last().isUpperCase() -> {
                    words += current.toString(); current.clear(); current.append(ch)
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words.filter { it.isNotBlank() }
    }
}
