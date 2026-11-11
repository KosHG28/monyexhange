package app.dirhamrub.ui.guide

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.ui.components.AppSheet
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.wallet.Acceptance
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.domain.wallet.Countries
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
        CardNetwork.UNIONPAY -> R.string.network_unionpay
        CardNetwork.MIR -> R.string.network_mir
        CardNetwork.VISA_MC_RU -> R.string.network_visa_ru
        CardNetwork.VISA_MC_FOREIGN -> R.string.network_visa_foreign
    },
)

/** Коротко — в подписи вашей карты: «Зарубежная Visa/MC · принимают почти везде». */
@Composable
private fun networkShort(n: CardNetwork): String = stringResource(
    when (n) {
        CardNetwork.UNIONPAY -> R.string.network_unionpay
        CardNetwork.MIR -> R.string.network_mir
        CardNetwork.VISA_MC_RU -> R.string.guide_net_visa_ru
        CardNetwork.VISA_MC_FOREIGN -> R.string.guide_net_visa_foreign
    },
)

/**
 * Шторка «Чем платить в стране»: ваши карты здесь, платёжные системы, наличные, банкоматы, советы; внизу — дата обновления и «Сообщить о неточности» (GitHub с готовым текстом).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryGuideSheet(guide: CountryGuide, cards: List<BankCard>, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val context = LocalContext.current
    val local = Countries.byIso(guide.iso)?.currency
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                countryFlag(guide.iso) + " " + stringResource(R.string.guide_title, countryName(guide.iso)),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            if (local != null && cards.isNotEmpty()) {
                Section(stringResource(R.string.guide_your_cards))
                Group {
                    cards.forEachIndexed { i, card ->
                        if (i > 0) Divider()
                        val verdict = guide.verdict(card, local)
                        val network = card.paymentNetwork
                        val note = network?.let { guide.networks[it]?.text }
                        // Наличные в чужой валюте здесь не платят — их меняют: значок обмена, а не «?».
                        val foreignCash = card.kind == CardKind.CASH && verdict == null
                        val sub = when {
                            foreignCash -> stringResource(R.string.guide_cash_other)
                            card.kind == CardKind.CASH && verdict == Acceptance.PARTIAL -> stringResource(R.string.guide_cash_exchange)
                            card.kind == CardKind.CASH -> stringResource(R.string.guide_cash_local)
                            network == null -> stringResource(R.string.guide_card_unknown)
                            note == null -> networkShort(network) + " · " + stringResource(R.string.guide_card_no_data)
                            else -> networkShort(network) + " · " + lower(note)
                        }
                        Line(
                            if (foreignCash) R.drawable.ic_exchange else acceptanceIcon(verdict),
                            acceptanceColor(verdict),
                            card.name,
                            sub,
                        )
                    }
                }
            }
            Section(stringResource(R.string.guide_networks))
            Group {
                CardNetwork.entries.filter { it in guide.networks }.forEachIndexed { i, n ->
                    if (i > 0) Divider()
                    val note = guide.networks.getValue(n)
                    Line(acceptanceIcon(note.acceptance), acceptanceColor(note.acceptance), networkName(n), note.text)
                }
            }
            // Наличные — зелёной купюрой (без знаков валют), банкоматы — серым, советы — жёлтой лампочкой.
            Items(stringResource(R.string.guide_cash), R.drawable.ic_cash, colors.good, guide.cash)
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
            Text(
                stringResource(R.string.guide_disclaimer),
                style = type.caption,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            val report = stringResource(R.string.guide_report)
            val issueTitle = stringResource(R.string.guide_report_title, countryName(guide.iso))
            val issueBody = stringResource(R.string.guide_report_body, countryName(guide.iso), guide.iso, guide.updated.toString())
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
                Text(report, style = type.bodyStrong, color = colors.accent)
            }
        }
    }
}

private const val REPORT_URL = "https://github.com/KosHG28/monyexhange/issues/new"

@Composable
private fun Items(title: String, @DrawableRes icon: Int, tint: Color, items: List<GuideItem>) {
    if (items.isEmpty()) return
    Section(title)
    Group {
        items.forEachIndexed { i, item ->
            if (i > 0) Divider()
            Line(icon, tint, item.title, item.text)
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
