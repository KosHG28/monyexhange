package app.dirhamrub.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.CountrySeasons
import app.dirhamrub.domain.SeasonalEffect
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.theme.blossom
import app.dirhamrub.ui.theme.blossomSoft
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import java.time.LocalDate

@Composable
internal fun SeasonalCountryHeader(country: String, currency: Currency, iso: String?) {
    val event = CountrySeasons.current(iso, LocalDate.now())
    val copy = seasonalCopy[iso]
    if (event == null || copy == null) { Header(country, currency); return }
    var expanded by rememberSaveable(iso, event.symbol) { mutableStateOf(false) }
    var play by remember(iso) { mutableIntStateOf(0) }
    val reduce = LocalReduceMotion.current
    val progress = remember(iso) { Animatable(1f) }
    LaunchedEffect(play, reduce) {
        if (play == 0 || reduce) { progress.snapTo(1f); return@LaunchedEffect }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100))
    }
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box {
        Column {
            Header(country, currency) {
                Text(
                    if (iso == "AE") {
                        if (colors.isDark) "🌙" else "☀️"
                    } else event.symbol,
                    color = colors.ink,
                    fontSize = 22.sp,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.season_open, stringResource(copy.first))) {
                            expanded = !expanded
                            if (expanded) play++
                        }.wrapContentSize(),
                )
            }
            AnimatedVisibility(expanded, enter = if (reduce) androidx.compose.animation.EnterTransition.None else androidx.compose.animation.fadeIn(), exit = if (reduce) androidx.compose.animation.ExitTransition.None else androidx.compose.animation.fadeOut()) {
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(if (event.effect == SeasonalEffect.FLOWER) colors.blossomSoft else colors.accentSoft)
                    .padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(copy.first), style = type.title, color = colors.ink)
                    Text(stringResource(copy.second), style = type.body, color = colors.ink, modifier = Modifier.padding(top = 6.dp))
                    Text(stringResource(R.string.season_hide), style = type.bodyStrong, color = colors.accent,
                        modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = false }.wrapContentHeight())
                }
            }
        }
        if (!reduce && progress.value < 1f) {
            val tint = if (event.effect == SeasonalEffect.FLOWER) colors.blossom else colors.accent
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val p = progress.value
                repeat(7) { i ->
                    val x = size.width * (0.12f + i * 0.12f) + kotlin.math.sin(p * 6f + i) * 10.dp.toPx()
                    val y = size.height * (p * 0.8f + (i % 3) * 0.06f)
                    val c = tint.copy(alpha = (1f - p) * 0.8f)
                    val r = (3 + i % 3).dp.toPx()
                    when (event.effect) {
                        SeasonalEffect.WATER -> drawArc(c, 0f, 180f, false, Offset(x, y), Size(r * 5, r * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                        SeasonalEffect.FLOWER, SeasonalEffect.LEAF -> rotate(i * 43f + p * 90f, Offset(x, y)) { drawOval(c, Offset(x, y), Size(r * 1.5f, r * 3)) }
                        SeasonalEffect.CONFETTI -> rotate(i * 35f + p * 180f, Offset(x, y)) { drawRect(c, Offset(x, y), Size(r, r * 2)) }
                        else -> drawCircle(c, r, Offset(x, y))
                    }
                }
            }
        }
    }
}

private val seasonalCopy = mapOf(
    "RU" to (R.string.season_ru_title to R.string.season_ru_description),
    "BY" to (R.string.season_by_title to R.string.season_by_description),
    "KZ" to (R.string.season_kz_title to R.string.season_kz_description),
    "KG" to (R.string.season_kg_title to R.string.season_kg_description),
    "TJ" to (R.string.season_tj_title to R.string.season_tj_description),
    "UZ" to (R.string.season_uz_title to R.string.season_uz_description),
    "TM" to (R.string.season_tm_title to R.string.season_tm_description),
    "AZ" to (R.string.season_az_title to R.string.season_az_description),
    "AM" to (R.string.season_am_title to R.string.season_am_description),
    "MD" to (R.string.season_md_title to R.string.season_md_description),
    "GE" to (R.string.season_ge_title to R.string.season_ge_description),
    "TR" to (R.string.season_tr_title to R.string.season_tr_description),
    "AE" to (R.string.season_ae_title to R.string.season_ae_description),
    "EG" to (R.string.season_eg_title to R.string.season_eg_description),
    "OM" to (R.string.season_om_title to R.string.season_om_description),
    "QA" to (R.string.season_qa_title to R.string.season_qa_description),
    "TH" to (R.string.season_th_title to R.string.season_th_description),
    "VN" to (R.string.season_vn_title to R.string.season_vn_description),
    "ID" to (R.string.season_id_title to R.string.season_id_description),
    "MY" to (R.string.season_my_title to R.string.season_my_description),
    "IN" to (R.string.season_in_title to R.string.season_in_description),
    "LK" to (R.string.season_lk_title to R.string.season_lk_description),
    "MV" to (R.string.season_mv_title to R.string.season_mv_description),
    "SC" to (R.string.season_sc_title to R.string.season_sc_description),
    "CN" to (R.string.season_cn_title to R.string.season_cn_description),
    "JP" to (R.string.season_jp_title to R.string.season_jp_description),
    "KR" to (R.string.season_kr_title to R.string.season_kr_description),
    "FR" to (R.string.season_fr_title to R.string.season_fr_description),
    "IT" to (R.string.season_it_title to R.string.season_it_description),
    "ES" to (R.string.season_es_title to R.string.season_es_description),
    "GR" to (R.string.season_gr_title to R.string.season_gr_description),
    "CY" to (R.string.season_cy_title to R.string.season_cy_description),
    "ME" to (R.string.season_me_title to R.string.season_me_description),
    "RS" to (R.string.season_rs_title to R.string.season_rs_description),
    "NL" to (R.string.season_nl_title to R.string.season_nl_description),
    "CZ" to (R.string.season_cz_title to R.string.season_cz_description),
    "CA" to (R.string.season_ca_title to R.string.season_ca_description),
)
