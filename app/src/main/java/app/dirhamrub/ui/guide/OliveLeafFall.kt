package app.dirhamrub.ui.guide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.OliveColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class OliveLeaf(
    val x: Float, val delay: Float, val duration: Float, val width: Float, val height: Float,
    val phase: Float, val sway: Float, val drift: Float, val angle: Float, val spin: Float, val alpha: Float,
)

/** Декоративный Canvas без pointerInput: не участвует в нажатиях и прокрутке справочника. */
@Composable
internal fun OliveLeafFall(play: Int, modifier: Modifier = Modifier) {
    if (play == 0 || LocalReduceMotion.current) return
    val progress = remember { Animatable(1f) }
    LaunchedEffect(play) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2250, easing = LinearEasing))
    }
    val leaves = remember {
        val random = Random(4701)
        List(16) { i ->
            OliveLeaf(
                x = (i + 0.25f + random.nextFloat() * 0.5f) / 16f,
                delay = random.nextFloat() * 350f, duration = 1500f + random.nextFloat() * 400f,
                width = 6f + random.nextFloat() * 3f, height = 19f + random.nextFloat() * 8f,
                phase = random.nextFloat() * 6.28f, sway = 12f + random.nextFloat() * 16f,
                drift = (random.nextFloat() - 0.5f) * 45f, angle = random.nextFloat() * 100f - 50f,
                spin = (130f + random.nextFloat() * 140f) * if (i % 2 == 0) 1f else -1f,
                alpha = 0.7f + random.nextFloat() * 0.15f,
            )
        }
    }
    val lance = remember {
        Path().apply {
            moveTo(0f, -1f)
            cubicTo(0.65f, -0.5f, 0.65f, 0.4f, 0f, 1f)
            cubicTo(-0.65f, 0.4f, -0.65f, -0.5f, 0f, -1f)
            close()
        }
    }
    Canvas(modifier.fillMaxSize().clipToBounds().clearAndSetSemantics { }) {
        val elapsed = progress.value * 2250f
        leaves.forEachIndexed { i, leaf ->
            val p = (elapsed - leaf.delay) / leaf.duration
            if (p <= 0f || p >= 1f) return@forEachIndexed
            val h = leaf.height.dp.toPx()
            val y = -h + (size.height + 2f * h) * p
            val x = size.width * leaf.x + sin(p * 2f * PI.toFloat() + leaf.phase) * leaf.sway.dp.toPx() + leaf.drift.dp.toPx() * p
            val fade = ((size.height - y) / (size.height / 3f)).coerceIn(0f, 1f) * (p / 0.08f).coerceIn(0f, 1f)
            val flip = 0.12f + 0.88f * abs(cos(p * 2.6f * PI.toFloat() + leaf.phase))
            val width = leaf.width.dp.toPx() * flip
            withTransform({
                translate(x, y)
                rotate(leaf.angle + leaf.spin * p, pivot = androidx.compose.ui.geometry.Offset.Zero)
                scale(width, h / 2f, pivot = androidx.compose.ui.geometry.Offset.Zero)
            }) {
                drawPath(lance, OliveColors.leaves[i % OliveColors.leaves.size].copy(alpha = leaf.alpha * fade))
            }
        }
    }
}
