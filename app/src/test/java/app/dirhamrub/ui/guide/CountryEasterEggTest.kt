package app.dirhamrub.ui.guide

import app.dirhamrub.domain.CountrySeasons
import org.junit.Assert.*
import org.junit.Test

class CountryEasterEggTest {
    @Test fun `every calendar event has its own explicit effect and vector badge`() {
        assertEquals(CountrySeasons.all.map { it.iso }.toSet(), countryEasterEggs.keys)
        val eggs = countryEasterEggs.values
        assertEquals(37, eggs.size)
        assertEquals(eggs.size, eggs.map { it.particles }.toSet().size)
        assertEquals(EasterEggParticleEffect.entries.toSet(), eggs.map { it.particles }.toSet())
        assertEquals(eggs.size, eggs.map { it.iconRes }.toSet().size)
        assertEquals(eggs.size, eggs.map { it.particles.spriteRes }.toSet().size)
        eggs.forEach {
            assertNotEquals(0, it.iconRes)
            assertNotEquals(0, it.titleRes)
            assertNotEquals(0, it.descriptionRes)
            assertNotEquals(0, it.particles.spriteRes)
        }
    }

    @Test fun `stagger never extends an effect beyond two seconds`() {
        EasterEggParticleEffect.entries.forEach { effect ->
            repeat(30) { seed ->
                val particles = seasonalParticleSpecs(effect, seed)
                assertTrue(particles.size in 14..20)
                particles.forEach {
                    assertTrue(it.delay in 0..350)
                    assertTrue(it.duration >= 1600)
                    assertTrue(it.delay + it.duration <= EASTER_EGG_CYCLE_MS)
                    assertTrue(it.delay + it.duration <= 2000)
                    assertTrue(it.size in effect.minSize..effect.maxSize)
                }
            }
        }
    }

    @Test fun `all effects keep the same distributed particles after a theme change`() {
        EasterEggParticleEffect.entries.forEach { effect ->
            val particles = seasonalParticleSpecs(effect, 42)
            assertEquals(particles, seasonalParticleSpecs(effect, 42))
            assertNotEquals(particles, seasonalParticleSpecs(effect, 43))
            assertTrue(particles.map { it.x }.distinct().size > 10)
            particles.forEach {
                assertTrue(it.x in 0f..1f && it.y in 0f..1f)
                assertTrue(it.opacity in 0f..1f)
                assertTrue(it.size > 0 && it.phase.isFinite() && it.spin.isFinite())
            }
        }
    }

    @Test fun `particles fade before the edge and respect both theme opacity limits`() {
        for (dark in listOf(false, true)) {
            for (twinkle in listOf(false, true)) {
                for (opacity in listOf(0f, 0.5f, 1f)) {
                    for (step in -10..110) {
                        val p = step / 100f
                        val a = seasonalParticleAlpha(p, opacity, dark, twinkle, 1.2f)
                        assertTrue(a.isFinite() && a in 0f..if (dark) 0.60001f else 0.65001f)
                        if (p <= 0f || p >= 0.94f) assertEquals(0f, a, 0f)
                    }
                    val peak = seasonalParticleAlpha(0.5f, opacity, dark, false, 0f)
                    assertTrue(peak >= if (dark) 0.40f else 0.45f)
                }
            }
        }
    }

    @Test fun `sea life rises while leaves and snow fall`() {
        for (effect in listOf(EasterEggParticleEffect.SEA_BUBBLES, EasterEggParticleEffect.TURTLE_SWIM, EasterEggParticleEffect.VESAK_LANTERNS)) {
            assertTrue(particleVerticalFraction(effect.motion, 0.1f, 0.5f, 0f) > particleVerticalFraction(effect.motion, 0.9f, 0.5f, 0f))
        }
        for (effect in listOf(EasterEggParticleEffect.OLIVE_LEAVES, EasterEggParticleEffect.WINTER_SNOW, EasterEggParticleEffect.MAPLE_FALL)) {
            assertTrue(particleVerticalFraction(effect.motion, 0.1f, 0.5f, 0f) < particleVerticalFraction(effect.motion, 0.9f, 0.5f, 0f))
        }
        ParticleMotion.entries.forEach { motion ->
            for (step in 0..100) {
                val y = particleVerticalFraction(motion, step / 100f, 0.5f, 1f)
                assertTrue(y.isFinite() && y in 0f..1f)
            }
        }
    }
}
