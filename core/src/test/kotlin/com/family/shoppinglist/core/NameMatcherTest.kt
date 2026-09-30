package com.family.shoppinglist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {
    @Test
    fun `matches an item inside a longer product name`() {
        assertTrue(NameMatcher.matches("milk", "Semi Skimmed Milk 2 Pint"))
        assertTrue(NameMatcher.matches("Toilet paper", "Andrex Classic Clean Toilet Paper 9 Roll"))
    }

    @Test
    fun `ignores plurals and case`() {
        assertTrue(NameMatcher.matches("Bananas", "Loose banana"))
        assertTrue(NameMatcher.matches("tomato", "Cherry Tomatoes 250g"))
        assertTrue(NameMatcher.matches("berries", "Mixed berry pack"))
    }

    @Test
    fun `every word of the item must be present`() {
        assertFalse(NameMatcher.matches("toilet paper", "Kitchen paper towels"))
        assertFalse(NameMatcher.matches("bread", "Milk"))
    }

    @Test
    fun `blank items never match`() {
        assertFalse(NameMatcher.matches("  ", "Milk"))
        assertFalse(NameMatcher.matches("!!!", "Milk"))
    }

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
    fun `words ending in ss or us are not stemmed`() {
        assertEquals(setOf("glass"), NameMatcher.tokens("Glass"))
        assertEquals(setOf("couscous"), NameMatcher.tokens("Couscous"))
    }
}
