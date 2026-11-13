package app.dirhamrub.ui.guide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.easterEggPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal data class SeasonalParticle(
    val x: Float, val y: Float, val delay: Int, val duration: Int, val width: Float, val height: Float,
    val phase: Float, val sway: Float, val drift: Float, val angle: Float, val spin: Float, val alpha: Float,
)

/** Детерминированная раскладка: пересборка композиции и смена темы не переставляют частицы. */
internal fun seasonalParticleSpecs(preset: ParticlePreset, seed: Int): List<SeasonalParticle> {
    val random = Random(seed)
    val stars = preset == ParticlePreset.STARS
    val olive = preset == ParticlePreset.OLIVE
    val count = if (stars) 18 else 16
    return List(count) { i ->
        SeasonalParticle(
            x = (i + 0.25f + random.nextFloat() * 0.5f) / count,
            y = 0.04f + random.nextFloat() * 0.88f,
            delay = random.nextInt(351), duration = if (stars) random.nextInt(1800, 2201) else if (olive) random.nextInt(1600, 1901) else random.nextInt(1700, 2201),
            width = if (olive) 6f + random.nextFloat() * 3f else if (stars) 3f + random.nextFloat() * 5f else 7f + random.nextFloat() * 4f,
            height = if (olive) 19f + random.nextFloat() * 8f else 9f + random.nextFloat() * 6f,
            phase = random.nextFloat() * 6.28f, sway = 12f + random.nextFloat() * 16f,
            drift = (random.nextFloat() - 0.5f) * 45f, angle = random.nextFloat() * 100f - 50f,
            spin = (130f + random.nextFloat() * 140f) * if (i % 2 == 0) 1f else -1f,
            alpha = 0.7f + random.nextFloat() * 0.15f,
        )
    }
}

/** Root overlay: no pointerInput/clickable, and no accessibility nodes; scroll and taps pass through. */
@Composable
internal fun SeasonalParticles(egg: CountryEasterEgg, play: Int, modifier: Modifier = Modifier) {
    if (play == 0 || LocalReduceMotion.current) return
    val palette = AppTheme.colors.easterEggPalette(egg.tone)
    val specs = remember(egg.iso, egg.particles) { seasonalParticleSpecs(egg.particles, egg.iso.hashCode()) }
    val duration = remember(specs) { specs.maxOf { it.delay + it.duration } }
    val progress = remember(egg.iso) { Animatable(1f) }
    LaunchedEffect(play, egg.iso) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
    }
    val leaf = remember { Path().apply {
        moveTo(0f, -1f); cubicTo(0.65f, -0.5f, 0.65f, 0.4f, 0f, 1f)
        cubicTo(-0.65f, 0.4f, -0.65f, -0.5f, 0f, -1f); close()
    } }
    val petal = remember { Path().apply {
        moveTo(0f, -1f); cubicTo(1.4f, -0.7f, 1.1f, 0.5f, 0f, 1f)
        cubicTo(-1.1f, 0.5f, -1.4f, -0.7f, -0.3f, -0.8f); lineTo(0f, -0.55f); close()
    } }
    val star = remember { Path().apply {
        moveTo(0f, -1f); lineTo(0.22f, -0.22f); lineTo(1f, 0f); lineTo(0.22f, 0.22f)
        lineTo(0f, 1f); lineTo(-0.22f, 0.22f); lineTo(-1f, 0f); lineTo(-0.22f, -0.22f); close()
    } }
    val wave = remember { Path().apply { moveTo(-1f, 0f); cubicTo(-0.5f, -1f, 0.5f, 1f, 1f, 0f) } }
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        if (progress.value >= 1f) return@Canvas
        val elapsed = progress.value * duration
        val tau = 2f * PI.toFloat()
        specs.forEachIndexed { i, spec ->
            val p = (elapsed - spec.delay) / spec.duration
            if (p <= 0f || p >= 1f) return@forEachIndexed
            val stars = egg.particles == ParticlePreset.STARS
            val h = spec.height.dp.toPx()
            val x = size.width * spec.x + sin(p * tau + spec.phase) * spec.sway.dp.toPx() + spec.drift.dp.toPx() * p
            val y = if (stars) size.height * spec.y - p * 32.dp.toPx() else -h + (size.height + 2f * h) * p
            val fade = if (stars) sin(p * PI.toFloat()).coerceAtLeast(0f) * (0.65f + 0.35f * sin(p * tau * 2f + spec.phase).pow2())
                else ((size.height - y) / (size.height / 3f)).coerceIn(0f, 1f) * (p / 0.08f).coerceIn(0f, 1f)
            val flip = if (stars) 1f else 0.12f + 0.88f * abs(cos(p * 2.6f * PI.toFloat() + spec.phase))
            val w = spec.width.dp.toPx() * flip
            val tint = palette.particles[i % palette.particles.size].copy(alpha = spec.alpha * fade)
            withTransform({
                translate(x, y)
                rotate(spec.angle + spec.spin * p * if (stars) 0.12f else 1f, pivot = Offset.Zero)
                scale(w, if (stars) w else h / 2f, pivot = Offset.Zero)
            }) {
                when (egg.particles) {
                    ParticlePreset.OLIVE, ParticlePreset.LEAVES -> drawPath(leaf, tint)
                    ParticlePreset.PETALS -> drawPath(petal, tint)
                    ParticlePreset.STARS -> if (i % 3 == 0) drawCircle(tint, 0.35f, Offset.Zero) else drawPath(star, tint)
                    ParticlePreset.SNOW -> {
                        repeat(3) { arm ->
                            withTransform({ rotate(arm * 60f, Offset.Zero) }) {
                                drawLine(tint, Offset(0f, -1f), Offset(0f, 1f), strokeWidth = 0.16f)
                                drawLine(tint, Offset(0f, -0.45f), Offset(0.3f, -0.75f), strokeWidth = 0.12f)
                                drawLine(tint, Offset(0f, -0.45f), Offset(-0.3f, -0.75f), strokeWidth = 0.12f)
                            }
                        }
                    }
                    ParticlePreset.WATER -> drawPath(wave, tint, style = Stroke(0.18f))
                    ParticlePreset.FRUIT -> { drawCircle(tint, 0.6f, Offset.Zero); drawPath(leaf, tint, alpha = 0.65f) }
                    ParticlePreset.CONFETTI -> drawRect(tint, Offset(-0.3f, -0.65f), androidx.compose.ui.geometry.Size(0.6f, 1.3f))
                }
            }
        }
    }
}

private fun Float.pow2() = this * this
