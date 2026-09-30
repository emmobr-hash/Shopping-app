package com.family.shoppinglist.core

/**
 * Loose matching between what's on your list ("milk", "Bananas") and what a supermarket
 * calls its product ("Semi Skimmed Milk 2 Pint"). Every word of the item must appear in the
 * product name; plurals are ignored.
 */
object NameMatcher {
    /** Items bought this many times count as "frequently bought". */
    const val FREQUENT_THRESHOLD = 3

    private val splitter = Regex("[^\\p{L}\\p{N}]+")

    fun tokens(name: String): Set<String> =
        name.lowercase().split(splitter).filter { it.isNotBlank() }.map(::stem).toSet()

    /** A stable key so "Banana", "bananas" and "BANANAS " count as the same thing. */
    fun key(name: String): String =
        tokens(name).sorted().joinToString(" ").ifEmpty { name.trim().lowercase() }

    fun matches(itemName: String, productName: String): Boolean {
        val wanted = tokens(itemName)
        return wanted.isNotEmpty() && tokens(productName).containsAll(wanted)
    }

    private fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && word.endsWith("oes") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") -> word.dropLast(1)
        else -> word
    }
}
