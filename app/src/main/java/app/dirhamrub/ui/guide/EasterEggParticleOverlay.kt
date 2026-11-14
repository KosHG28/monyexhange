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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import app.dirhamrub.ui.theme.easterEggPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Cached vector silhouettes, rendered by one transparent Canvas. No input or accessibility nodes. */
@Composable
internal fun EasterEggParticleOverlay(egg: CountryEasterEgg, play: Int, modifier: Modifier = Modifier) {
    if (play == 0 || LocalReduceMotion.current) return
    val effect = egg.particles
    val dark = AppTheme.colors.isDark
    val palette = AppTheme.colors.easterEggPalette(egg.tone, egg.iso)
    val specs = remember(egg.iso, effect) { seasonalParticleSpecs(effect, egg.iso.hashCode()) }
    val sprite = painterResource(effect.spriteRes)
    val detail = painterResource(when (effect) {
        EasterEggParticleEffect.RTVELI_GRAPES -> R.drawable.ic_egg_particle_rs
        EasterEggParticleEffect.VINE_LEAVES -> R.drawable.ic_egg_particle_ge
        EasterEggParticleEffect.TET_BLOSSOMS -> R.drawable.ic_egg_particle_kr
        EasterEggParticleEffect.MIMOSA_PUFFS -> R.drawable.ic_egg_particle_in
        EasterEggParticleEffect.LAVENDER_BUDS -> R.drawable.ic_egg_badge_fr
        else -> effect.spriteRes
    })
    val filters = remember(palette) { palette.particles.map { ColorFilter.tint(it) } }
    val glow = remember(palette) {
        Brush.radialGradient(listOf(palette.particles.first(), Color.Transparent), center = Offset.Zero, radius = 1f)
    }
    val density = LocalDensity.current
    val bubbleStroke = remember(density) { Stroke(with(density) { 1.2.dp.toPx() }) }
    val progress = remember(egg.iso) { Animatable(1f) }
    LaunchedEffect(play, egg.iso) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(EASTER_EGG_CYCLE_MS, easing = LinearEasing))
    }
    Canvas(modifier.clipToBounds().clearAndSetSemantics { }) {
        if (progress.value >= 1f) return@Canvas
        val elapsed = progress.value * EASTER_EGG_CYCLE_MS
        val tau = 2f * PI.toFloat()
        specs.forEachIndexed { i, spec ->
            val p = (elapsed - spec.delay) / spec.duration
            val twinkle = effect.motion == ParticleMotion.TWINKLE
            val alpha = seasonalParticleAlpha(p, spec.opacity, dark, twinkle, spec.phase)
            if (alpha <= 0f) return@forEachIndexed
            val s = spec.size.dp.toPx()
            // A full sprite margin: both the fade and the trajectory finish inside the screen.
            val margin = s * 1.7f
            val diagonal = if (effect.motion == ParticleMotion.DIAGONAL) spec.drift.dp.toPx() * p else 0f
            val x = (size.width * spec.x + sin(p * tau + spec.phase) * spec.sway.dp.toPx() + diagonal)
                .coerceIn(margin.coerceAtMost(size.width / 2f), (size.width - margin).coerceAtLeast(size.width / 2f))
            val yFraction = particleVerticalFraction(effect.motion, p, spec.y, spec.phase)
            val y = margin + (size.height - 2f * margin).coerceAtLeast(0f) * yFraction.coerceIn(0f, 1f)
            val tint = palette.particles[i % palette.particles.size]
            val isBubble = effect == EasterEggParticleEffect.SEA_BUBBLES ||
                (effect == EasterEggParticleEffect.TURTLE_SWIM && i % 4 == 0)
            if (isBubble) {
                val radius = if (effect == EasterEggParticleEffect.SEA_BUBBLES) s / 2f else s * 0.26f
                drawCircle(tint, radius, Offset(x, y), alpha = alpha * 0.10f)
                drawCircle(tint, radius, Offset(x, y), alpha = alpha, style = bubbleStroke)
                drawCircle(tint, 0.6.dp.toPx(), Offset(x - radius * 0.32f, y - radius * 0.34f), alpha = alpha)
            } else if (effect == EasterEggParticleEffect.DESERT_STARS && i % 3 == 0) {
                drawCircle(tint, s * 0.18f, Offset(x, y), alpha = alpha)
            } else {
                val lantern = effect == EasterEggParticleEffect.RED_LANTERNS || effect == EasterEggParticleEffect.VESAK_LANTERNS ||
                    effect == EasterEggParticleEffect.FLOATING_KRATHONGS
                val cloud = effect == EasterEggParticleEffect.KHAREEF_MIST && i % 3 == 0 ||
                    effect == EasterEggParticleEffect.HOLI_POWDER && i % 4 == 0
                if (lantern || cloud) {
                    withTransform({ translate(x, y); scale(s * 1.3f, s * if (cloud) 0.7f else 1.3f, pivot = Offset.Zero) }) {
                        drawCircle(glow, radius = 1f, center = Offset.Zero, alpha = alpha * if (cloud) 0.5f else 0.22f)
                    }
                }
                if (!cloud) {
                    val flip = if (effect.flips) 0.16f + 0.84f * abs(cos(p * tau + spec.phase)) else 1f
                    val bloom = if (effect.motion == ParticleMotion.BLOOM) 0.6f + 0.4f * (p / 0.5f).coerceIn(0f, 1f) else 1f
                    val angle = if (effect == EasterEggParticleEffect.TURTLE_SWIM) -12f + sin(p * tau + spec.phase) * 10f
                        else spec.angle + spec.spin * p
                    val image = if (i % 4 == 0) detail else sprite
                    withTransform({
                        translate(x, y)
                        rotate(angle, pivot = Offset.Zero)
                        scale(flip * bloom, bloom, pivot = Offset.Zero)
                        translate(-s / 2f, -s / 2f)
                    }) {
                        with(image) { draw(Size(s, s), alpha = alpha, colorFilter = filters[i % filters.size]) }
                    }
                }
            }
        }
    }
}
