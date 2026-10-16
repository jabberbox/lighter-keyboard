package com.thelightphone.lp3keyboard

import android.content.Intent
import android.content.SharedPreferences
import android.text.InputType
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardSwipeCallback
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.layout.LayoutRegistryItem
import com.thelightphone.lp3Keyboard.ui.layout.buildRootViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback

class IMEService : LifecycleInputMethodService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    Lp3RepeatableKeyboardCallback {

    private var renderedLayout: LayoutRegistryItem? = null
    private var viewModel: Lp3KeyboardViewModel<*>? = null

    private var layoutPrefs: SharedPreferences? = null
    private val layoutChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LayoutPreferences.KEY_ACTIVE_LAYOUT) {
                refreshLayoutIfNeeded()
            }
        }

    private fun refreshLayoutIfNeeded() {
        if (LayoutPreferences.getActiveLayout(this) != renderedLayout) {
            setInputView(onCreateInputView())
        }
    }

    private fun buildViewModel(layout: LayoutRegistryItem): Lp3KeyboardViewModel<*> {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dummySwipeCallback = object : Lp3KeyboardSwipeCallback<Unit> {}
                return layout.buildRootViewModel(
                    this@IMEService,
                    dummySwipeCallback,
                    haptic = ::tick
                ) as T
            }
        }
        // Key by the layout's uniqueId so each layout gets its own retained ViewModel instance.
        return ViewModelProvider(store, factory)[layout.uniqueId, ViewModel::class.java]
                as Lp3KeyboardViewModel<*>
    }

    override fun onCreateInputView(): View {
        val layout = LayoutPreferences.getActiveLayout(this)
        val vm = buildViewModel(layout)
        renderedLayout = layout
        viewModel = vm

        // Same keyboard, drawn at the size the stock LightOS tools use (see StockSizeKeyboardView).
        val view = StockSizeKeyboardView(context = this, viewModel = vm, dictation = dictationState.asStateFlow())
        setCandidatesViewShown(false)
        window?.window?.let {
            it.decorView.apply {
                setViewTreeLifecycleOwner(this@IMEService)
                setViewTreeViewModelStoreOwner(this@IMEService)
                setViewTreeSavedStateRegistryOwner(this@IMEService)
            }
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshLayoutIfNeeded()
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        layoutPrefs = LayoutPreferences.registerOnChange(this, layoutChangeListener)
    }

    override fun onDestroy() {
        dictation.release()
        layoutPrefs?.unregisterOnSharedPreferenceChangeListener(layoutChangeListener)
        store.clear()
        super.onDestroy()
    }

    override val viewModelStore: ViewModelStore
        get() = store
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private val store = ViewModelStore()
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    private fun tick() {
        // 50ms feels good on LP3, other device motors may allow faster buzz
        vibrator.vibrate(50)
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onWindowHidden() {
        super.onWindowHidden()
        viewModel?.cancelHeldKeys()
        dictation.stop()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dictation.stop()
        super.onFinishInputView(finishingInput)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        updateCapsMode()
    }

    private fun updateCapsMode() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        // might be set if the TextField is set to capitalize sentence starts, for example
        val caps = ic.getCursorCapsMode(ei.inputType)
        viewModel?.setCapsMode(caps != 0)
    }

    // --- Dictation: speech to text on the phone, see Dictation.

    private val dictationState = MutableStateFlow(Dictation.State.Idle)

    // A phrase being spoken is shown as composing text, which the recognizer keeps rewriting until it settles.
    private var composing = false
    private var phrasePrefix = ""
    private var phraseStartsSentence = false

    private val dictation by lazy {
        Dictation(
            this,
            object : Dictation.Listener {
                override fun onState(state: Dictation.State) {
                    dictationState.value = state
                    when (state) {
                        Dictation.State.NoPermission -> askForMicrophone()
                        // Stopped partway through a phrase: what was heard so far stays as typed text.
                        Dictation.State.Idle -> endPhrase()
                        else -> {}
                    }
                }

                override fun onPartial(text: String) = showPhrase(text, final = false)

                override fun onFinal(text: String) = showPhrase(text, final = true)
            },
        )
    }

    private fun beginPhrase(ic: android.view.inputmethod.InputConnection) {
        if (composing) return
        val before = ic.getTextBeforeCursor(2, 0)
        phrasePrefix = if (DictationText.needsLeadingSpace(before)) " " else ""
        val info = currentInputEditorInfo
        val capSentences = info != null && (info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        val afterSentenceEnd = before?.lastOrNull()?.let { it == '.' || it == '?' || it == '!' } == true
        phraseStartsSentence = (info != null && ic.getCursorCapsMode(info.inputType) != 0) || (capSentences && afterSentenceEnd)
        composing = true
    }

    private fun showPhrase(text: String, final: Boolean) {
        val ic = currentInputConnection ?: return
        beginPhrase(ic)
        val words = DictationText.format(text, phraseStartsSentence)
        if (final) {
            // A finished phrase is committed with a space after it, the way the keyboard ends a typed word.
            ic.commitText(if (words.isEmpty()) "" else "$phrasePrefix$words ", 1)
            composing = false
            updateCapsMode()
        } else {
            ic.setComposingText(if (words.isEmpty()) "" else "$phrasePrefix$words", 1)
        }
    }

    /** Leaves whatever has been heard so far in the field as ordinary text. */
    private fun endPhrase() {
        if (!composing) return
        currentInputConnection?.finishComposingText()
        composing = false
    }

    /** A keyboard cannot ask for a permission itself, so the settings screen does it. */
    private fun askForMicrophone() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_ASK_MIC, true),
        )
    }

    override fun onKeyPressed(code: Int) {
    }

    override fun onSubmitWord(word: CharSequence) {
        currentInputConnection?.commitText("$word ", 1)
    }

    override fun onSpecialKeyPressed(key: SpecialKey) {
        when (key) {
            SpecialKey.Space -> {
                currentInputConnection?.commitText(" ", 1)
                updateCapsMode()
            }

            else -> {}
        }
    }

    override fun onKeyReleased(code: Int) {
        endPhrase()
        val text = buildString { appendCodePoint(code) }
        currentInputConnection?.commitText(text, 1)
        updateCapsMode()
    }

    override fun onSpecialKeyReleased(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                val ic = currentInputConnection ?: return
                val before = ic.getTextBeforeCursor(1, 0)
                val charsToDelete =
                    if (!before.isNullOrEmpty() && Character.isLowSurrogate(before[0])) 2 else 1
                ic.deleteSurroundingText(charsToDelete, 0)
                updateCapsMode()
            }

            SpecialKey.Return -> {
                endPhrase()
                currentInputConnection?.commitText("\n", 1)
            }

            SpecialKey.Voice -> {
                tick()
                dictation.toggle()
            }

            SpecialKey.Close -> {
                requestHideSelf(0)
            }

            else -> {}
        }
    }

    override fun onKeyLongPressed(code: Int) {
    }

    private fun deletePrecedingWord() {
        val ic = currentInputConnection ?: return
        // Get text before cursor to find the word boundary (max 100 chars long)
        val before = ic.getTextBeforeCursor(100, 0) ?: return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.indexOfLast { it.isWhitespace() }
        // Delete from cursor back to start of word (including trailing spaces)
        val charsToDelete = before.length - (if (lastSpace >= 0) lastSpace + 1 else 0)
        ic.deleteSurroundingText(charsToDelete, 0)
        updateCapsMode()
    }

    override fun onSpecialKeyLongPressed(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                deletePrecedingWord()
            }

            else -> {}
        }
    }

    override fun onKeyRepeated(code: Int) {
        onKeyReleased(code)
    }

    override fun onSpecialKeyRepeated(specialKey: SpecialKey) {
        when (specialKey) {
            SpecialKey.Space -> {
                currentInputConnection?.commitText(" ", 1)
                updateCapsMode()
            }

            SpecialKey.Backspace -> {
                deletePrecedingWord()
            }

            else -> {}
        }
    }
}
