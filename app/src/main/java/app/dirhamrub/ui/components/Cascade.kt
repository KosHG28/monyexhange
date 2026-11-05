package app.dirhamrub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.LocalReduceMotion

/**
 * Часы каскада шторки: миллисекунды с её открытия. Элементы с [cascade] появляются друг за другом,
 * читая часы в слое отрисовки — без рекомпозиций. При отключённых анимациях часы сразу в конце.
 */
@Composable
fun rememberCascade(): State<Float> {
    val reduceMotion = LocalReduceMotion.current
    val clock = remember { Animatable(if (reduceMotion) CASCADE_TOTAL_MS else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) clock.animateTo(CASCADE_TOTAL_MS, tween(CASCADE_TOTAL_MS.toInt(), easing = LinearEasing))
    }
    return clock.asState()
}

/** Элемент номер [index] каскада: проявляется и поднимается на место. */
fun Modifier.cascade(clock: State<Float>, index: Int): Modifier = graphicsLayer {
    val p = progress(clock.value, startOf(index), ITEM_MS)
    alpha = p
    translationY = (1f - p) * RISE.toPx()
}

/** Подсветка, пробегающая слева направо, когда появится элемент [index] (текущая строка шпаргалки). */
fun Modifier.sweep(clock: State<Float>, index: Int, color: Color): Modifier = drawBehind {
    val p = progress(clock.value, startOf(index) + ITEM_MS / 2f, SWEEP_MS)
    if (p > 0f) drawRect(color, size = Size(size.width * p, size.height))
}

private fun startOf(index: Int): Float = DELAY_MS + minOf(index, MAX_INDEX) * STEP_MS

/** Доля пройденного отрезка [start, start + duration] с замедлением к концу. */
private fun progress(now: Float, start: Float, duration: Float): Float {
    val q = 1f - ((now - start) / duration).coerceIn(0f, 1f)
    return 1f - q * q * q
}

/** Шторка выезжает ~0,3 с — первые строки начинают проявляться чуть позже начала. */
private const val DELAY_MS = 110f
private const val STEP_MS = 32f
private const val ITEM_MS = 240f
private const val SWEEP_MS = 320f
private const val MAX_INDEX = 16
private val RISE = 14.dp
private const val CASCADE_TOTAL_MS = DELAY_MS + MAX_INDEX * STEP_MS + ITEM_MS + SWEEP_MS
