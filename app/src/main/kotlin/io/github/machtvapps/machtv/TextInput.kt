package io.github.machtvapps.machtv

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_MS, easing = OutCubic), label = "field")
    var editing by remember { mutableStateOf(false) }
    if (!focused && editing) editing = false
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = !editing,
        textStyle = MachText.field.copy(color = lerp(MetalText, MetalInk, t)),
        cursorBrush = SolidColor(lerp(MetalText, MetalInk, t)),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            editing = false
            onDone()
        }),
        interactionSource = interaction,
        modifier = modifier.onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            if (key.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && key.keyCode != KeyEvent.KEYCODE_ENTER && key.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) {
                return@onPreviewKeyEvent false
            }
            if (key.action == KeyEvent.ACTION_UP) {
                editing = true
                keyboard?.show()
            }
            true
        },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .pillShadow(rest = 0f, corner = 12.dp) { t }
                    .metalBody(corner = 12.dp) { t }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    BasicText(
                        placeholder,
                        style = MachText.field.copy(color = lerp(MetalText.copy(alpha = 0.6f), MetalInk.copy(alpha = 0.55f), t)),
                    )
                }
                inner()
            }
        },
    )
}
