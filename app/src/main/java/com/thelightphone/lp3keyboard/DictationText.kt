package com.thelightphone.lp3keyboard

/**
 * Turns what the recognizer says into text for a message. The recognizer returns lowercase words with no punctuation,
 * so this fixes the two things that always look wrong when sent as is: the word "I", and the first letter of a
 * sentence. It adds nothing else and never guesses punctuation.
 */
object DictationText {
    // The small model writes this when it hears a noise that is not a word.
    private val noise = Regex("""\bhuh\b""")
    private val loneI = Regex("""\bi\b""")

    /** [raw] is the recognizer's text. [startsSentence] is whether the text will land where a sentence begins. */
    fun format(raw: String, startsSentence: Boolean): String {
        var text = raw.trim().replace(noise, " ").replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return ""
        text = text.replace(loneI, "I")
        if (startsSentence) text = text.replaceFirstChar { it.uppercaseChar() }
        return text
    }

    /** Words can be joined onto text that already ends in a space, a newline, or nothing; otherwise a space is needed. */
    fun needsLeadingSpace(textBeforeCursor: CharSequence?): Boolean {
        val last = textBeforeCursor?.lastOrNull() ?: return false
        return !last.isWhitespace()
    }
}
