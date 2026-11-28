package app.dirhamrub.ui.guide

import app.dirhamrub.ui.theme.surfaceOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import java.util.Locale

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import app.dirhamrub.ui.components.CollapsingBar
import app.dirhamrub.ui.components.rememberCollapsingTitle
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.domain.wallet.noun
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.dirhamrub.R
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Acceptance
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.sign
import app.dirhamrub.domain.wallet.CountryGuide
import app.dirhamrub.domain.wallet.GuideItem
import app.dirhamrub.ui.theme.AppTheme
import java.net.URLEncoder
import java.time.LocalDate

/** «ОАЭ» по коду страны (из справочника стран приложения). */
fun countryName(iso: String): String = Countries.byIso(iso)?.nameRu ?: iso

fun countryFlag(iso: String): String = Countries.byIso(iso)?.flag.orEmpty()

/** Цвет значка «принимают ли»: зелёный — да, жёлтый — не везде, красный — нет (синий — только для действий). */
@Composable
internal fun acceptanceColor(a: Acceptance?): Color = when (a) {
    Acceptance.YES -> AppTheme.colors.good
    Acceptance.PARTIAL -> AppTheme.colors.warn
    Acceptance.NO -> AppTheme.colors.danger
    null -> AppTheme.colors.muted
}

/** Значок «принимают ли»: ✓, ≈, ✕ или ? — иконкой, а не символом шрифта. */
@DrawableRes
internal fun acceptanceIcon(a: Acceptance?): Int = when (a) {
    Acceptance.YES -> R.drawable.ic_mark_yes
    Acceptance.PARTIAL -> R.drawable.ic_mark_partial
    Acceptance.NO -> R.drawable.ic_mark_no
    null -> R.drawable.ic_mark_unknown
}

/** Название платёжной системы в разделе «Карты»: «Visa и Mastercard из РФ». */
@Composable
private fun networkName(n: CardNetwork): String = stringResource(
    when (n) {
        CardNetwork.UNIONPAY -> R.string.network_unionpay_ru
        CardNetwork.UNIONPAY_FOREIGN -> R.string.network_unionpay_foreign
        CardNetwork.MIR -> R.string.network_mir
        CardNetwork.VISA_MC_FOREIGN -> R.string.network_visa_foreign
    },
)

@StringRes
private fun acceptanceLabel(acceptance: Acceptance?, exchangeOnly: Boolean): Int = when (acceptance) {
    Acceptance.YES -> R.string.guide_status_yes
    Acceptance.PARTIAL -> if (exchangeOnly) R.string.guide_status_exchange else R.string.guide_status_partial
    Acceptance.NO -> R.string.guide_status_no
    null -> R.string.guide_status_unknown
}

/**
 * Экран страны (ⓘ в шапке главного) — сразу «Чем платить». Сверху: страна мелко, валюта крупно, код и знак.
 * Ниже — ваши карты здесь, платёжные системы, наличные, ввоз и вывоз, купюры, банкоматы, советы; внизу — дата
 * и «Сообщить о неточности». Справочника по стране нет ([guide] == null) — шапка и «подсказать, что добавить».
 */
@Composable
fun CountryGuideScreen(
    guide: CountryGuide?,
    /** Флаг и название страны — как в шапке главного. */
    place: Pair<String, String>,
    /** Страна (ISO) — для письма о неточности, если справочника нет. */
    iso: String?,
    currency: Currency,
    cards: List<BankCard>,
    onDismiss: () -> Unit,
    backEnabled: Boolean,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    var seasonPlay by remember(iso) { mutableIntStateOf(0) }
    var activeEgg by remember(iso) { mutableStateOf<CountryEasterEgg?>(null) }
    val scroll = rememberScrollState()
    var headerHeight by remember { mutableIntStateOf(0) }
    val titleHeight = with(LocalDensity.current) { headerHeight.toDp() }.coerceAtLeast(56.dp)
    val collapsing = rememberCollapsingTitle(scroll, titleHeight)
    val titleCollapsed by remember(collapsing) { derivedStateOf { collapsing.progress >= 0.5f } }
    FullScreen(onDismissRequest = onDismiss, inline = true, backEnabled = backEnabled, backdrop = {
        Box(Modifier.matchParentSize().clipToBounds()) {
            FlagBackdrop(iso, Modifier.align(Alignment.TopEnd))
        }
    }) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        CollapsingBar(currency.noun, onBack = { close() }, collapsing, transparent = true)
        Column(
            Modifier
                .fillMaxWidth()
                .fadingScroll(scroll, topEdge = 0.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            SeasonalCountryHeader(
                place.second, currency, iso,
                onOpen = { activeEgg = it; seasonPlay++ }, onClose = { activeEgg = null },
                titleModifier = Modifier
                    .onSizeChanged { headerHeight = it.height }
                    .graphicsLayer { alpha = 1f - collapsing.progress }
                    .then(if (titleCollapsed) Modifier.semantics { hideFromAccessibility() } else Modifier),
            )
            if (guide == null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier

                        .guideCard()
                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                ) {
                    Text(stringResource(R.string.guide_empty), style = type.body, color = colors.ink)
                    ReportLink(stringResource(R.string.guide_suggest), iso, place.second, null)
                }
                return@Column
            }
            val local = Countries.byIso(guide.iso)?.currency
            // Наличные в чужой валюте здесь не платят — это и так понятно, строку не показываем.
            val shown = cards.filterNot { local != null && it.kind == CardKind.CASH && guide.verdict(it, local) == null }

            if (local != null && shown.isNotEmpty()) {
              Column {
                Section(stringResource(R.string.guide_your_cards))
                Group(shown) { card ->
                    val verdict = guide.verdict(card, local)
                    CardStatus(
                        acceptanceIcon(verdict), acceptanceColor(verdict), card.name,
                        stringResource(acceptanceLabel(verdict, exchangeOnly = card.kind == CardKind.CASH)),
                    )
                }
              }
            }
            Column {
            Section(stringResource(R.string.guide_networks))
            Group(CardNetwork.entries.filter { it in guide.networks }) { n ->
                    val note = guide.networks.getValue(n)
                    Line(acceptanceIcon(note.acceptance), acceptanceColor(note.acceptance), networkName(n), note.text)
            }
            }
            // Наличные — зелёной купюрой (без знаков валют), банкоматы — серым, советы — жёлтой лампочкой.
            Items(stringResource(R.string.guide_cash), R.drawable.ic_cash, colors.good, guide.cash)
            // Ввоз и вывоз — жёлтым: местную валюту нельзя вывезти, крупные суммы декларируют.
            Items(stringResource(R.string.guide_export), R.drawable.ic_alert, colors.warn, guide.export)
            // Ходовые купюры: номиналы и код валюты — чтобы узнавать деньги в руках.
            guide.notes?.let { n ->
                val code = local?.code.orEmpty()
                val denominations = DIGIT_SPACES.replace(n.title, "\u00A0")
                Items(stringResource(R.string.guide_notes), R.drawable.ic_cash, colors.muted, listOf(GuideItem("$denominations $code".trimEnd(), n.text)))
            }
            Items(stringResource(R.string.guide_atm), R.drawable.ic_atm, colors.muted, guide.atm)
            Items(stringResource(R.string.guide_tips), R.drawable.ic_tip, colors.warn, guide.tips)
            Spacer(Modifier.height(12.dp))
            val stale = guide.isStale(LocalDate.now())
            Text(
                stringResource(if (stale) R.string.guide_updated_stale else R.string.guide_updated, RuDates.long(guide.updated) + " " + guide.updated.year),
                style = type.caption,
                color = if (stale) colors.warn else colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ReportLink(stringResource(R.string.guide_report), guide.iso, countryName(guide.iso), guide.updated)
        }
        }
        activeEgg?.let { EasterEggParticleOverlay(it, seasonPlay, Modifier.matchParentSize()) }
      }
    }
}

/**
 * Шапка: «Армения» мелко серым, «Драм» очень крупно, «AMD · ֏» мелко серым — по одной левой линии.
 * Длинное название («Фунт стерлингов» на узком экране) уменьшается до 80 %, дальше — переносится.
 */
@Composable
internal fun Header(country: String, currency: Currency, modifier: Modifier = Modifier, countryLabel: @Composable (() -> Unit)? = null) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(modifier.padding(start = 4.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (countryLabel != null) countryLabel() else
                Text(country, style = type.body.copy(fontSize = 14.sp), color = colors.muted, maxLines = 1)
        }
        val title = currency.noun
        val base = type.title.copy(fontSize = HEADER_SP.sp, lineHeight = (HEADER_SP * 1.12f).sp, fontWeight = FontWeight.SemiBold)
        BoxWithConstraints(Modifier.padding(top = 2.dp)) {
            val measurer = rememberTextMeasurer()
            val limit = constraints.maxWidth
            val style = remember(title, limit, base) {
                val fits = measurer.measure(title, base, maxLines = 1, softWrap = false).size.width <= limit
                if (fits) base else base.copy(fontSize = (HEADER_SP * 0.8f).sp, lineHeight = (HEADER_SP * 0.8f * 1.12f).sp)
            }
            Text(title, style = style, color = colors.ink, maxLines = 2, modifier = Modifier.semantics { heading() })
        }
        Text(
            if (currency.sign == currency.code) currency.code else currency.code + " · " + currency.sign,
            style = type.body.copy(fontSize = 13.5.sp),
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val HEADER_SP = 40f
private val DIGIT_SPACES = Regex("(?<=\\d) +(?=\\d{3}(?!\\d))")

/** The source SVG is rendered at the actual size of the background. */
@Composable
private fun FlagBackdrop(iso: String?, modifier: Modifier) {
    val code = iso?.takeIf { it.length == 2 && it.all { symbol -> symbol in 'A'..'Z' || symbol in 'a'..'z' } }
        ?.lowercase(Locale.ROOT) ?: return
    val context = LocalContext.current
    val request = remember(context, code) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/flags/$code.svg")
            .decoderFactory(SvgDecoder.Factory(renderToBitmap = false))
            .build()
    }
    val opacityByTheme = if (AppTheme.colors.isDark) 0.32f else 0.24f
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .size(width = 230.dp, height = 170.dp)
            .graphicsLayer {
                rotationZ = -8f
                transformOrigin = TransformOrigin(1f, 0f)
                alpha = opacityByTheme
            }
            .blur(6.dp, BlurredEdgeTreatment.Unbounded)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val mask = Brush.radialGradient(
                    0f to Color.Black,
                    0.12f to Color.Black,
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                    radius = size.width * 0.95f,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            },
    )
}

/** «Сообщить о неточности» / «Подсказать, что добавить»: задача в GitHub с готовым текстом. */
@Composable
private fun ReportLink(text: String, iso: String?, country: String, updated: LocalDate?) {
    val context = LocalContext.current
    val issueTitle = stringResource(R.string.guide_report_title, country)
    val issueBody = stringResource(R.string.guide_report_body, country, iso.orEmpty(), updated?.toString() ?: "—")
    Box(
        Modifier
            .padding(top = 6.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) {
                val url = REPORT_URL +
                    "?title=" + URLEncoder.encode(issueTitle, "UTF-8") +
                    "&body=" + URLEncoder.encode(issueBody, "UTF-8")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = AppTheme.type.bodyStrong, color = AppTheme.colors.accent)
    }
}

private const val REPORT_URL = "https://github.com/KosHG28/monyexhange/issues/new"

@Composable
private fun Items(title: String, @DrawableRes icon: Int, tint: Color, items: List<GuideItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier) {
    Section(title)
    Group(items) { item ->
        Line(icon, tint, item.title, item.text)
    }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = AppTheme.type.section.copy(fontSize = 13.sp, lineHeight = 16.sp),
        color = AppTheme.colors.muted,
        // Заголовок отделён от карточки воздухом: 12 dp снизу.
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 12.dp),
    )
}

@Composable
private fun <T> Group(items: List<T>, content: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.guideCard()) {
        items.forEachIndexed { i, item ->
            if (i > 0) Divider()
            content(item)
        }
    }
}

@Composable
private fun Modifier.guideCard(): Modifier {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    return fillMaxWidth().clip(shape).background(colors.surface).surfaceOutline(shape)
}

/** Разделитель от края текста: колонка значков остаётся чистой. */
@Composable
private fun Divider() = HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(start = LINE_PAD + ICON_COLUMN))

private val LINE_PAD = 12.dp
private val ICON_COLUMN = 36.dp

@Composable
private fun CardStatus(@DrawableRes icon: Int, tint: Color, title: String, status: String) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = LINE_PAD, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ICON_COLUMN)) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(title, Modifier.weight(1f), style = type.body, color = colors.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(
            status,
            style = type.caption.copy(fontWeight = FontWeight.Medium),
            color = tint,
            modifier = Modifier.background(tint.copy(alpha = 0.12f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Line(@DrawableRes icon: Int, tint: Color, title: String, text: String) {
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = LINE_PAD, vertical = 11.dp),
    ) {
        Box(Modifier.width(ICON_COLUMN).padding(top = 1.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = AppTheme.colors.ink)
            if (text.isNotEmpty()) Text(text, style = type.caption, color = AppTheme.colors.muted)
        }
    }
}
