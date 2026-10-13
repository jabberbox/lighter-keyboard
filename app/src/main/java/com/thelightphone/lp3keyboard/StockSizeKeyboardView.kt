package com.thelightphone.lp3keyboard

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.thelightphone.lp3Keyboard.ui.DarkKeyboardColors
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardTheme
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardWrapper
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel

/*
 * The keyboard library is drawn in fixed dp (keys 35 dp wide, 164 dp tall) for a screen 360 dp across, which is the
 * LP3 at 3.0 pixels per dp. LightOS runs the LP3 at 2.55 pixels per dp, so the keyboard came out 15% smaller than the
 * one inside the stock tools, which fill the width. This view draws the same keyboard as if the screen were 360 dp
 * across. The density and the screen size move by the same factor, so only the fixed-dp keyboard changes.
 */
private const val DESIGN_WIDTH_DP = 360f

@Composable
private fun AtDesignDensity(content: @Composable () -> Unit) {
    val current = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthPx = configuration.screenWidthDp * current.density
    val heightPx = configuration.screenHeightDp * current.density
    val target = widthPx / DESIGN_WIDTH_DP
    val scaled = remember(target, current.fontScale) { Density(target, current.fontScale) }
    val scaledConfiguration = remember(configuration, target) {
        Configuration(configuration).apply {
            screenWidthDp = (widthPx / target).toInt()
            screenHeightDp = (heightPx / target).toInt()
        }
    }
    CompositionLocalProvider(LocalDensity provides scaled, LocalConfiguration provides scaledConfiguration) {
        content()
    }
}

/** Light's keyboard, at the size the stock tools draw it. */
class StockSizeKeyboardView<T>(
    context: Context,
    private val viewModel: Lp3KeyboardViewModel<T>,
) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        AtDesignDensity {
            Lp3KeyboardTheme(DarkKeyboardColors) {
                val layout by viewModel.layoutFlow.collectAsState()
                val keyboardOptions by viewModel.keyboardOptionsFlow.collectAsState()
                val layoutOptions by viewModel.layoutOptionsFlow.collectAsState()
                // Android delivers the key events to an input method itself, and there is no external keyboard to remap.
                // The mic key is hidden: nothing in this keyboard handles dictation, so it would do nothing when tapped.
                Lp3KeyboardWrapper(
                    layout = layout,
                    keyboardOptions = keyboardOptions.copy(displayVoice = false),
                    layoutOptions = layoutOptions,
                    callback = viewModel,
                    swipeCallback = viewModel,
                    handleHardwareKeyboardInput = false,
                    remapKeyCode = null,
                )
            }
        }
    }
}