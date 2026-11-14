package app.dirhamrub.ui.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.dirhamrub.R
import app.dirhamrub.ui.theme.EasterEggTone

internal data class CountryEasterEgg(
    val iso: String,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val particles: EasterEggParticleEffect,
    val tone: EasterEggTone,
)

/** One explicit effect per calendar entry; dates remain in CountrySeasons. */
internal val countryEasterEggs = listOf(
    CountryEasterEgg("RU", R.drawable.ic_egg_badge_ru, R.string.season_ru_title, R.string.season_ru_description, EasterEggParticleEffect.WINTER_SNOW, EasterEggTone.WINTER),
    CountryEasterEgg("BY", R.drawable.ic_egg_badge_by, R.string.season_by_title, R.string.season_by_description, EasterEggParticleEffect.OAK_AUTUMN, EasterEggTone.HARVEST),
    CountryEasterEgg("KZ", R.drawable.ic_egg_badge_kz, R.string.season_kz_title, R.string.season_kz_description, EasterEggParticleEffect.NAURYZ_TULIPS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("KG", R.drawable.ic_egg_badge_kg, R.string.season_kg_title, R.string.season_kg_description, EasterEggParticleEffect.TUNDUK_LIGHT, EasterEggTone.DESERT),
    CountryEasterEgg("TJ", R.drawable.ic_egg_badge_tj, R.string.season_tj_title, R.string.season_tj_description, EasterEggParticleEffect.NAVRUZ_BRANCHES, EasterEggTone.BLOSSOM),
    CountryEasterEgg("UZ", R.drawable.ic_egg_badge_uz, R.string.season_uz_title, R.string.season_uz_description, EasterEggParticleEffect.SPRING_SHOOTS, EasterEggTone.FOREST),
    CountryEasterEgg("TM", R.drawable.ic_egg_badge_tm, R.string.season_tm_title, R.string.season_tm_description, EasterEggParticleEffect.SPRING_WILDFLOWERS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("AZ", R.drawable.ic_egg_badge_az, R.string.season_az_title, R.string.season_az_description, EasterEggParticleEffect.SEMENI_GRAINS, EasterEggTone.FOREST),
    CountryEasterEgg("AM", R.drawable.ic_egg_badge_am, R.string.season_am_title, R.string.season_am_description, EasterEggParticleEffect.APRICOT_HARVEST, EasterEggTone.HARVEST),
    CountryEasterEgg("MD", R.drawable.ic_egg_badge_md, R.string.season_md_title, R.string.season_md_description, EasterEggParticleEffect.MARTISOR_CHARMS, EasterEggTone.FESTIVAL),
    CountryEasterEgg("GE", R.drawable.ic_egg_badge_ge, R.string.season_ge_title, R.string.season_ge_description, EasterEggParticleEffect.RTVELI_GRAPES, EasterEggTone.HARVEST),
    CountryEasterEgg("TR", R.drawable.ic_egg_badge_tr, R.string.season_tr_title, R.string.season_tr_description, EasterEggParticleEffect.ISTANBUL_PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("AE", R.drawable.ic_egg_badge_ae, R.string.season_ae_title, R.string.season_ae_description, EasterEggParticleEffect.DESERT_STARS, EasterEggTone.DESERT),
    CountryEasterEgg("EG", R.drawable.ic_egg_badge_eg, R.string.season_eg_title, R.string.season_eg_description, EasterEggParticleEffect.NILE_DAISIES, EasterEggTone.BLOSSOM),
    CountryEasterEgg("OM", R.drawable.ic_egg_badge_om, R.string.season_om_title, R.string.season_om_description, EasterEggParticleEffect.KHAREEF_MIST, EasterEggTone.SEA),
    CountryEasterEgg("QA", R.drawable.ic_egg_badge_qa, R.string.season_qa_title, R.string.season_qa_description, EasterEggParticleEffect.DHOW_SAILS, EasterEggTone.SEA),
    CountryEasterEgg("TH", R.drawable.ic_egg_badge_th, R.string.season_th_title, R.string.season_th_description, EasterEggParticleEffect.FLOATING_KRATHONGS, EasterEggTone.DESERT),
    CountryEasterEgg("VN", R.drawable.ic_egg_badge_vn, R.string.season_vn_title, R.string.season_vn_description, EasterEggParticleEffect.TET_BLOSSOMS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("ID", R.drawable.ic_egg_badge_id, R.string.season_id_title, R.string.season_id_description, EasterEggParticleEffect.GALUNGAN_LEAVES, EasterEggTone.FOREST),
    CountryEasterEgg("MY", R.drawable.ic_egg_badge_my, R.string.season_my_title, R.string.season_my_description, EasterEggParticleEffect.RED_LANTERNS, EasterEggTone.FESTIVAL),
    CountryEasterEgg("IN", R.drawable.ic_egg_badge_in, R.string.season_in_title, R.string.season_in_description, EasterEggParticleEffect.HOLI_POWDER, EasterEggTone.FESTIVAL),
    CountryEasterEgg("LK", R.drawable.ic_egg_badge_lk, R.string.season_lk_title, R.string.season_lk_description, EasterEggParticleEffect.VESAK_LANTERNS, EasterEggTone.DESERT),
    CountryEasterEgg("MV", R.drawable.ic_egg_badge_mv, R.string.season_mv_title, R.string.season_mv_description, EasterEggParticleEffect.SEA_BUBBLES, EasterEggTone.SEA),
    CountryEasterEgg("SC", R.drawable.ic_egg_badge_sc, R.string.season_sc_title, R.string.season_sc_description, EasterEggParticleEffect.TURTLE_SWIM, EasterEggTone.SEA),
    CountryEasterEgg("CN", R.drawable.ic_egg_badge_cn, R.string.season_cn_title, R.string.season_cn_description, EasterEggParticleEffect.MOONCAKE_GLOW, EasterEggTone.DESERT),
    CountryEasterEgg("JP", R.drawable.ic_egg_badge_jp, R.string.season_jp_title, R.string.season_jp_description, EasterEggParticleEffect.SAKURA_PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("KR", R.drawable.ic_egg_badge_kr, R.string.season_kr_title, R.string.season_kr_description, EasterEggParticleEffect.CHERRY_CLUSTERS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("FR", R.drawable.ic_egg_badge_fr, R.string.season_fr_title, R.string.season_fr_description, EasterEggParticleEffect.LAVENDER_BUDS, EasterEggTone.LAVENDER),
    CountryEasterEgg("IT", R.drawable.ic_egg_badge_it, R.string.season_it_title, R.string.season_it_description, EasterEggParticleEffect.CARNIVAL_MASKS, EasterEggTone.FESTIVAL),
    CountryEasterEgg("ES", R.drawable.ic_egg_badge_es, R.string.season_es_title, R.string.season_es_description, EasterEggParticleEffect.ALMOND_PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("GR", R.drawable.ic_egg_badge_gr, R.string.season_gr_title, R.string.season_gr_description, EasterEggParticleEffect.OLIVE_LEAVES, EasterEggTone.OLIVE),
    CountryEasterEgg("CY", R.drawable.ic_egg_badge_cy, R.string.season_cy_title, R.string.season_cy_description, EasterEggParticleEffect.ALMOND_SPRIGS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("ME", R.drawable.ic_egg_badge_me, R.string.season_me_title, R.string.season_me_description, EasterEggParticleEffect.MIMOSA_PUFFS, EasterEggTone.HARVEST),
    CountryEasterEgg("RS", R.drawable.ic_egg_badge_rs, R.string.season_rs_title, R.string.season_rs_description, EasterEggParticleEffect.VINE_LEAVES, EasterEggTone.HARVEST),
    CountryEasterEgg("NL", R.drawable.ic_egg_badge_nl, R.string.season_nl_title, R.string.season_nl_description, EasterEggParticleEffect.DUTCH_TULIP_CUPS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("CZ", R.drawable.ic_egg_badge_cz, R.string.season_cz_title, R.string.season_cz_description, EasterEggParticleEffect.CHRISTMAS_LIGHTS, EasterEggTone.WINTER),
    CountryEasterEgg("CA", R.drawable.ic_egg_badge_ca, R.string.season_ca_title, R.string.season_ca_description, EasterEggParticleEffect.MAPLE_FALL, EasterEggTone.HARVEST),
).associateBy { it.iso }
