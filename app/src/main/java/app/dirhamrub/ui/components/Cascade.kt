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
import androidx.compose.ui.unit.Dp
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
    private val total: Float,
) : State<Float> {
    override val value: Float get() = clock.value

    fun finish() {
        if (clock.value < total || clock.isRunning) scope.launch { clock.snapTo(total) }
    }
}

/**
 * Ритм каскада: пауза до первого элемента, шаг между элементами, длительность и подъём одного элемента.
 * [Screen] — экран въезжает сам, волна ждёт его; [Step] — шаги знакомства сменяются на месте, сразу и быстрее.
 */
class CascadeTiming(val delayMs: Float, val stepMs: Float, val itemMs: Float, val rise: Dp) {
    val totalMs: Float get() = delayMs + MAX_INDEX * stepMs + itemMs

    companion object {
        val Screen = CascadeTiming(delayMs = 120f, stepMs = 40f, itemMs = 280f, rise = 24.dp)
        val Step = CascadeTiming(delayMs = 0f, stepMs = 35f, itemMs = 250f, rise = 20.dp)
    }
}

@Composable
fun rememberCascade(timing: CascadeTiming = CascadeTiming.Screen): CascadeState {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val total = timing.totalMs
    val clock = remember { Animatable(if (reduceMotion) total else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
    }
    return remember(clock, scope) { CascadeState(clock, scope, total) }
}

/** Первое касание экрана — волна сразу доходит до конца (за 0 мс), чтобы не мешать листать и нажимать. */
fun Modifier.finishCascadeOnTouch(cascade: CascadeState): Modifier = pointerInput(cascade) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        cascade.finish()
    }
}

/** Элемент номер [index] каскада: проявляется (0 → 100 %) и поднимается на место снизу ([CascadeTiming.rise]). */
fun Modifier.cascade(clock: State<Float>, index: Int, timing: CascadeTiming = CascadeTiming.Screen): Modifier = graphicsLayer {
    val p = progress(clock.value, timing.delayMs + minOf(index, MAX_INDEX) * timing.stepMs, timing.itemMs)
    alpha = p
    translationY = (1f - p) * timing.rise.toPx()
}

/** Доля пройденного отрезка [start, start + duration] по кривой Emphasized Decelerate (Material 3). */
private fun progress(now: Float, start: Float, duration: Float): Float =
    DECELERATE.transform(((now - start) / duration).coerceIn(0f, 1f))

/** Material 3 «Emphasized Decelerate»: быстрый старт, мягкая посадка на место. */
private val DECELERATE = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Дальше 16-го элемента волна не растягивается: остальные появляются вместе с ним. */
private const val MAX_INDEX = 16
