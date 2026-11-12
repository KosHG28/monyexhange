package app.dirhamrub.ui.guide

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import android.os.Build
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.Canvas
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.finishCascadeOnTouch
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.FullScreen
import app.dirhamrub.ui.components.ScreenBar
import app.dirhamrub.domain.wallet.noun
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** «Карты российских банков не работают» → «карты российских банков не работают»: после «·» в подписи. */
private fun lower(text: String): String = text.replaceFirstChar { it.lowercase() }

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

/** Коротко — в подписи вашей карты: «Зарубежная Visa/MC · принимают почти везде». */
@Composable
private fun networkShort(n: CardNetwork): String = stringResource(
    when (n) {
        CardNetwork.UNIONPAY -> R.string.guide_net_unionpay_ru
        CardNetwork.UNIONPAY_FOREIGN -> R.string.guide_net_unionpay_foreign
        CardNetwork.MIR -> R.string.network_mir
        CardNetwork.VISA_MC_FOREIGN -> R.string.guide_net_visa_foreign
    },
)

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
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    // Флаг страны — фоном в правом верхнем углу экрана (и под строкой состояния — без резкого среза сверху);
    // прокручивается только содержимое.
    FullScreen(onDismissRequest = onDismiss, backdrop = { FlagBackdrop(place.first, Modifier.align(Alignment.TopEnd)) }) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        ScreenBar(onBack = { close() })
        // Шапка — сразу, блоки ниже выплывают снизу по очереди; касание — сразу всё на месте.
        val cascade = rememberCascade()
        Column(
            Modifier
                .fillMaxWidth()
                .finishCascadeOnTouch(cascade)
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Header(place.second, currency)
            if (guide == null) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .cascade(cascade, 0)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
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
            var step = 0
            if (local != null && shown.isNotEmpty()) {
              Column(Modifier.cascade(cascade, step++)) {
                Section(stringResource(R.string.guide_your_cards))
                Group {
                    shown.forEachIndexed { i, card ->
                        if (i > 0) Divider()
                        val verdict = guide.verdict(card, local)
                        val network = card.paymentNetwork
                        val note = network?.let { guide.networks[it]?.text }
                        val sub = when {
                            card.kind == CardKind.CASH && verdict == Acceptance.PARTIAL -> stringResource(R.string.guide_cash_exchange)
                            card.kind == CardKind.CASH -> stringResource(R.string.guide_cash_local)
                            network == null -> stringResource(R.string.guide_card_unknown)
                            note == null -> networkShort(network) + " · " + stringResource(R.string.guide_card_no_data)
                            else -> networkShort(network) + " · " + lower(note)
                        }
                        Line(acceptanceIcon(verdict), acceptanceColor(verdict), card.name, sub)
                    }
                }
              }
            }
            Column(Modifier.cascade(cascade, step++)) {
            Section(stringResource(R.string.guide_networks))
            Group {
                CardNetwork.entries.filter { it in guide.networks }.forEachIndexed { i, n ->
                    if (i > 0) Divider()
                    val note = guide.networks.getValue(n)
                    Line(acceptanceIcon(note.acceptance), acceptanceColor(note.acceptance), networkName(n), note.text)
                }
            }
            }
            // Наличные — зелёной купюрой (без знаков валют), банкоматы — серым, советы — жёлтой лампочкой.
            Items(stringResource(R.string.guide_cash), R.drawable.ic_cash, colors.good, guide.cash, Modifier.cascade(cascade, step++))
            // Ввоз и вывоз — жёлтым: местную валюту нельзя вывезти, крупные суммы декларируют.
            Items(stringResource(R.string.guide_export), R.drawable.ic_alert, colors.warn, guide.export, Modifier.cascade(cascade, step++))
            // Ходовые купюры: номиналы и код валюты — чтобы узнавать деньги в руках.
            guide.notes?.let { n ->
                val code = local?.code.orEmpty()
                Items(stringResource(R.string.guide_notes), R.drawable.ic_cash, colors.muted, listOf(GuideItem("${n.title} $code", n.text)), Modifier.cascade(cascade, step++))
            }
            Items(stringResource(R.string.guide_atm), R.drawable.ic_atm, colors.muted, guide.atm, Modifier.cascade(cascade, step++))
            Items(stringResource(R.string.guide_tips), R.drawable.ic_tip, colors.warn, guide.tips, Modifier.cascade(cascade, step++))
            Spacer(Modifier.height(12.dp))
            val stale = guide.isStale(LocalDate.now())
            Text(
                stringResource(if (stale) R.string.guide_updated_stale else R.string.guide_updated, RuDates.long(guide.updated) + " " + guide.updated.year),
                style = type.caption,
                color = if (stale) colors.warn else colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                stringResource(R.string.guide_disclaimer),
                style = type.caption,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            ReportLink(stringResource(R.string.guide_report), guide.iso, countryName(guide.iso), guide.updated)
        }
        }
      }
    }
}

/**
 * Шапка: «Армения» мелко серым, «Драм» очень крупно, «AMD · ֏» мелко серым — по одной левой линии.
 * Длинное название («Фунт стерлингов» на узком экране) уменьшается до 80 %, дальше — переносится.
 */
@Composable
private fun Header(country: String, currency: Currency) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
        Text(country, style = type.body.copy(fontSize = 14.sp), color = colors.muted, maxLines = 1)
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

/**
 * Флаг страны фоном: мягкое цветное пятно в правом верхнем углу экрана, растворяется радиальной маской от угла
 * (45 % → 0 к середине — текст слева всегда на чистом фоне). Эмодзи — маленькая картинка (~136 px), растянутая
 * текстом на полэкрана она рассыпается на пиксели (и тяжела для отрисовки). Поэтому флаг рисуется маленьким,
 * обрезается по самому флагу и растягивается со сглаживанием и лёгким размытием — читается как флаг, но без пикселей.
 * В светлой теме — наложение «умножение»: белые поля флага (Япония, Кипр) пропадают на светлом фоне.
 */
@Composable
private fun FlagBackdrop(flag: String, modifier: Modifier = Modifier) {
    val dark = AppTheme.colors.isDark
    val image = remember(flag) { flagBitmap(flag) } ?: return
    val blur = with(LocalDensity.current) { FLAG_BLUR.toPx() }
    Canvas(
        modifier
            .size(FLAG_SIZE)
            .clearAndSetSemantics { }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                if (!dark) blendMode = BlendMode.Multiply
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            },
    ) {
        // Флаг «накрывает» угол: по высоте чуть больше квадрата, правым краем — за край экрана.
        val h = size.height * 1.05f
        val w = h * image.width / image.height
        drawImage(
            image,
            dstOffset = IntOffset((size.width - w * 0.82f).roundToInt(), (-size.height * 0.08f).roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.High,
        )
        drawRect(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                0.3f to Color.Black.copy(alpha = 0.38f),
                0.55f to Color.Black.copy(alpha = 0.16f),
                0.8f to Color.Transparent,
                center = Offset(size.width, 0f),
                radius = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Эмодзи флага, нарисованный маленьким и обрезанный по непрозрачной части (без полей шрифта); null — пусто. */
private fun flagBitmap(flag: String): ImageBitmap? {
    val px = FLAG_BITMAP_PX
    val full = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = px * 0.75f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val metrics = paint.fontMetrics
    android.graphics.Canvas(full).drawText(flag, px / 2f, px / 2f - (metrics.ascent + metrics.descent) / 2f, paint)
    var left = px
    var top = px
    var right = -1
    var bottom = -1
    val row = IntArray(px)
    for (y in 0 until px) {
        full.getPixels(row, 0, px, 0, y, px, 1)
        for (x in 0 until px) {
            if ((row[x] ushr 24) > 40) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < left || bottom < top) return null
    return android.graphics.Bitmap.createBitmap(full, left, top, right - left + 1, bottom - top + 1).asImageBitmap()
}

private const val FLAG_BITMAP_PX = 256
private val FLAG_BLUR = 3.dp

private val FLAG_SIZE = 280.dp

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
    Group {
        items.forEachIndexed { i, item ->
            if (i > 0) Divider()
            Line(icon, tint, item.title, item.text)
        }
    }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = AppTheme.colors.muted,
        // Заголовок отделён от карточки воздухом: 12 dp снизу.
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 12.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.surface),
    ) { content() }
}

/** Разделитель от края текста: колонка значков остаётся чистой. */
@Composable
private fun Divider() = HorizontalDivider(color = AppTheme.colors.line, modifier = Modifier.padding(start = LINE_PAD + ICON_COLUMN))

private val LINE_PAD = 12.dp
private val ICON_COLUMN = 36.dp

@Composable
private fun Line(@DrawableRes icon: Int, tint: Color, title: String, text: String) {
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = LINE_PAD, vertical = 11.dp),
    ) {
        // Значок — к первой строке заголовка; смысл несут заголовок и подпись, TalkBack их и читает.
        Box(Modifier.width(ICON_COLUMN).padding(top = 1.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = AppTheme.colors.ink)
            if (text.isNotEmpty()) Text(text, style = type.caption, color = AppTheme.colors.muted)
        }
    }
}
