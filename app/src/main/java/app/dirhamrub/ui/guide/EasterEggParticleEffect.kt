package app.dirhamrub.ui.guide

import androidx.annotation.DrawableRes
import app.dirhamrub.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal const val EASTER_EGG_CYCLE_MS = 1900
internal enum class ParticleMotion { FALL, DIAGONAL, RISE, DRIFT, TWINKLE, BLOOM, MIST, POWDER }

/** Each event owns its silhouette and motion profile; there is no generic fallback. */
internal enum class EasterEggParticleEffect(
    @DrawableRes val spriteRes: Int,
    val motion: ParticleMotion,
    val minSize: Float,
    val maxSize: Float,
    val sway: Float,
    val spin: Float,
    val flips: Boolean,
) {
    WINTER_SNOW(R.drawable.ic_egg_particle_ru, ParticleMotion.FALL, 8f, 13f, 12f, 18f, false),
    OAK_AUTUMN(R.drawable.ic_egg_particle_by, ParticleMotion.FALL, 10f, 16f, 24f, 100f, true),
    NAURYZ_TULIPS(R.drawable.ic_egg_particle_kz, ParticleMotion.BLOOM, 10f, 15f, 11f, 12f, false),
    TUNDUK_LIGHT(R.drawable.ic_egg_particle_kg, ParticleMotion.TWINKLE, 9f, 14f, 8f, 32f, false),
    NAVRUZ_BRANCHES(R.drawable.ic_egg_particle_tj, ParticleMotion.DIAGONAL, 11f, 16f, 16f, 45f, true),
    SPRING_SHOOTS(R.drawable.ic_egg_particle_uz, ParticleMotion.RISE, 9f, 14f, 10f, 12f, false),
    SPRING_WILDFLOWERS(R.drawable.ic_egg_particle_tm, ParticleMotion.FALL, 10f, 15f, 22f, 100f, true),
    SEMENI_GRAINS(R.drawable.ic_egg_particle_az, ParticleMotion.RISE, 9f, 14f, 7f, 8f, false),
    APRICOT_HARVEST(R.drawable.ic_egg_particle_am, ParticleMotion.FALL, 9f, 14f, 16f, 75f, true),
    MARTISOR_CHARMS(R.drawable.ic_egg_particle_md, ParticleMotion.DIAGONAL, 9f, 14f, 20f, 110f, true),
    RTVELI_GRAPES(R.drawable.ic_egg_particle_ge, ParticleMotion.FALL, 11f, 15f, 12f, 45f, false),
    ISTANBUL_PETALS(R.drawable.ic_egg_particle_tr, ParticleMotion.DIAGONAL, 9f, 15f, 24f, 110f, true),
    DESERT_STARS(R.drawable.ic_egg_particle_ae, ParticleMotion.TWINKLE, 4f, 8f, 8f, 8f, false),
    NILE_DAISIES(R.drawable.ic_egg_particle_eg, ParticleMotion.BLOOM, 9f, 14f, 24f, 90f, false),
    KHAREEF_MIST(R.drawable.ic_egg_particle_om, ParticleMotion.MIST, 5f, 10f, 22f, 8f, false),
    DHOW_SAILS(R.drawable.ic_egg_particle_qa, ParticleMotion.DRIFT, 11f, 16f, 10f, 8f, false),
    FLOATING_KRATHONGS(R.drawable.ic_egg_particle_th, ParticleMotion.DRIFT, 11f, 16f, 7f, 5f, false),
    TET_BLOSSOMS(R.drawable.ic_egg_particle_vn, ParticleMotion.DIAGONAL, 10f, 15f, 20f, 60f, true),
    GALUNGAN_LEAVES(R.drawable.ic_egg_particle_id, ParticleMotion.DIAGONAL, 11f, 17f, 26f, 80f, true),
    RED_LANTERNS(R.drawable.ic_egg_particle_my, ParticleMotion.RISE, 10f, 15f, 10f, 8f, false),
    HOLI_POWDER(R.drawable.ic_egg_particle_in, ParticleMotion.POWDER, 3f, 7f, 28f, 60f, false),
    VESAK_LANTERNS(R.drawable.ic_egg_particle_lk, ParticleMotion.RISE, 11f, 16f, 14f, 12f, false),
    SEA_BUBBLES(R.drawable.ic_egg_particle_mv, ParticleMotion.RISE, 4f, 9f, 12f, 0f, false),
    TURTLE_SWIM(R.drawable.ic_egg_particle_sc, ParticleMotion.RISE, 10f, 16f, 16f, 10f, false),
    MOONCAKE_GLOW(R.drawable.ic_egg_particle_cn, ParticleMotion.TWINKLE, 10f, 15f, 6f, 15f, false),
    SAKURA_PETALS(R.drawable.ic_egg_particle_jp, ParticleMotion.DIAGONAL, 9f, 15f, 22f, 110f, true),
    CHERRY_CLUSTERS(R.drawable.ic_egg_particle_kr, ParticleMotion.DIAGONAL, 8f, 12f, 14f, 65f, true),
    LAVENDER_BUDS(R.drawable.ic_egg_particle_fr, ParticleMotion.FALL, 8f, 13f, 16f, 45f, true),
    CARNIVAL_MASKS(R.drawable.ic_egg_particle_it, ParticleMotion.DIAGONAL, 10f, 15f, 24f, 130f, true),
    ALMOND_PETALS(R.drawable.ic_egg_particle_es, ParticleMotion.DIAGONAL, 10f, 16f, 18f, 90f, true),
    OLIVE_LEAVES(R.drawable.ic_egg_particle_gr, ParticleMotion.FALL, 12f, 18f, 24f, 130f, true),
    ALMOND_SPRIGS(R.drawable.ic_egg_particle_cy, ParticleMotion.DRIFT, 11f, 16f, 12f, 20f, true),
    MIMOSA_PUFFS(R.drawable.ic_egg_particle_me, ParticleMotion.FALL, 7f, 11f, 16f, 55f, false),
    VINE_LEAVES(R.drawable.ic_egg_particle_rs, ParticleMotion.DIAGONAL, 10f, 16f, 22f, 90f, true),
    DUTCH_TULIP_CUPS(R.drawable.ic_egg_particle_nl, ParticleMotion.DIAGONAL, 10f, 15f, 16f, 38f, false),
    CHRISTMAS_LIGHTS(R.drawable.ic_egg_particle_cz, ParticleMotion.TWINKLE, 6f, 10f, 4f, 10f, false),
    MAPLE_FALL(R.drawable.ic_egg_particle_ca, ParticleMotion.FALL, 11f, 17f, 26f, 105f, true);
}

internal data class SeasonalParticle(
    val x: Float, val y: Float, val delay: Int, val duration: Int, val size: Float,
    val phase: Float, val sway: Float, val drift: Float, val angle: Float, val spin: Float,
    /** 0..1: mapped to the theme's opacity range only during drawing. */
    val opacity: Float,
)

/** Stable across recomposition and theme changes; all staggered particles finish within 1.9 s. */
internal fun seasonalParticleSpecs(effect: EasterEggParticleEffect, seed: Int): List<SeasonalParticle> {
    val random = Random(seed)
    val count = if (effect == EasterEggParticleEffect.DESERT_STARS) 18 else 16
    return List(count) { i ->
        val delay = random.nextInt(241)
        SeasonalParticle(
            x = (i + 0.25f + random.nextFloat() * 0.5f) / count,
            y = 0.08f + random.nextFloat() * 0.84f,
            delay = delay, duration = random.nextInt(1600, EASTER_EGG_CYCLE_MS - delay + 1),
            size = effect.minSize + random.nextFloat() * (effect.maxSize - effect.minSize),
            phase = random.nextFloat() * (2f * PI.toFloat()),
            sway = effect.sway * (0.65f + random.nextFloat() * 0.35f),
            drift = 20f + random.nextFloat() * 35f,
            angle = if (effect.motion in setOf(ParticleMotion.DRIFT, ParticleMotion.RISE, ParticleMotion.BLOOM)) 0f else random.nextFloat() * 60f - 30f,
            spin = effect.spin * (0.7f + random.nextFloat() * 0.3f) * if (i % 2 == 0) 1f else -1f,
            opacity = random.nextFloat(),
        )
    }
}

/** Fade reaches zero before a particle touches either vertical edge. */
internal fun seasonalParticleAlpha(p: Float, opacity: Float, dark: Boolean, twinkle: Boolean, phase: Float): Float {
    if (p <= 0f || p >= 0.94f) return 0f
    val envelope = (p / 0.14f).coerceIn(0f, 1f) * ((0.94f - p) / 0.23f).coerceIn(0f, 1f)
    val flicker = if (twinkle) 0.55f + 0.45f * sin(p * 4f * PI.toFloat() + phase).let { it * it } else 1f
    return ((if (dark) 0.40f else 0.45f) + 0.20f * opacity) * envelope * flicker
}

internal fun particleVerticalFraction(motion: ParticleMotion, p: Float, origin: Float, phase: Float): Float = when (motion) {
    ParticleMotion.FALL, ParticleMotion.DIAGONAL -> p
    ParticleMotion.RISE, ParticleMotion.BLOOM -> 1f - p
    ParticleMotion.DRIFT, ParticleMotion.TWINKLE -> origin + sin(p * 2f + phase) * 0.018f
    ParticleMotion.MIST -> origin * 0.65f + p * 0.25f
    ParticleMotion.POWDER -> origin - sin(p * PI.toFloat()) * 0.14f
}
