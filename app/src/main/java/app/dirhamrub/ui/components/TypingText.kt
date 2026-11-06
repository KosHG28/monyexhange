package app.dirhamrub.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import app.dirhamrub.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/** Что произошло со строкой ввода: дописали, стёрли с конца или заменили целиком («=», сброс, другая строка). */
private enum class Change { NONE, APPEND, DELETE, REPLACE }

/** Память строки ввода между рекомпозициями: что было и сколько раз что менялось (ключи анимаций). */
private class TypingTrack(var last: String) {
    var change = Change.NONE
    var generation = 0
    var appends = 0
    var deletes = 0
    var removed = ""

    fun update(text: String) {
        if (text == last) return
        val a = digits(last)
        val b = digits(text)
        change = when {
            b.startsWith(a) && b.length > a.length -> Change.APPEND.also { appends++ }
            a.startsWith(b) && a.length > b.length -> Change.DELETE.also {
                deletes++
                removed = a.substring(b.length)
            }
            else -> Change.REPLACE.also { generation++ }
        }
        last = text
    }

    /** Без разрядных пробелов: «1 000» продолжает «100». */
    private fun digits(s: String) = s.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }
}

/**
 * Строка ввода: новый символ «впрыгивает» с пружинкой, стёртый сжимается и гаснет, а замена целиком
 * (результат «=», сброс) — старое уходит вверх, новое поднимается снизу. При отключённых анимациях — обычный текст.
 */
@Composable
fun TypingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, modifier = modifier)
        return
    }
    val track = remember { TypingTrack(text) }
    track.update(text)
    AnimatedContent(
        targetState = track.generation to text,
        contentKey = { it.first },
        transitionSpec = {
            (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220, delayMillis = 40))) togetherWith
                (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160))) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomEnd,
        label = "typing",
        modifier = modifier,
    ) { (generation, t) ->
        val live = generation == track.generation
        Row(verticalAlignment = Alignment.Bottom) {
            if (t.isEmpty()) return@Row
            Text(t.dropLast(1), style = style, color = color, maxLines = 1, softWrap = false)
            // Последний символ — отдельно: при каждом новом нажатии он «впрыгивает».
            key(track.appends) {
                PopChar(t.last().toString(), style, color, animate = live && track.change == Change.APPEND)
            }
            if (live && track.change == Change.DELETE) {
                key(track.deletes) { Ghost(track.removed, style, color) }
            }
        }
    }
}

@Composable
private fun PopChar(ch: String, style: TextStyle, color: Color, animate: Boolean) {
    val p = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f)) }
    Text(
        ch,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            val v = p.value
            scaleX = 0.55f + 0.45f * v
            scaleY = 0.55f + 0.45f * v
            alpha = (v * 1.6f).coerceIn(0f, 1f)
            translationY = (1f - v) * size.height * 0.25f
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    )
}

/** Стёртые символы: сжимаются вместе со своим местом и гаснут. */
@Composable
private fun Ghost(text: String, style: TextStyle, color: Color) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(Unit) { p.animateTo(0f, tween(170)) }
    if (p.value <= 0f) return
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout((placeable.width * p.value).roundToInt(), placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                scaleX = 0.5f + 0.5f * p.value
                scaleY = 0.5f + 0.5f * p.value
                alpha = p.value
                transformOrigin = TransformOrigin(0f, 1f)
            },
    )
}
