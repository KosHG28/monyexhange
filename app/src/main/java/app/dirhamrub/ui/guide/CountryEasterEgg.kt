package app.dirhamrub.ui.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.dirhamrub.R
import app.dirhamrub.ui.theme.EasterEggTone

/** Геометрия/физика частиц; цвета выбирает тема, даты остаются в CountrySeasons. */
internal enum class ParticlePreset { OLIVE, STARS, PETALS, SNOW, LEAVES, WATER, FRUIT, CONFETTI }

internal data class CountryEasterEgg(
    val iso: String,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val particles: ParticlePreset,
    val tone: EasterEggTone,
)

internal val countryEasterEggs = listOf(
    CountryEasterEgg("RU", R.drawable.ic_season_snow, R.string.season_ru_title, R.string.season_ru_description, ParticlePreset.SNOW, EasterEggTone.WINTER),
    CountryEasterEgg("BY", R.drawable.ic_season_leaf, R.string.season_by_title, R.string.season_by_description, ParticlePreset.LEAVES, EasterEggTone.HARVEST),
    CountryEasterEgg("KZ", R.drawable.ic_season_tulip, R.string.season_kz_title, R.string.season_kz_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("KG", R.drawable.ic_season_sun, R.string.season_kg_title, R.string.season_kg_description, ParticlePreset.STARS, EasterEggTone.DESERT),
    CountryEasterEgg("TJ", R.drawable.ic_season_blossom, R.string.season_tj_title, R.string.season_tj_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("UZ", R.drawable.ic_season_leaf, R.string.season_uz_title, R.string.season_uz_description, ParticlePreset.LEAVES, EasterEggTone.FOREST),
    CountryEasterEgg("TM", R.drawable.ic_season_blossom, R.string.season_tm_title, R.string.season_tm_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("AZ", R.drawable.ic_season_leaf, R.string.season_az_title, R.string.season_az_description, ParticlePreset.LEAVES, EasterEggTone.FOREST),
    CountryEasterEgg("AM", R.drawable.ic_season_fruit, R.string.season_am_title, R.string.season_am_description, ParticlePreset.FRUIT, EasterEggTone.HARVEST),
    CountryEasterEgg("MD", R.drawable.ic_season_ribbon, R.string.season_md_title, R.string.season_md_description, ParticlePreset.CONFETTI, EasterEggTone.FESTIVAL),
    CountryEasterEgg("GE", R.drawable.ic_season_grapes, R.string.season_ge_title, R.string.season_ge_description, ParticlePreset.FRUIT, EasterEggTone.HARVEST),
    CountryEasterEgg("TR", R.drawable.ic_season_tulip, R.string.season_tr_title, R.string.season_tr_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("AE", R.drawable.ic_season_moon, R.string.season_ae_title, R.string.season_ae_description, ParticlePreset.STARS, EasterEggTone.DESERT),
    CountryEasterEgg("EG", R.drawable.ic_season_blossom, R.string.season_eg_title, R.string.season_eg_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("OM", R.drawable.ic_season_leaf, R.string.season_om_title, R.string.season_om_description, ParticlePreset.WATER, EasterEggTone.SEA),
    CountryEasterEgg("QA", R.drawable.ic_season_boat, R.string.season_qa_title, R.string.season_qa_description, ParticlePreset.WATER, EasterEggTone.SEA),
    CountryEasterEgg("TH", R.drawable.ic_season_lantern, R.string.season_th_title, R.string.season_th_description, ParticlePreset.STARS, EasterEggTone.DESERT),
    CountryEasterEgg("VN", R.drawable.ic_season_blossom, R.string.season_vn_title, R.string.season_vn_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("ID", R.drawable.ic_season_leaf, R.string.season_id_title, R.string.season_id_description, ParticlePreset.LEAVES, EasterEggTone.FOREST),
    CountryEasterEgg("MY", R.drawable.ic_season_lantern, R.string.season_my_title, R.string.season_my_description, ParticlePreset.STARS, EasterEggTone.FESTIVAL),
    CountryEasterEgg("IN", R.drawable.ic_season_star, R.string.season_in_title, R.string.season_in_description, ParticlePreset.CONFETTI, EasterEggTone.FESTIVAL),
    CountryEasterEgg("LK", R.drawable.ic_season_lantern, R.string.season_lk_title, R.string.season_lk_description, ParticlePreset.STARS, EasterEggTone.DESERT),
    CountryEasterEgg("MV", R.drawable.ic_season_wave, R.string.season_mv_title, R.string.season_mv_description, ParticlePreset.WATER, EasterEggTone.SEA),
    CountryEasterEgg("SC", R.drawable.ic_season_turtle, R.string.season_sc_title, R.string.season_sc_description, ParticlePreset.WATER, EasterEggTone.SEA),
    CountryEasterEgg("CN", R.drawable.ic_season_moon, R.string.season_cn_title, R.string.season_cn_description, ParticlePreset.STARS, EasterEggTone.DESERT),
    CountryEasterEgg("JP", R.drawable.ic_season_blossom, R.string.season_jp_title, R.string.season_jp_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("KR", R.drawable.ic_season_blossom, R.string.season_kr_title, R.string.season_kr_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("FR", R.drawable.ic_season_blossom, R.string.season_fr_title, R.string.season_fr_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("IT", R.drawable.ic_season_mask, R.string.season_it_title, R.string.season_it_description, ParticlePreset.CONFETTI, EasterEggTone.FESTIVAL),
    CountryEasterEgg("ES", R.drawable.ic_season_blossom, R.string.season_es_title, R.string.season_es_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("GR", R.drawable.ic_olive_branch, R.string.season_gr_title, R.string.season_gr_description, ParticlePreset.OLIVE, EasterEggTone.OLIVE),
    CountryEasterEgg("CY", R.drawable.ic_season_blossom, R.string.season_cy_title, R.string.season_cy_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("ME", R.drawable.ic_season_blossom, R.string.season_me_title, R.string.season_me_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("RS", R.drawable.ic_season_grapes, R.string.season_rs_title, R.string.season_rs_description, ParticlePreset.FRUIT, EasterEggTone.HARVEST),
    CountryEasterEgg("NL", R.drawable.ic_season_tulip, R.string.season_nl_title, R.string.season_nl_description, ParticlePreset.PETALS, EasterEggTone.BLOSSOM),
    CountryEasterEgg("CZ", R.drawable.ic_season_snow, R.string.season_cz_title, R.string.season_cz_description, ParticlePreset.SNOW, EasterEggTone.WINTER),
    CountryEasterEgg("CA", R.drawable.ic_season_maple, R.string.season_ca_title, R.string.season_ca_description, ParticlePreset.LEAVES, EasterEggTone.HARVEST),
).associateBy { it.iso }
