package app.dirhamrub.ui.guide

import app.dirhamrub.domain.CountrySeasons
import app.dirhamrub.R
import org.junit.Assert.*
import org.junit.Test

class CountryEasterEggTest {
    @Test fun `every calendar entry has a vector badge and particle preset`() {
        assertEquals(CountrySeasons.all.map { it.iso }.toSet(), countryEasterEggs.keys)
        countryEasterEggs.values.forEach { egg ->
            assertNotEquals(0, egg.iconRes)
            assertNotEquals(0, egg.titleRes)
            assertNotEquals(0, egg.descriptionRes)
        }
        assertEquals(R.drawable.ic_olive_branch, countryEasterEggs.getValue("GR").iconRes)
        assertEquals(R.drawable.ic_season_moon, countryEasterEggs.getValue("AE").iconRes)
        assertEquals(ParticlePreset.PETALS, countryEasterEggs.getValue("JP").particles)
        assertEquals(ParticlePreset.SNOW, countryEasterEggs.getValue("RU").particles)
    }

    @Test fun `olive and desert animations respect particle and timing budgets`() {
        val leaves = seasonalParticleSpecs(ParticlePreset.OLIVE, 1)
        assertTrue(leaves.size in 14..18)
        leaves.forEach { assertTrue(it.duration in 1600..1900); assertTrue(it.height > it.width * 2) }
        val stars = seasonalParticleSpecs(ParticlePreset.STARS, 2)
        assertTrue(stars.size in 16..20)
        stars.forEach { assertTrue(it.duration in 1800..2200) }
    }

    @Test fun `all presets stay stable through recomposition with finite distributed particles`() {
        ParticlePreset.entries.forEach { preset ->
            val particles = seasonalParticleSpecs(preset, 42)
            assertEquals(particles, seasonalParticleSpecs(preset, 42))
            assertNotEquals(particles, seasonalParticleSpecs(preset, 43))
            assertTrue(particles.map { it.x }.distinct().size > 10)
            particles.forEach {
                assertTrue(it.x in 0f..1f && it.y in 0f..1f)
                assertTrue(it.delay in 0..350 && it.alpha in 0.7f..0.85f)
                assertTrue(it.duration > 0 && it.width > 0 && it.height > 0)
            }
        }
    }
}
