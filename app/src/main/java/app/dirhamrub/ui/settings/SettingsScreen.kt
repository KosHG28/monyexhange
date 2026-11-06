package app.dirhamrub.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.dirhamrub.R
import app.dirhamrub.diag.CrashLog
import app.dirhamrub.domain.MotionMode
import app.dirhamrub.domain.RatesUpdate
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.ThemeMode
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.cbrPublishes
import app.dirhamrub.ui.currency.CurrencyPickerSheet
import app.dirhamrub.ui.main.MainUiState
import app.dirhamrub.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Что можно сделать на экране настроек. */
class SettingsActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onUnionPay: (Boolean) -> Unit,
    val onRatesUpdate: (RatesUpdate) -> Unit,
    val onHome: (Currency) -> Unit,
    val onCountryHint: (Boolean) -> Unit,
    val onEditCard: (BankCard) -> Unit,
    val onAddCard: () -> Unit,
    val onReorder: (List<String>) -> Unit,
    val onCameraEmbedded: (Boolean) -> Unit,
    /** null — плитку в шторку добавить нельзя (Android до 13). */
    val onAddTile: (() -> Unit)?,
    val onTheme: (ThemeMode) -> Unit,
    val onMotion: (MotionMode) -> Unit,
    val onOnboarding: () -> Unit,
)

/**
 * Настройки (⚙ на главном экране): когда и откуда скачаны курсы, обновление, UnionPay, итоговая валюта,
 * подсказка о стране, порядок карт, камера, тема, анимации, знакомство и журнал ошибок.
 */
@Composable
fun SettingsScreen(state: MainUiState, actions: SettingsActions) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    var homePicker by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.settings_back)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = back, onClick = actions.onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.settings_title),
                style = type.title.copy(fontSize = 21.sp),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp),
        ) {
            RatesSection(state, actions)
            CurrenciesSection(state, actions) { homePicker = true }
            SectionTitle(stringResource(R.string.settings_cards))
            Group {
                CardOrderList(state.wallet.cards, actions.onReorder, actions.onEditCard)
                if (state.wallet.cards.isNotEmpty()) Divider()
                LinkRow(stringResource(R.string.settings_card_add), null, null, actions.onAddCard, titleColor = colors.accent)
            }
            CameraSection(state, actions)
            LookSection(state, actions)
            AboutSection(actions)
        }
    }
    if (homePicker) {
        CurrencyPickerSheet(
            title = stringResource(R.string.settings_home),
            selected = state.wallet.home,
            // Итог — только рубль и валюты с курсом ЦБ: иначе карты не с чем сравнить.
            options = Currency.entries.filter { cbrPublishes(it, state.official) },
            onPick = actions.onHome,
            onDismiss = { homePicker = false },
        )
    }
}

@Composable
private fun RatesSection(state: MainUiState, actions: SettingsActions) {
    val colors = AppTheme.colors
    val today = LocalDate.now()
    SectionTitle(stringResource(R.string.settings_rates))
    Group {
        val official = state.official
        val cbrStatus = when {
            state.ratesLoading -> stringResource(R.string.settings_loading)
            official == null -> stringResource(R.string.settings_no_rates)
            state.ratesFailed -> stringResource(R.string.settings_offline)
            else -> fetchedText(state.officialFetchedAt, today)
        }
        SourceRow(
            badge = stringResource(R.string.settings_badge_cbr),
            title = stringResource(R.string.settings_cbr),
            status = cbrStatus,
            ok = official != null && !state.ratesFailed && !state.ratesLoading,
            subtitle = official?.let {
                stringResource(R.string.settings_cbr_sub, RuDates.short(it.date), it.rubPerUnit.size, state.officialSource ?: "—")
            } ?: stringResource(R.string.settings_cbr_empty),
        )
        Divider()
        val week = state.officialWeekAgo
        SourceRow(
            badge = stringResource(R.string.settings_badge_week),
            title = stringResource(R.string.settings_week),
            status = if (week != null) "✓" else "—",
            ok = week != null,
            subtitle = stringResource(R.string.settings_week_sub, week?.let { RuDates.short(it.date) } ?: "—"),
        )
        Divider()
        val days = state.ratesHistory.keys.sortedDescending()
        SourceRow(
            badge = stringResource(R.string.settings_badge_receipts),
            title = stringResource(R.string.settings_receipts),
            status = if (days.isEmpty()) "—" else "✓ ${days.size}",
            ok = days.isNotEmpty(),
            subtitle = if (days.isEmpty()) {
                stringResource(R.string.settings_receipts_none)
            } else {
                stringResource(R.string.settings_receipts_sub, days.take(3).joinToString(", ") { RuDates.short(it) } + if (days.size > 3) "…" else "")
            },
        )
        if (state.settings.unionPay) {
            Divider()
            val up = state.unionPay
            SourceRow(
                badge = stringResource(R.string.settings_badge_unionpay),
                title = stringResource(R.string.settings_unionpay),
                status = if (up == null) "—" else fetchedText(state.unionPayFetchedAt, today),
                ok = up != null,
                subtitle = up?.let {
                    stringResource(
                        R.string.settings_unionpay_sub,
                        RuDates.short(it.date),
                        it.rates.values.flatMap { row -> row.keys }.toSet().size,
                        it.rates.size,
                    )
                } ?: stringResource(R.string.settings_unionpay_empty),
            )
        }
        val label = stringResource(if (state.ratesLoading) R.string.settings_loading_button else R.string.settings_refresh)
        Box(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, colors.accent, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, enabled = !state.ratesLoading, onClick = actions.onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Text("↻  $label", style = AppTheme.type.bodyStrong, color = colors.accent)
        }
    }
    Spacer(Modifier.height(10.dp))
    Group {
        SwitchRow(
            stringResource(R.string.settings_unionpay_switch),
            stringResource(R.string.settings_unionpay_switch_sub),
            state.settings.unionPay,
            actions.onUnionPay,
        )
        Divider()
        val updates = RatesUpdate.entries
        ChoiceRow(
            stringResource(R.string.settings_update),
            listOf(
                stringResource(R.string.settings_update_launch),
                stringResource(R.string.settings_update_6h),
                stringResource(R.string.settings_update_manual),
            ),
            updates.indexOf(state.settings.ratesUpdate),
        ) { actions.onRatesUpdate(updates[it]) }
    }
}

@Composable
private fun CurrenciesSection(state: MainUiState, actions: SettingsActions, onPickHome: () -> Unit) {
    SectionTitle(stringResource(R.string.settings_currencies))
    Group {
        val home = state.wallet.home
        LinkRow(stringResource(R.string.settings_home), stringResource(R.string.settings_home_sub), "${home.flag} ${home.code} ›", onPickHome)
        Divider()
        SwitchRow(
            stringResource(R.string.settings_country_hint),
            stringResource(R.string.settings_country_hint_sub),
            state.settings.countryHint,
            actions.onCountryHint,
        )
    }
}

@Composable
private fun CameraSection(state: MainUiState, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.settings_camera))
    Group {
        ChoiceRow(
            stringResource(R.string.settings_camera_mode),
            listOf(stringResource(R.string.settings_camera_embed), stringResource(R.string.settings_camera_bubbles)),
            if (state.cameraEmbedded) 0 else 1,
        ) { actions.onCameraEmbedded(it == 0) }
        actions.onAddTile?.let { add ->
            Divider()
            LinkRow(stringResource(R.string.settings_tile), stringResource(R.string.settings_tile_sub), stringResource(R.string.settings_tile_add), add)
        }
    }
}

@Composable
private fun LookSection(state: MainUiState, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.settings_look))
    Group {
        val themes = ThemeMode.entries
        ChoiceRow(
            stringResource(R.string.settings_theme),
            listOf(
                stringResource(R.string.settings_as_system),
                stringResource(R.string.settings_theme_dark),
                stringResource(R.string.settings_theme_light),
            ),
            themes.indexOf(state.settings.theme),
        ) { actions.onTheme(themes[it]) }
        Divider()
        val motions = MotionMode.entries
        ChoiceRow(
            stringResource(R.string.settings_motion),
            listOf(stringResource(R.string.settings_as_system), stringResource(R.string.settings_motion_off)),
            motions.indexOf(state.settings.motion),
        ) { actions.onMotion(motions[it]) }
    }
}

@Composable
private fun AboutSection(actions: SettingsActions) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    SectionTitle(stringResource(R.string.settings_about))
    Group {
        val version = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        LinkRow(stringResource(R.string.settings_version), null, version, null)
        Divider()
        LinkRow(stringResource(R.string.settings_onboarding), null, stringResource(R.string.settings_onboarding_show), actions.onOnboarding)
        Divider()
        var crash by remember(context) { mutableStateOf(CrashLog.last(context)) }
        val report = crash
        if (report == null) {
            LinkRow(stringResource(R.string.settings_crash), stringResource(R.string.settings_crash_none), null, null)
        } else {
            val first = report.lineSequence().firstOrNull().orEmpty().removePrefix("Время: ").take(16).replace('T', ' ')
            LinkRow(stringResource(R.string.settings_crash), stringResource(R.string.settings_crash_last, first), null, null)
            Row(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
                TextAction(stringResource(R.string.settings_crash_copy)) { copy(context, report) }
                TextAction(stringResource(R.string.settings_crash_clear)) {
                    CrashLog.forget(context)
                    crash = null
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_footer),
        style = AppTheme.type.caption,
        color = colors.muted,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium), color = AppTheme.colors.accent)
    }
}

/** «✓ сегодня 19:41», «✓ вчера 08:15», «✓ 3 ноя 12:00»; 0 — «—». */
@Composable
private fun fetchedText(at: Long, today: LocalDate): String {
    if (at <= 0L) return "—"
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val time = "%02d:%02d".format(t.hour, t.minute)
    return when (val day = t.toLocalDate()) {
        today -> stringResource(R.string.settings_fetched_today, time)
        today.minusDays(1) -> stringResource(R.string.settings_fetched_yesterday, time)
        else -> stringResource(R.string.settings_fetched_on, RuDates.short(day), time)
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("crash", text))
}
