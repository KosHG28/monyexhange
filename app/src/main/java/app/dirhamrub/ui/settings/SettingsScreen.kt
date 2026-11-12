package app.dirhamrub.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.Rounding
import app.dirhamrub.ui.components.AppSheet
import java.math.BigDecimal
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.core.net.toUri
import app.dirhamrub.R
import app.dirhamrub.ui.components.fadingScroll
import app.dirhamrub.diag.CrashLog
import app.dirhamrub.domain.MotionMode
import app.dirhamrub.domain.RatesUpdate
import app.dirhamrub.domain.RuDates
import app.dirhamrub.domain.ThemeMode
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.cbrPublishes
import app.dirhamrub.ui.currency.CurrencyPickerScreen
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
    val onCameraEmbedded: (Boolean) -> Unit,
    /** null — плитку в шторку добавить нельзя (Android до 13). */
    val onAddTile: (() -> Unit)?,
    val onTheme: (ThemeMode) -> Unit,
    val onMotion: (MotionMode) -> Unit,
    val onKeyHaptics: (Boolean) -> Unit,
    val onWifiOnly: (Boolean) -> Unit,
    val onRounding: (Rounding) -> Unit,
    val onOnboarding: () -> Unit,
    /** «Карты и наличные» — все способы оплаты (на главном — только те, что подходят к валюте трат). */
    val onCards: () -> Unit,
)

/**
 * Настройки (значок в шапке главного). Основные (карты и наличные); курсы — одна карточка: источники,
 * UnionPay в своей строке, частота, «только по Wi-Fi», ниже — «Обновить сейчас»; валюты (итог, округление,
 * подсказка о стране); камера; оформление (тема, анимации, вибрация); о приложении (журнал ошибок — отдельным экраном).
 */
@Composable
fun SettingsScreen(state: MainUiState, actions: SettingsActions) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    var homePicker by remember { mutableStateOf(false) }
    var roundingPicker by remember { mutableStateOf(false) }
    var crashOpen by remember { mutableStateOf(false) }
    if (crashOpen) {
        BackHandler { crashOpen = false }
        CrashLogScreen(onBack = { crashOpen = false })
        return
    }
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
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_section_main))
            Group {
                LinkRow(
                    stringResource(R.string.settings_cards_link),
                    stringResource(R.string.settings_cards_link_sub),
                    state.wallet.cards.size.takeIf { it > 0 }?.toString(),
                    onClick = actions.onCards,
                )
            }
            RatesSection(state, actions)
            CurrenciesSection(state, actions, onPickHome = { homePicker = true }, onPickRounding = { roundingPicker = true })
            CameraSection(state, actions)
            LookSection(state, actions)
            AboutSection(onOnboarding = actions.onOnboarding, onCrash = { crashOpen = true })
        }
    }
    if (roundingPicker) {
        RoundingSheet(state.settings.rounding, onPick = {
            actions.onRounding(it)
            roundingPicker = false
        }, onDismiss = { roundingPicker = false })
    }
    if (homePicker) {
        CurrencyPickerScreen(
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
            warn = official == null || state.ratesFailed,
            subtitle = official?.let {
                stringResource(R.string.settings_cbr_sub, RuDates.short(it.date), it.rubPerUnit.size, state.officialSource ?: "—")
            } ?: stringResource(R.string.settings_cbr_empty),
        )
        Divider()
        val week = state.officialWeekAgo
        SourceRow(
            badge = stringResource(R.string.settings_badge_week),
            title = stringResource(R.string.settings_week),
            status = "✓".takeIf { week != null },
            enabled = week != null,
            subtitle = stringResource(R.string.settings_week_sub, week?.let { RuDates.short(it.date) } ?: "—"),
        )
        Divider()
        val days = state.ratesHistory.keys.sortedDescending()
        SourceRow(
            badge = stringResource(R.string.settings_badge_receipts),
            title = stringResource(R.string.settings_receipts),
            status = "✓ ${days.size}".takeIf { days.isNotEmpty() },
            enabled = days.isNotEmpty(),
            subtitle = if (days.isEmpty()) {
                stringResource(R.string.settings_receipts_none)
            } else {
                stringResource(R.string.settings_receipts_sub, days.take(3).joinToString(", ") { RuDates.short(it) } + if (days.size > 3) "…" else "")
            },
        )
        Divider()
        // UnionPay включается прямо в своей строке — отдельного переключателя нет.
        val up = state.unionPay?.takeIf { state.settings.unionPay }
        SourceRow(
            badge = stringResource(R.string.settings_badge_unionpay),
            title = stringResource(R.string.settings_unionpay),
            status = null,
            subtitle = if (up != null) {
                stringResource(R.string.settings_unionpay_on, fetchedText(state.unionPayFetchedAt, today).removePrefix("✓ "))
            } else {
                stringResource(R.string.settings_unionpay_switch_sub)
            },
            trailing = { AppSwitch(state.settings.unionPay) },
            modifier = Modifier.toggleable(value = state.settings.unionPay, role = Role.Switch, onValueChange = actions.onUnionPay),
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
        Divider()
        SwitchRow(
            stringResource(R.string.settings_wifi_only),
            stringResource(R.string.settings_wifi_only_sub),
            state.settings.wifiOnly,
            actions.onWifiOnly,
        )
    }
    // «Обновить сейчас» — отдельная тональная кнопка под карточкой курсов.
    val colors = AppTheme.colors
    val label = stringResource(if (state.ratesLoading) R.string.settings_loading_button else R.string.settings_refresh)
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.accentSoft)
            .clickable(role = Role.Button, enabled = !state.ratesLoading, onClick = actions.onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        Text("↻  $label", style = AppTheme.type.bodyStrong, color = colors.accent)
    }
}

@Composable
private fun CurrenciesSection(state: MainUiState, actions: SettingsActions, onPickHome: () -> Unit, onPickRounding: () -> Unit) {
    SectionTitle(stringResource(R.string.settings_currencies))
    Group {
        val home = state.wallet.home
        LinkRow(stringResource(R.string.settings_home), stringResource(R.string.settings_home_sub), "${home.flag} ${home.code}", onPickHome)
        Divider()
        val roundings = Rounding.entries
        val labels = listOf(
            stringResource(R.string.rounding_none),
            stringResource(R.string.rounding_whole),
            stringResource(R.string.rounding_tens),
        )
        val current = roundings.indexOf(state.settings.rounding)
        // Узкий экран: три варианта в одну линию не помещаются — строка «До целых ›» и шторка с выбором.
        if (LocalConfiguration.current.screenWidthDp >= WIDE_DP) {
            ChoiceRow(stringResource(R.string.settings_rounding), labels, current) { actions.onRounding(roundings[it]) }
        } else {
            LinkRow(stringResource(R.string.settings_rounding), null, labels[current], onPickRounding)
        }
        Divider()
        SwitchRow(
            stringResource(R.string.settings_country_hint),
            stringResource(R.string.settings_country_hint_sub),
            state.settings.countryHint,
            actions.onCountryHint,
        )
    }
}

/** Ширина экрана, с которой три варианта округления помещаются в одну линию. */
private const val WIDE_DP = 380

/** Шторка выбора округления (узкий экран): три пункта-кружка с примером. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundingSheet(current: Rounding, onPick: (Rounding) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val sample = BigDecimal("3706.06")
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.settings_rounding),
                style = AppTheme.type.title.copy(fontSize = 19.sp),
                color = colors.ink,
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 12.dp)
                    .semantics { heading() },
            )
            Group(Modifier.selectableGroup()) {
                val labels = listOf(R.string.rounding_none, R.string.rounding_whole, R.string.rounding_tens)
                Rounding.entries.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(selected = r == current, role = Role.RadioButton) { onPick(r) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = r == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.muted),
                        )
                        Spacer(Modifier.width(12.dp))
                        RowText(stringResource(labels[i]), Numbers.total(sample, r) + " " + Currency.RUB.mark, Modifier.weight(1f))
                    }
                }
            }
        }
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
            LinkRow(stringResource(R.string.settings_tile), stringResource(R.string.settings_tile_sub), null, add)
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
        Divider()
        SwitchRow(
            stringResource(R.string.settings_key_haptics),
            stringResource(R.string.settings_key_haptics_sub),
            state.settings.keyHaptics,
            actions.onKeyHaptics,
        )
    }
}

private const val PRIVACY_URL = "https://github.com/KosHG28/monyexhange/blob/main/docs/privacy.md"

@Composable
private fun AboutSection(onOnboarding: () -> Unit, onCrash: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    SectionTitle(stringResource(R.string.settings_about))
    Group {
        val version = remember(context) {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
        LinkRow(stringResource(R.string.settings_version), null, version, null)
        Divider()
        LinkRow(stringResource(R.string.settings_onboarding), stringResource(R.string.settings_onboarding_sub), null, onOnboarding)
        Divider()
        // Политика — в репозитории; ссылку на неё требует и карточка RuStore.
        LinkRow(stringResource(R.string.settings_privacy), null, null, onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri())) }
        })
        Divider()
        val report = remember(context) { CrashLog.last(context) }
        val sub = report?.let {
            stringResource(R.string.settings_crash_last, it.lineSequence().firstOrNull().orEmpty().removePrefix("Время: ").take(16).replace('T', ' '))
        } ?: stringResource(R.string.settings_crash_none)
        LinkRow(stringResource(R.string.settings_crash), sub, null, onCrash)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_footer),
        style = AppTheme.type.caption,
        color = colors.muted,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

/**
 * Журнал ошибок — отдельный экран из настроек: последняя ошибка целиком, «Скопировать» (основная) и
 * «Очистить» (красная текстовая): действия не в списке настроек, случайно не нажать.
 */
@Composable
private fun CrashLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val type = AppTheme.type
    var report by remember(context) { mutableStateOf(CrashLog.last(context)) }
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
                    .clickable(role = Role.Button, onClickLabel = back, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_crash), style = type.title.copy(fontSize = 21.sp), color = colors.ink, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier
                .fillMaxWidth()
                .fadingScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.crash_hint), style = type.caption.copy(fontSize = 13.sp), color = colors.muted, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            val text = report
            if (text == null) {
                Group { LinkRow(stringResource(R.string.settings_crash_none), null, null, null) }
                return@Column
            }
            Text(
                text,
                style = type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 16.sp),
                color = colors.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.accentSoft)
                        .clickable(role = Role.Button) { copy(context, text) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.settings_crash_copy), style = type.bodyStrong, color = colors.accent) }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button) {
                            CrashLog.forget(context)
                            report = null
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.settings_crash_clear), style = type.bodyStrong, color = colors.danger) }
            }
        }
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
