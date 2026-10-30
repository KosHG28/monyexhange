package app.dirhamrub.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Key
import app.dirhamrub.ui.theme.AppTheme

private val KEY_HEIGHT = 54.dp
private val KEY_GAP = 8.dp

/**
 * Своя клавиатура калькулятора. Системная клавиатура на главном экране не появляется.
 * Кнопка «Ценник» — в правом нижнем углу, под большим пальцем.
 */
@Composable
fun Keypad(onKey: (Key) -> Unit, onCamera: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        val plus = stringResource(R.string.key_plus)
        val times = stringResource(R.string.key_times)
        val equals = stringResource(R.string.key_equals)
        val comma = stringResource(R.string.key_comma)
        KeyRow(
            left = { DigitKey(7, onKey); DigitKey(8, onKey) },
            right = { DigitKey(9, onKey); BackspaceKey(onKey) },
        )
        KeyRow(
            left = { DigitKey(4, onKey); DigitKey(5, onKey) },
            // Долгое нажатие на «+» — «=»: свернуть выражение в одно число.
            right = { DigitKey(6, onKey); OperatorKey("+", plus, onLongClick = { onKey(Key.Equals) }, longClickLabel = equals) { onKey(Key.Plus) } },
        )
        KeyRow(
            left = { DigitKey(1, onKey); DigitKey(2, onKey) },
            right = { DigitKey(3, onKey); OperatorKey("×", times) { onKey(Key.Times) } },
        )
        KeyRow(
            left = { TextKey(",", comma) { onKey(Key.Comma) }; DigitKey(0, onKey) },
            right = { CameraKey(onCamera) },
        )
    }
}

/** Ряд из двух половин: так кнопка «Ценник» ровно занимает две клавиши вместе с зазором. */
@Composable
private fun KeyRow(left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(KEY_GAP), content = left)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(KEY_GAP), content = right)
    }
}

@Composable
private fun RowScope.KeyBase(
    description: String,
    background: Color,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .weight(1f)
            .height(KEY_HEIGHT)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .combinedClickable(
                onLongClickLabel = longClickLabel,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        action()
                    }
                },
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                // Долгое нажатие доступно и из TalkBack.
                val longClick = onLongClick
                if (longClick != null) {
                    this.onLongClick(label = longClickLabel) {
                        longClick()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RowScope.DigitKey(digit: Int, onKey: (Key) -> Unit) {
    TextKey(digit.toString(), digit.toString()) { onKey(Key.Digit(digit)) }
}

@Composable
private fun RowScope.TextKey(label: String, description: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    KeyBase(description, colors.surface, onClick = onClick) {
        Text(label, style = AppTheme.type.key, color = colors.ink)
    }
}

@Composable
private fun RowScope.OperatorKey(
    label: String,
    description: String,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    // Знаки действий без подложки — только цветом: клавиатура спокойнее.
    KeyBase(description, Color.Transparent, onLongClick = onLongClick, longClickLabel = longClickLabel, onClick = onClick) {
        Text(label, style = AppTheme.type.key.copy(fontSize = 30.sp, fontWeight = FontWeight.Light), color = colors.accent)
    }
}

@Composable
private fun RowScope.BackspaceKey(onKey: (Key) -> Unit) {
    val colors = AppTheme.colors
    KeyBase(
        description = stringResource(R.string.key_backspace),
        background = Color.Transparent,
        onLongClick = { onKey(Key.Clear) },
        onClick = { onKey(Key.Backspace) },
    ) {
        Icon(painterResource(R.drawable.ic_backspace), contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun RowScope.CameraKey(onClick: () -> Unit) {
    val colors = AppTheme.colors
    KeyBase(stringResource(R.string.price_tag_a11y), colors.accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.price_tag_button), style = AppTheme.type.bodyStrong.copy(fontSize = 18.sp), color = colors.onAccent)
        }
    }
}
