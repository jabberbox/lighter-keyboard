# Lighter Keyboard

A keyboard for the Light Phone III: an unofficial build of
[Light's open-source keyboard](https://github.com/lightphone/light-keyboard) that draws at the same size as the keyboard
inside the stock LightOS tools, with offline dictation on the mic key. It is a system keyboard, so it works in any app
that uses the Android keyboard, such as Signal or a browser.

This is not made by Light, and Light has not reviewed it.

## Why this exists

Light's keyboard library draws its keys at a fixed size, built for a screen 360 dp wide, which is 3.0 pixels per dp.
LightOS runs the Light Phone III at 2.55 pixels per dp, so that keyboard comes out about 15% smaller than the one in
the stock tools. This build draws the same keyboard as if the screen were 360 dp across. Measured on a Light Phone III
against the stock Messaging editor, in pixels on the 1080 wide screen:

| | Stock | Light's library as is | This build |
|---|---|---|---|
| Keys, side to side | 104 | 89 | 105 |
| Rows, top to bottom | 135 | 112 | 132 |
| Space bar | 300 to 780 | 340 to 747 | 303 to 783 |

Rows are still about 2% under stock, because the library fixes the row height. You cannot see it by eye. The change
is one file, `StockSizeKeyboardView.kt`.

## Install

1. Download the APK from [Releases](../../releases), or add this repository to
   [Obtainium](https://github.com/ImranR98/Obtainium) to get updates.
2. Open **Lighter Keyboard**. Tap **1. Enable IME** and switch on "Lighter Keyboard".
3. Tap **2. Select IME** and choose it. You can switch back to another keyboard at any time from the same picker.

It needs Android 13 or newer and a 64-bit ARM phone, which the Light Phone III is. It installs next to any other
keyboard. LightOS may warn that the app is not signed by Light, since it is not.

## What it has

- Light's keyboard as it is: letters, numbers, symbols and emoji, QWERTY layout.
- **Dictation.** The mic key opens a black full-screen page that types what you say, using the
  [Vosk](https://alphacephei.com/vosk/) recognizer and its small English model, both inside the app. Your words appear
  as you speak, with a capital at the start of a sentence and a proper "I". It adds no punctuation. **DONE** types the
  text into the field you were in, **X** (or going back) throws it away, and **RETRY** clears it and listens again. It
  listens until you press one of them, so a pause does not stop it. The first time, it asks for the microphone.

It does not have autocorrect, swipe typing, GIFs or other languages. If you want those, other keyboards for the Light
Phone exist, and this one is meant to look like the stock keyboard and nothing more.

## Privacy

The app has no network permission, so nothing you type or say can leave the phone. It uses two permissions: the
microphone, which it asks for only when you tap the mic key, and vibration for key feedback. The speech model is inside
the APK. You can check the permissions in `app/src/main/AndroidManifest.xml`.

## Status

I built this for my own Light Phone III and have tested it only there. Dictation uses a small model, so expect mistakes
with unusual words and names. Bug reports are welcome in the issues.

## Build from source

You need JDK 17 or newer, the Android SDK with API 36, and `bash`, `curl` and `unzip` for the model script.

```sh
tools/fetch-vosk-model.sh        # once: downloads the speech model (about 41 MB), checks its SHA-256
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug
```

For a signed release build, set `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` and run
`./gradlew :app:assembleRelease`. Without them the release APK is unsigned.

## Credits and licences

- The keyboard is Light's code, MIT, copyright 2026 The Light Phone. See [LICENSE](LICENSE).
- Vosk and the `vosk-model-small-en-us-0.15` model are Apache 2.0, from Alpha Cephei Inc. JNA, which Vosk needs, is Apache 2.0 or LGPL 2.1.
- The changes in this fork are released under the same MIT license.

Light, Light Phone and LightOS belong to Light.

---

# Light's original README

What follows is the README of the upstream library, kept as it was.
# LPIII Keyboard

A Compose implementation of the Light Phone's keyboard. To be used in LightOS, community tools, and/or as an Android system keyboard.

**Note that as of July 1, 2026, public releases of LightOS are not yet using this as the embedded keyboard. Coming soon!**

If you'd like to contribute/file issues, please read [CONTRIBUTING.md](CONTRIBUTING.md). For general questions/comments about the keyboard, please head to our [discussions](https://github.com/orgs/lightphone/discussions/categories/keyboard) page.

### Layouts

Currently, only English/QWERTY is supported. We want to add more languages/layouts as soon as possible. Please reach out if there are any you are particularly excited about!

## Usage

The `app` module wraps the keyboard into an Android IME app, which can be installed on any Android device

The `ui` module is an Android library that contains all the actual keyboard UI code:

Use the [Lp3Keyboard](ui/src/main/java/com/thelightphone/lp3Keyboard/ui/Lp3Keyboard.kt) composable for "embedded" usage (used in LightOS with some auxiliary UI around it)
```kotlin
@Composable
fun Lp3Keyboard(
    layout: Layout,
    options: KeyboardOptions,
    callback: Lp3KeyboardCallback,
    swipeCallback: Lp3KeyboardSwipeCallback<*>?
) 
```

Use the [Lp3KeyboardWrapper](ui/src/main/java/com/thelightphone/lp3Keyboard/ui/Lp3KeyboardWrapper.kt) composable for a self-contained version (includes a dismiss button)
```kotlin
@Composable
fun Lp3KeyboardWrapper(
    layout: Layout,
    keyboardOptions: KeyboardOptions,
    layoutOptions: LayoutOptions,
    callback: Lp3KeyboardCallback,
    swipeCallback: Lp3KeyboardSwipeCallback<*>?
) 
```

Use the [Lp3RawKeyboardView](ui/src/main/java/com/thelightphone/lp3Keyboard/ui/Lp3KeyboardView.kt) view for mixing in with classic Android views in a Java environment
```kotlin
open class Lp3RawKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) 
```

Use the [Lp3KeyboardView](ui/src/main/java/com/thelightphone/lp3Keyboard/ui/Lp3KeyboardView.kt) view for mixing in with classic Android views in Kotlin
```kotlin
class Lp3RawKeyboardView<T>(context: Context, private val viewModel: Lp3KeyboardViewModel<T>) 
```
