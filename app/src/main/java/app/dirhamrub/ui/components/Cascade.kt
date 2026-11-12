package app.dirhamrub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.LocalReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Часы каскада экрана: миллисекунды с его открытия. Элементы с [cascade] появляются друг за другом, выплывая
 * снизу, — часы читаются в слое отрисовки, без рекомпозиций. Без анимаций (в системе, в настройках, в режиме
 * энергосбережения — [LocalReduceMotion]) часы сразу в конце. [finish] — сразу всё на месте: коснулись экрана
 * до конца волны — она не мешает листать и нажимать.
 */
class CascadeState internal constructor(
    private val clock: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope,
) : State<Float> {
    override val value: Float get() = clock.value

    fun finish() {
        if (clock.value < CASCADE_TOTAL_MS || clock.isRunning) scope.launch { clock.snapTo(CASCADE_TOTAL_MS) }
    }
}

@Composable
fun rememberCascade(): CascadeState {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val clock = remember { Animatable(if (reduceMotion) CASCADE_TOTAL_MS else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) clock.animateTo(CASCADE_TOTAL_MS, tween(CASCADE_TOTAL_MS.toInt(), easing = LinearEasing))
    }
    return remember(clock, scope) { CascadeState(clock, scope) }
}

/** Первое касание экрана — волна сразу доходит до конца (за 0 мс), чтобы не мешать листать и нажимать. */
fun Modifier.finishCascadeOnTouch(cascade: CascadeState): Modifier = pointerInput(cascade) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        cascade.finish()
    }
}

/** Элемент номер [index] каскада: проявляется (0 → 100 %) и поднимается на место с 24 dp ниже. */
fun Modifier.cascade(clock: State<Float>, index: Int): Modifier = graphicsLayer {
    val p = progress(clock.value, startOf(index), ITEM_MS)
    alpha = p
    translationY = (1f - p) * RISE.toPx()
}

private fun startOf(index: Int): Float = DELAY_MS + minOf(index, MAX_INDEX) * STEP_MS

/** Доля пройденного отрезка [start, start + duration] по кривой Emphasized Decelerate (Material 3). */
private fun progress(now: Float, start: Float, duration: Float): Float =
    DECELERATE.transform(((now - start) / duration).coerceIn(0f, 1f))

/** Material 3 «Emphasized Decelerate»: быстрый старт, мягкая посадка на место. */
private val DECELERATE = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Экран въезжает ~0,26 с — волна начинается, когда он почти на месте; шаг 40 мс, каждый элемент 280 мс. */
private const val DELAY_MS = 120f
private const val STEP_MS = 40f
private const val ITEM_MS = 280f
private const val MAX_INDEX = 16
private val RISE = 24.dp
private const val CASCADE_TOTAL_MS = DELAY_MS + MAX_INDEX * STEP_MS + ITEM_MS
