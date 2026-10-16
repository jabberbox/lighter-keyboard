package com.thelightphone.lp3keyboard

import android.os.SystemClock

/**
 * Hands the text from the dictation screen back to the keyboard. The screen covers the app being typed in, and the
 * keyboard cannot type into it until the app is back in front, so the text waits here and the keyboard takes it
 * the next time a text field starts. Both live in the same process, so this is just a variable.
 *
 * Text that nobody picks up in a few seconds is thrown away, so it can never turn up in a field much later.
 */
object DictationBridge {
    private const val MAX_AGE_MS = 20_000L

    private class Pending(val text: String, val at: Long)

    @Volatile
    private var pending: Pending? = null

    fun deliver(text: String) {
        pending = Pending(text, SystemClock.elapsedRealtime())
    }

    /** The waiting text, once, or null if there is none or it is stale. */
    fun take(): String? {
        val p = pending ?: return null
        pending = null
        return p.text.takeIf { SystemClock.elapsedRealtime() - p.at <= MAX_AGE_MS }
    }
}
