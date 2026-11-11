package app.dirhamrub.ui.guide

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.dirhamrub.R
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

@Composable
private fun acceptanceColor(a: Acceptance?): Color = when (a) {
    Acceptance.YES -> AppTheme.colors.good
    Acceptance.PARTIAL -> AppTheme.colors.warn
    Acceptance.NO -> AppTheme.colors.accent
    null -> AppTheme.colors.muted
}

private fun acceptanceMark(a: Acceptance?): String = when (a) {
    Acceptance.YES -> "✓"
    Acceptance.PARTIAL -> "≈"
    Acceptance.NO -> "✗"
    null -> "?"
}

@Composable
private fun networkName(n: CardNetwork): String = stringResource(
    when (n) {
        CardNetwork.UNIONPAY -> R.string.network_unionpay
        CardNetwork.MIR -> R.string.network_mir
        CardNetwork.VISA_MC_RU -> R.string.network_visa_ru
        CardNetwork.VISA_MC_FOREIGN -> R.string.network_visa_foreign
    },
)

/**
 * Шторка «Чем платить в стране»: ваши карты здесь, платёжные системы, наличные, банкоматы, обменники,
 * советы; внизу — дата обновления и «Сообщить о неточности» (GitHub с готовым текстом).
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
                .verticalScroll(rememberScrollState())
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
                        val sub = when {
                            // Наличные в чужой валюте здесь не платят — их меняют.
                            card.kind == CardKind.CASH && verdict == null -> stringResource(R.string.guide_cash_other, card.currency.code)
                            card.kind == CardKind.CASH && verdict == Acceptance.PARTIAL -> stringResource(R.string.guide_cash_exchange, card.currency.code)
                            card.kind == CardKind.CASH -> ""
                            verdict == null && card.paymentNetwork == null -> stringResource(R.string.guide_card_unknown)
                            verdict == null -> stringResource(R.string.guide_card_no_data)
                            else -> card.paymentNetwork?.let { guide.networks[it]?.text }.orEmpty()
                        }
                        val label = card.name + (card.paymentNetwork?.let { " · " + networkName(it) } ?: "")
                        Line(acceptanceMark(verdict), acceptanceColor(verdict), label, sub)
                    }
                }
            }
            Section(stringResource(R.string.guide_networks))
            Group {
                CardNetwork.entries.filter { it in guide.networks }.forEachIndexed { i, n ->
                    if (i > 0) Divider()
                    val note = guide.networks.getValue(n)
                    Line(acceptanceMark(note.acceptance), acceptanceColor(note.acceptance), networkName(n), note.text)
                }
            }
            Items(stringResource(R.string.guide_cash), "$", guide.cash)
            Items(stringResource(R.string.guide_atm), "🏧", guide.atm)
            Items(stringResource(R.string.guide_exchange), "!", guide.exchange)
            Items(stringResource(R.string.guide_tips), "💡", guide.tips)
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
private fun Items(title: String, mark: String, items: List<GuideItem>) {
    if (items.isEmpty()) return
    Section(title)
    Group {
        items.forEachIndexed { i, item ->
            if (i > 0) Divider()
            Line(mark, AppTheme.colors.warn, item.title, item.text)
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
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

@Composable
private fun Divider() = HorizontalDivider(color = AppTheme.colors.line)

@Composable
private fun Line(mark: String, markColor: Color, title: String, text: String) {
    val type = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(mark, style = type.bodyStrong, color = markColor, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = AppTheme.colors.ink)
            if (text.isNotEmpty()) Text(text, style = type.caption, color = AppTheme.colors.muted)
        }
    }
}
