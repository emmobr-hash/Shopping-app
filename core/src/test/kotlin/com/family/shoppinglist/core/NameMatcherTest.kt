package com.family.shoppinglist.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NameMatcherTest {
    @Test
    fun `key is stable across spelling variations`() {
        assertEquals(NameMatcher.key("Banana"), NameMatcher.key(" BANANAS "))
        assertEquals(NameMatcher.key("paper toilet"), NameMatcher.key("Toilet Paper"))
    }

    @Test
    fun `key falls back to the raw text when nothing is tokenisable`() {
        assertEquals("!!!", NameMatcher.key(" !!! "))
    }

    @Test
    fun `plurals collapse to the same word`() {
        assertEquals(setOf("tomato"), NameMatcher.tokens("Tomatoes"))
        assertEquals(setOf("berry"), NameMatcher.tokens("berries"))
        assertEquals(setOf("banana"), NameMatcher.tokens("Bananas"))
    }

    @Test
    fun `words ending in ss or us are not stemmed`() {
        assertEquals(setOf("glass"), NameMatcher.tokens("Glass"))
        assertEquals(setOf("couscous"), NameMatcher.tokens("Couscous"))
    }
}
