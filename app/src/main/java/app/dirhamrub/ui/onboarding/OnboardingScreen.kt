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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.components.CurrencyChips
import app.dirhamrub.ui.currency.CurrencyTile
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.CardPalette

private const val STEPS = 3

/**
 * Знакомство при первом запуске: что умеет приложение → где тратите и в чём считать итог →
 * добавить карту (или пока считать по ЦБ). Показывается, пока нет карт и знакомство не пройдено.
 * [suggested] — валюта страны сотовой сети: выбирается сразу на втором шаге.
 */
@Composable
fun OnboardingScreen(
    spend: Currency,
    home: Currency,
    suggested: Currency?,
    onSpend: (Currency) -> Unit,
    onHome: (Currency) -> Unit,
    onAddCard: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = AppTheme.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> Intro()
                1 -> Currencies(spend, home, onSpend, onHome)
                else -> AddCard()
            }
        }
        Spacer(Modifier.height(12.dp))
        when (step) {
            STEPS - 1 -> {
                PrimaryButton(stringResource(R.string.onboarding_add_card), onAddCard)
                SecondaryButton(stringResource(R.string.onboarding_skip), onSkip)
            }
            else -> {
                PrimaryButton(stringResource(if (step == 0) R.string.onboarding_start else R.string.onboarding_next)) { step++ }
                Spacer(Modifier.height(48.dp))
            }
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
private fun Title(text: String) {
    Text(
        text,
        style = AppTheme.type.title.copy(fontSize = 28.sp, lineHeight = 34.sp),
        color = AppTheme.colors.ink,
        modifier = Modifier
            .padding(top = 12.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Lead(text: String) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
    )
}

@Composable
private fun Intro() {
    Title(stringResource(R.string.app_name))
    Lead(stringResource(R.string.onboarding_intro))
    Feature("🧾", stringResource(R.string.onboarding_feature_receipts))
    Feature("⚖️", stringResource(R.string.onboarding_feature_compare))
    Feature("📷", stringResource(R.string.onboarding_feature_camera))
}

@Composable
private fun Feature(emoji: String, text: String) {
    val colors = AppTheme.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(14.dp))
        Text(text, style = AppTheme.type.body.copy(fontSize = 16.sp, lineHeight = 21.sp), color = colors.ink)
    }
}

@Composable
private fun Currencies(spend: Currency, home: Currency, onSpend: (Currency) -> Unit, onHome: (Currency) -> Unit) {
    Title(stringResource(R.string.onboarding_spend_title))
    Lead(stringResource(R.string.onboarding_spend_lead))
    for (row in Currency.SPEND.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            for (c in row) CurrencyTile(c, selected = c == spend, wide = true, modifier = Modifier.weight(1f)) { onSpend(c) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Text(
        stringResource(R.string.currency_sheet_home),
        style = AppTheme.type.bodyStrong,
        color = AppTheme.colors.ink,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
    CurrencyChips(options = Currency.entries, selected = home, onSelect = onHome)
}

@Composable
private fun AddCard() {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Title(stringResource(R.string.onboarding_card_title))
    Lead(stringResource(R.string.onboarding_card_lead))
    // Пример карты — как она будет выглядеть на главном экране.
    val style = CardPalette.style(1)
    Column(
        Modifier
            .size(width = 220.dp, height = 132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(style.start, style.end)))
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_card_sample_name), style = type.label, color = CardPalette.onCard.copy(alpha = 0.9f))
        Spacer(Modifier.weight(1f))
        Text("3 749 ₽", style = type.number.copy(fontSize = 26.sp, fontWeight = FontWeight.Medium), color = CardPalette.onCard)
        Text(stringResource(R.string.onboarding_card_sample_rate), style = type.caption, color = CardPalette.onCard.copy(alpha = 0.85f))
    }
    Text(
        stringResource(R.string.onboarding_card_hint),
        style = type.body,
        color = colors.muted,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
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
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong, color = AppTheme.colors.accent)
    }
}
