package app.dirhamrub.ui.main

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.Key
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Высота клавиш: на коротких экранах ниже, чтобы сумма и подсказки не уходили под прокрутку. */
@Composable
private fun keyHeight(): Dp {
    val h = LocalConfiguration.current.screenHeightDp
    return when {
        h < SHORT_SCREEN_DP -> 44.dp
        h < MEDIUM_SCREEN_DP -> 48.dp
        else -> 54.dp
    }
}

/** Ниже этой высоты экрана (dp) — короткий телефон: клавиши ниже, быстрых сумм нет. */
internal const val SHORT_SCREEN_DP = 760
private const val MEDIUM_SCREEN_DP = 820
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
    // Клавиша «проседает» при нажатии: даже короткий тап доигрывает нажатие, потом пружинит обратно.
    val source = remember { MutableInteractionSource() }
    val press = remember { Animatable(1f) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(source, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> press.animateTo(PRESSED_SCALE, tween(PRESS_MS))
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .height(keyHeight())
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
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

/** Насколько клавиша «проседает» при нажатии и за сколько. */
private const val PRESSED_SCALE = 0.92f
private const val PRESS_MS = 60
