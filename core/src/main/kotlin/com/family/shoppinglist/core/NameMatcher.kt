package com.family.shoppinglist.core

/** Normalises item names so "Banana", "bananas" and "BANANAS " count as the same thing. */
object NameMatcher {
    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    fun tokens(name: String): Set<String> =
        name.lowercase().split(splitter).filter { it.isNotBlank() }.map(::stem).toSet()

    /** A stable key so "Banana", "bananas" and "BANANAS " count as the same thing. */
    fun key(name: String): String =
        tokens(name).sorted().joinToString(" ").ifEmpty { name.trim().lowercase() }

    private fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && word.endsWith("oes") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") -> word.dropLast(1)
        else -> word
    }
}
