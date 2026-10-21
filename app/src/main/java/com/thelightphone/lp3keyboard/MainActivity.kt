package com.thelightphone.lp3keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.lp3Keyboard.ui.layout.LayoutRegistryItem
import java.io.File

class MainActivity : AppCompatActivity() {
    private val micGranted = mutableStateOf(false)
    private val enabled = mutableStateOf(false)
    private val inUse = mutableStateOf(false)

    private val askForMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted.value = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
        refresh()
        // The keyboard opens this screen when the mic key is tapped without the permission.
        if (intent?.getBooleanExtra(EXTRA_ASK_MIC, false) == true && !micGranted.value) {
            askForMic.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent {
            SetupScreen(
                enabled = enabled.value,
                inUse = inUse.value,
                micGranted = micGranted.value,
                onBack = { finish() },
                onEnable = { startActivity(android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                onChoose = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                onAskMic = { askForMic.launch(Manifest.permission.RECORD_AUDIO) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // The keyboard picker is a dialog, not a screen, so coming back from it only returns focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()
    }

    private fun refresh() {
        micGranted.value = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val imm = getSystemService(InputMethodManager::class.java)
        enabled.value = imm.enabledInputMethodList.any { it.packageName == packageName }
        inUse.value = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
    }

    companion object {
        const val EXTRA_ASK_MIC = "ask_mic"
    }
}

/*
 * LightOS lays its screens out in units of the screen width: 27 across, so 40 px on the 1080 px Light Phone III, and
 * sizes text in design pixels scaled by width / 480 (2.25 px each). The style numbers are the ones in Light's table:
 * 24.5 for rows, 20 for the lighter lines and the top bar title.
 */
private val Gray = Color(0xFFBBBBBB)

private class LightScale(private val widthPx: Float, private val density: androidx.compose.ui.unit.Density) {
    val unit: Dp get() = with(density) { (widthPx / 27f).toDp() }
    fun units(n: Float): Dp = with(density) { (widthPx / 27f * n).toDp() }
    fun text(designPx: Float): TextUnit = with(density) { (designPx * widthPx / 480f).toDp().toSp() }
}

/** Akkurat is a system font on the Light Phone III. Anywhere else this falls back to the default sans serif. */
private fun lightFont(): FontFamily {
    val regular = File("/system/fonts/AkkuratLLTT-Regular.ttf")
    return if (regular.exists()) FontFamily(Font(regular, FontWeight.Normal)) else FontFamily.SansSerif
}

@Composable
private fun SetupScreen(
    enabled: Boolean,
    inUse: Boolean,
    micGranted: Boolean,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onChoose: () -> Unit,
    onAskMic: () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = LocalConfiguration.current.screenWidthDp * density.density
    val scale = remember(widthPx, density) { LightScale(widthPx, density) }
    val font = remember { lightFont() }
    val row = remember(scale) { TextStyle(fontFamily = font, fontSize = scale.text(24.5f), color = Color.White) }
    val line = remember(scale) { TextStyle(fontFamily = font, fontSize = scale.text(20f), color = Gray) }
    val bar = remember(scale) { TextStyle(fontFamily = font, fontSize = scale.text(20f), color = Color.White, textAlign = TextAlign.Center) }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        TopBar(title = "Lighter Keyboard", style = bar, scale = scale, onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = scale.units(2f)),
        ) {
            Spacer(Modifier.height(scale.units(0.5f)))
            SetupRow(
                title = "Turn on the keyboard",
                status = if (enabled) "On" else "Off. Tap to open Android's keyboard list.",
                rowStyle = row, lineStyle = line, scale = scale, onClick = onEnable,
            )
            SetupRow(
                title = "Use it as your keyboard",
                status = if (inUse) "In use" else "Not in use. Tap to choose.",
                rowStyle = row, lineStyle = line, scale = scale, onClick = onChoose,
            )
            SetupRow(
                title = if (micGranted) "Microphone" else "Allow the microphone",
                status = if (micGranted) "Allowed. Dictation is on." else "Dictation needs it.",
                rowStyle = row, lineStyle = line, scale = scale, onClick = { if (!micGranted) onAskMic() },
            )
            LayoutRows(row, line, scale)
            Spacer(Modifier.height(scale.units(1f)))
            TryField(row, line, scale)
            Spacer(Modifier.height(scale.units(1.5f)))
            BasicText(
                text = "Unofficial. Not made by Light.",
                style = line,
                modifier = Modifier.padding(bottom = scale.units(1f)),
            )
        }
    }
}

@Composable
private fun TopBar(title: String, style: TextStyle, scale: LightScale, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(scale.units(3f))) {
        val tap = remember { MutableInteractionSource() }
        Box(
            Modifier.align(Alignment.CenterStart).size(width = scale.units(3.3f), height = scale.units(3f)).clickable(tap, indication = null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(scale.units(1f))) {
                val stroke = Stroke(width = size.width * 0.14f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                val top = Offset(size.width * 0.7f, size.height * 0.05f)
                val tip = Offset(size.width * 0.2f, size.height * 0.5f)
                val bottom = Offset(size.width * 0.7f, size.height * 0.95f)
                drawLine(Color.White, top, tip, stroke.width, stroke.cap)
                drawLine(Color.White, tip, bottom, stroke.width, stroke.cap)
            }
        }
        BasicText(text = title, style = style, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun SetupRow(
    title: String,
    status: String,
    rowStyle: TextStyle,
    lineStyle: TextStyle,
    scale: LightScale,
    onClick: () -> Unit,
) {
    val tap = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxWidth().clickable(tap, indication = null, onClick = onClick).padding(vertical = scale.units(0.75f)),
    ) {
        BasicText(text = title, style = rowStyle)
        BasicText(text = status, style = lineStyle, modifier = Modifier.padding(top = scale.units(0.15f)))
    }
}

/** The layouts the keyboard library has. Tapping the row opens the list, and choosing one closes it again. */
@Composable
private fun LayoutRows(rowStyle: TextStyle, lineStyle: TextStyle, scale: LightScale) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LayoutPreferences.getActiveLayout(context)) }
    var open by remember { mutableStateOf(false) }
    SetupRow(
        title = "Layout",
        status = if (open) "Choose one." else selected.label,
        rowStyle = rowStyle, lineStyle = lineStyle, scale = scale, onClick = { open = !open },
    )
    if (open) {
        LayoutRegistryItem.entries.forEach { item ->
            val tap = remember { MutableInteractionSource() }
            Column(
                Modifier.fillMaxWidth().clickable(tap, indication = null) {
                    selected = item
                    LayoutPreferences.setActiveLayout(context, item)
                    open = false
                }.padding(start = scale.units(1f), top = scale.units(0.4f), bottom = scale.units(0.4f)),
            ) {
                BasicText(
                    text = if (item == selected) "${item.label}, in use" else item.label,
                    style = if (item == selected) rowStyle else lineStyle,
                )
            }
        }
    }
}

/** A box to type in, drawn like Light's text entry: the text over a plain underline. */
@Composable
private fun TryField(rowStyle: TextStyle, lineStyle: TextStyle, scale: LightScale) {
    var text by remember { mutableStateOf("") }
    BasicText(text = "Try it here", style = lineStyle)
    Spacer(Modifier.height(scale.units(0.3f)))
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = rowStyle,
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(scale.units(0.4f)))
    Box(Modifier.fillMaxWidth().height(scale.units(0.075f)).background(Color.White))
}