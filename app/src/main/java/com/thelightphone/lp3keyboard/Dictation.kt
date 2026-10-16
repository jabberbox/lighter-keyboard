package com.thelightphone.lp3keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.Executors

/**
 * Speech to text that runs entirely on the phone, with the Vosk recognizer and a small English model that ships
 * inside this app. Nothing is sent anywhere: the app has no network permission.
 *
 * Everything here is called on the main thread. Results come back on the main thread too.
 */
class Dictation(private val context: Context, private val listener: Listener) {
    enum class State {
        /** Not listening. */
        Idle,

        /** Getting the model ready. Only slow the first time after the keyboard starts. */
        Loading,

        /** The microphone is open. */
        Listening,

        /** The microphone permission has not been given. */
        NoPermission,

        /** The model or the microphone could not be started. */
        Failed,
    }

    interface Listener {
        fun onState(state: State)

        /** What is being said right now; it may still change. */
        fun onPartial(text: String)

        /** A finished phrase. */
        fun onFinal(text: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    private var service: SpeechService? = null

    var state: State = State.Idle
        private set

    val listening: Boolean get() = state == State.Listening || state == State.Loading

    private fun set(next: State) {
        state = next
        listener.onState(next)
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts listening, or stops if it already is. */
    fun toggle() {
        if (listening) stop() else start()
    }

    fun start() {
        if (listening) return
        if (!hasPermission()) return set(State.NoPermission)
        set(State.Loading)
        val loaded = model
        if (loaded != null) {
            begin(loaded)
            return
        }
        worker.execute {
            val result = runCatching { loadModel() }
            main.post {
                // The person may have stopped while the model was loading.
                if (state != State.Loading) {
                    result.getOrNull()?.let { model = it }
                    return@post
                }
                result.fold(
                    onSuccess = { m ->
                        model = m
                        begin(m)
                    },
                    onFailure = { set(State.Failed) },
                )
            }
        }
    }

    fun stop() {
        service?.let {
            // Cancel, not stop: stop would deliver one more result after the screen already kept what was heard.
            it.cancel()
            it.shutdown()
        }
        service = null
        set(State.Idle)
    }

    /** Frees the recognizer's memory. The model loads again the next time it is needed. */
    fun release() {
        stop()
        model?.close()
        model = null
    }

    private fun begin(model: Model) {
        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            val speech = SpeechService(recognizer, SAMPLE_RATE)
            service = speech
            val started = speech.startListening(
                object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        text(hypothesis, "partial")?.let(listener::onPartial)
                    }

                    override fun onResult(hypothesis: String?) {
                        text(hypothesis, "text")?.let(listener::onFinal)
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        text(hypothesis, "text")?.let(listener::onFinal)
                    }

                    override fun onError(exception: Exception?) {
                        service = null
                        set(State.Failed)
                    }

                    override fun onTimeout() {
                        stop()
                    }
                },
                MAX_LISTEN_MS,
            )
            if (started) set(State.Listening) else {
                service = null
                set(State.Failed)
            }
        } catch (e: Exception) {
            service = null
            set(State.Failed)
        }
    }

    private fun text(json: String?, key: String): String? =
        runCatching { JSONObject(json ?: return null).optString(key) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Copies the bundled model out of the app's assets the first time, then opens it. */
    private fun loadModel(): Model {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val target = File(context.filesDir, "vosk/$MODEL_DIR")
        val done = File(target, ".complete-$MODEL_VERSION")
        if (!done.exists()) {
            target.deleteRecursively()
            copyAssets(MODEL_ASSET, target)
            done.writeText(MODEL_VERSION)
        }
        return Model(target.absolutePath)
    }

    private fun copyAssets(path: String, to: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            to.parentFile?.mkdirs()
            context.assets.open(path).use { input -> to.outputStream().use { input.copyTo(it) } }
        } else {
            to.mkdirs()
            children.forEach { copyAssets("$path/$it", File(to, it)) }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16000.0f
        const val MODEL_ASSET = "model-en-us"
        const val MODEL_DIR = "model-en-us-0.15"
        const val MODEL_VERSION = "vosk-model-small-en-us-0.15"

        /** A safety stop so the microphone is never left open by accident. */
        const val MAX_LISTEN_MS = 3 * 60 * 1000
    }
}
