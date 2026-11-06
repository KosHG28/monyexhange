package app.dirhamrub.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.ui.theme.AppTheme

private const val STEPS = 5

/**
 * Знакомство при первом запуске (и из настроек): зачем → как считаем → что учитываем → откуда и куда
 * считаем (валюта трат и итога) → чем будете платить (мастер первой карты или наличных).
 * Без привязки к конкретным картам: способы оплаты — карта в итоговой валюте, в другой валюте,
 * наличные или пока ничего. [suggested] — валюта страны сотовой сети: выбирается сразу.
 * [onFinish] — карта из мастера (или null — без неё / «пропустить»), [onOpenEditor] — та же карта
 * в полном редакторе («заполнить из скриншота»).
 */
@Composable
fun OnboardingScreen(
    spend: Currency,
    home: Currency,
    suggested: Currency?,
    official: OfficialRates?,
    unionPay: UnionPayRates?,
    onSpend: (Currency) -> Unit,
    onHome: (Currency) -> Unit,
    newCard: () -> BankCard,
    onFinish: (BankCard?) -> Unit,
    onOpenEditor: (BankCard) -> Unit,
    /** С какого шага начать (снимки CI). */
    startStep: Int = 0,
) {
    val colors = AppTheme.colors
    var step by rememberSaveable { mutableIntStateOf(startStep.coerceIn(0, STEPS - 1)) }
    BackHandler(enabled = step > 0) { step-- }
    LaunchedEffect(suggested) { if (suggested != null) onSpend(suggested) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Dots(step)
        if (step == STEPS - 1) {
            // Мастер сам рисует кнопки: «Готово» зависит от выбранного способа и полей.
            PayStep(spend, home, official, unionPay, newCard, onFinish, onOpenEditor, Modifier.weight(1f))
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> WhyPage()
                1 -> HowPage(home)
                2 -> WhatPage()
                else -> CurrenciesPage(spend, home, official, onSpend, onHome)
            }
        }
        Spacer(Modifier.height(12.dp))
        val label = when (step) {
            0 -> R.string.onboarding_why_next
            1 -> R.string.onboarding_how_next
            2 -> R.string.onboarding_what_next
            else -> R.string.onboarding_next
        }
        PrimaryButton(stringResource(label)) { step++ }
        if (step == 0) {
            SecondaryButton(stringResource(R.string.onboarding_skip_intro), muted = true) { onFinish(null) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Точки шагов вверху. */
@Composable
private fun Dots(step: Int) {
    val colors = AppTheme.colors
    val desc = stringResource(R.string.onboarding_step, step + 1, STEPS)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = desc },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i == step) colors.accent else colors.line),
            )
        }
    }
}

@Composable
internal fun Title(text: String) {
    Text(
        text,
        style = AppTheme.type.title.copy(fontSize = 27.sp, lineHeight = 33.sp),
        color = AppTheme.colors.ink,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun Lead(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
        color = AppTheme.colors.muted,
        modifier = modifier.padding(top = 8.dp, bottom = 14.dp),
    )
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong.copy(fontSize = 17.sp), color = colors.onAccent)
    }
}

@Composable
internal fun SecondaryButton(text: String, muted: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong, color = if (muted) AppTheme.colors.muted else AppTheme.colors.accent)
    }
}
