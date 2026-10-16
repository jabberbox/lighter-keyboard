package com.thelightphone.lp3keyboard

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/**
 * The dictation screen, laid out like the stock Light keyboard's: a black page where the words appear as they are
 * heard, and RETRY, X and DONE along the bottom. It listens until DONE or X, with no cut-off on a pause.
 *
 *  - DONE types the text into the field the person was in.
 *  - X, or going back, throws it away.
 *  - RETRY clears it and starts listening again.
 *
 * The recognizer runs on the phone (see [Dictation]); nothing here uses the network.
 */
class DictationActivity : ComponentActivity() {
    private var transcript by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)
    private var listening by mutableStateOf(false)

    private val phrases = mutableListOf<String>()
    private var partial = ""
    private var startsSentence = false
    private var needsSpace = false
    private var finishing = false
    private var askingForPermission = false

    private val dictation by lazy {
        Dictation(
            this,
            object : Dictation.Listener {
                override fun onState(state: Dictation.State) {
                    listening = state == Dictation.State.Listening || state == Dictation.State.Loading
                    message = when (state) {
                        Dictation.State.Loading -> "Getting ready..."
                        Dictation.State.Failed -> "Voice is not available right now."
                        else -> null
                    }
                }

                override fun onPartial(text: String) {
                    partial = text
                    refresh()
                }

                override fun onFinal(text: String) {
                    phrases += text
                    partial = ""
                    refresh()
                }
            },
        )
    }

    private val askForMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            askingForPermission = false
            if (granted) startListening() else message = "The microphone is not allowed for this keyboard."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startsSentence = intent.getBooleanExtra(EXTRA_STARTS_SENTENCE, false)
        needsSpace = intent.getBooleanExtra(EXTRA_NEEDS_SPACE, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            DictationScreen(
                text = transcript,
                message = message,
                onRetry = ::retry,
                onCancel = ::cancel,
                onDone = ::done,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (dictation.hasPermission()) {
            startListening()
        } else if (!askingForPermission) {
            askingForPermission = true
            askForMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        // Leaving the screen any other way (home, another app) drops the dictation; the microphone is never left open.
        dictation.release()
        if (!finishing && !askingForPermission) finish()
        super.onStop()
    }

    private fun startListening() {
        dictation.start()
    }

    private fun refresh() {
        transcript = DictationText.compose(phrases, partial, startsSentence)
    }

    private fun retry() {
        dictation.stop()
        phrases.clear()
        partial = ""
        refresh()
        message = null
        startListening()
    }

    private fun cancel() {
        finishing = true
        dictation.release()
        finish()
    }

    private fun done() {
        finishing = true
        dictation.stop()
        val text = DictationText.compose(phrases, partial, startsSentence)
        if (text.isNotEmpty()) DictationBridge.deliver((if (needsSpace) " " else "") + text)
        dictation.release()
        finish()
    }

    companion object {
        const val EXTRA_STARTS_SENTENCE = "starts_sentence"
        const val EXTRA_NEEDS_SPACE = "needs_space"
    }
}

@Composable
private fun DictationScreen(
    text: String,
    message: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    // A cursor that blinks where the next word will go.
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            cursorOn = !cursorOn
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(Color.Black).padding(start = 32.dp, end = 32.dp, top = 44.dp, bottom = 6.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Text(
                text = message ?: (text + if (cursorOn) "|" else " "),
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RETRY",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                modifier = Modifier.weight(1f).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onRetry).padding(vertical = 16.dp),
            )
            Box(
                Modifier.size(56.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val w = 2.5.dp.toPx()
                    drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Square)
                    drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Square)
                }
            }
            Text(
                text = "DONE",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDone).padding(vertical = 16.dp),
            )
        }
    }
}
