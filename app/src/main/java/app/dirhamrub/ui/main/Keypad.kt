package app.dirhamrub.ui.main

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
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
import app.dirhamrub.ui.theme.Haptic
import app.dirhamrub.ui.theme.rememberHaptics
import app.dirhamrub.ui.theme.LocalReduceMotion

/** Высота клавиш: на коротких экранах ниже, чтобы сумма и подсказки не уходили под прокрутку. */
@Composable
private fun keyHeight(): Dp {
    val h = LocalConfiguration.current.screenHeightDp
    return when {
        h < SHORT_SCREEN_DP -> 40.dp
        h < MEDIUM_SCREEN_DP -> 44.dp
        else -> 48.dp
    }
}

/** Ниже этой высоты экрана (dp) — короткий телефон: клавиши и итог ниже. */
internal const val SHORT_SCREEN_DP = 760
private const val MEDIUM_SCREEN_DP = 820
private val KEY_GAP = 6.dp

/**
 * Своя клавиатура калькулятора, сетка 4 × 5 без пустот: сверху C · × · − · ⌫, справа высокие «+» и
 * камера (в правом нижнем углу, под большим пальцем, на синей подложке), снизу «0» двойной ширины и «,».
 * «=» нет: итог считается на лету (удержание «+» сворачивает выражение в одно число).
 */
@Composable
fun Keypad(
    onKey: (Key) -> Unit,
    onCamera: () -> Unit,
    modifier: Modifier = Modifier,
    /** Где на экране клавиша камеры (центр, в координатах окна): из неё раскрывается камера. */
    onCameraPlaced: (Offset) -> Unit = {},
) {
    val colors = AppTheme.colors
    val h = keyHeight()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = (maxWidth - KEY_GAP * 3) / 4
        val tall = h * 2 + KEY_GAP
        val ops = colors.surface2
        Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                KeyBase(stringResource(R.string.key_clear), ops, Modifier.size(w, h), tap = Haptic.THUD, onClick = { onKey(Key.Clear) }) {
                    Text("C", style = AppTheme.type.key.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), color = colors.muted)
                }
                IconKey(R.drawable.ic_times, stringResource(R.string.key_times), ops, Modifier.size(w, h)) { onKey(Key.Times) }
                IconKey(R.drawable.ic_minus, stringResource(R.string.key_minus), ops, Modifier.size(w, h)) { onKey(Key.Minus) }
                IconKey(
                    R.drawable.ic_backspace_line,
                    stringResource(R.string.key_backspace),
                    ops,
                    Modifier.size(w, h),
                    onLongClick = { onKey(Key.Clear) },
                ) { onKey(Key.Backspace) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    for (row in listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))) {
                        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                            for (d in row) TextKey(d.toString(), d.toString(), Modifier.size(w, h)) { onKey(Key.Digit(d)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        TextKey("0", "0", Modifier.size(w * 2 + KEY_GAP, h)) { onKey(Key.Digit(0)) }
                        TextKey(",", stringResource(R.string.key_comma), Modifier.size(w, h)) { onKey(Key.Comma) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    // Удержание «+» — «=»: свернуть выражение в одно число.
                    IconKey(
                        R.drawable.ic_plus,
                        stringResource(R.string.key_plus),
                        ops,
                        Modifier.size(w, tall),
                        onLongClick = { onKey(Key.Equals) },
                        longClickLabel = stringResource(R.string.key_equals),
                        longTap = Haptic.CONFIRM,
                    ) { onKey(Key.Plus) }
                    CameraKey(Modifier.size(w, tall), onCamera, onCameraPlaced)
                }
            }
        }
    }
}

@Composable
private fun KeyBase(
    description: String,
    background: Color,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    /** Отклик на нажатие: цифры — тонкий [Haptic.KEY], действия — щелчок, «C» — «бум». */
    tap: Haptic = Haptic.TICK,
    longTap: Haptic = Haptic.THUD,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
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
        modifier = modifier
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClickLabel = longClickLabel,
                onClick = {
                    haptics.play(tap)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.play(longTap)
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
private fun TextKey(label: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    KeyBase(description, colors.surface, modifier, tap = Haptic.KEY, onClick = onClick) {
        Text(label, style = AppTheme.type.key, color = colors.ink)
    }
}

/** Клавиша действия: контурный значок в цвет цифр на второй поверхности. */
@Composable
private fun IconKey(
    icon: Int,
    description: String,
    background: Color,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    longTap: Haptic = Haptic.THUD,
    onClick: () -> Unit,
) {
    KeyBase(description, background, modifier, onLongClick = onLongClick, longClickLabel = longClickLabel, longTap = longTap, onClick = onClick) {
        Icon(painterResource(icon), contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(24.dp))
    }
}

/** Камера: та же форма, но светло-синяя подложка — заметна и зовёт к действию; значок контурный. */
@Composable
private fun CameraKey(modifier: Modifier, onClick: () -> Unit, onPlaced: (Offset) -> Unit) {
    val colors = AppTheme.colors
    val tint = colors.accent.copy(alpha = if (colors.isDark) 0.24f else 0.12f)
    KeyBase(
        stringResource(R.string.price_tag_a11y),
        tint,
        modifier.onGloballyPositioned { onPlaced(it.boundsInRoot().center) },
        onClick = onClick,
    ) {
        Icon(painterResource(R.drawable.ic_camera_line), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
    }
}

/** Насколько клавиша «проседает» при нажатии и за сколько. */
private const val PRESSED_SCALE = 0.92f
private const val PRESS_MS = 60
