package com.thelightphone.lp3keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationTextTest {
    @Test
    fun theFirstLetterIsCapitalizedAtTheStartOfASentence() {
        assertEquals("Hello there", DictationText.format("hello there", startsSentence = true))
    }

    @Test
    fun theFirstLetterIsLeftAloneMidSentence() {
        assertEquals("hello there", DictationText.format("hello there", startsSentence = false))
    }

    @Test
    fun theWordIIsAlwaysCapitalized() {
        assertEquals("so I think I will", DictationText.format("so i think i will", startsSentence = false))
        assertEquals("I'm sure I'll go", DictationText.format("i'm sure i'll go", startsSentence = true))
    }

    @Test
    fun theLetterIInsideAWordIsNotTouched() {
        assertEquals("this is it", DictationText.format("this is it", startsSentence = false))
    }

    @Test
    fun noiseWordsAreDroppedAndSpacesTidied() {
        assertEquals("", DictationText.format("huh", startsSentence = true))
        assertEquals("see you", DictationText.format("huh  see   you huh", startsSentence = false))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", DictationText.format("   ", startsSentence = true))
        assertEquals("", DictationText.format("", startsSentence = false))
    }

    @Test
    fun aSpaceIsNeededOnlyAfterNonWhitespace() {
        assertTrue(DictationText.needsLeadingSpace("hello"))
        assertFalse(DictationText.needsLeadingSpace("hello "))
        assertFalse(DictationText.needsLeadingSpace("hello\n"))
        assertFalse(DictationText.needsLeadingSpace(""))
        assertFalse(DictationText.needsLeadingSpace(null))
    }

    @Test
    fun phrasesAndTheOneBeingSpokenAreJoinedIntoOneRun() {
        assertEquals(
            "Hello there how are you",
            DictationText.compose(listOf("hello there", "how are"), "you", startsSentence = true),
        )
    }

    @Test
    fun onlyTheFirstLetterIsCapitalizedWhenAtASentenceStart() {
        assertEquals("so I said hello", DictationText.compose(listOf("so i said"), "hello", startsSentence = false))
        assertEquals("So I said hello", DictationText.compose(listOf("so i said"), "hello", startsSentence = true))
    }

    @Test
    fun nothingHeardGivesAnEmptyText() {
        assertEquals("", DictationText.compose(emptyList(), "", startsSentence = true))
        assertEquals("", DictationText.compose(listOf("huh"), "  ", startsSentence = true))
    }
}
