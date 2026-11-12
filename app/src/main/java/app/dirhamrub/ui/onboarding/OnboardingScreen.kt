package app.dirhamrub.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.UnionPayRates
import app.dirhamrub.ui.components.CascadeTiming
import app.dirhamrub.ui.components.cascade
import app.dirhamrub.ui.components.finishCascadeOnTouch
import app.dirhamrub.ui.components.rememberCascade
import app.dirhamrub.ui.currency.CountryScreen
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion

private const val STEPS = 3

/**
 * Знакомство при первом запуске (и из настроек), три шага: сколько на самом деле стоит покупка (кусок главного
 * экрана) → откуда точный курс (скриншоты банка и какие чеки нужны) → настройка под поездку (страна и первый
 * способ оплаты). Шапка на всех шагах: точки по центру, «Пропустить» справа, на 2–3 шаге «‹» слева.
 * Блоки шага появляются каскадом. [suggested] — валюта и страна сотовой сети: выбираются сразу, один раз
 * (null — знакомство открыли повторно из настроек: страну не трогаем). [onFinish] — карта из мастера
 * (null — «Пропустить»), [onOpenEditor] — та же карта в полном редакторе («заполнить из скриншота»).
 */
@Composable
fun OnboardingScreen(
    spend: Currency,
    home: Currency,
    /** Флаг и название страны трат — как в шапке главного. */
    place: Pair<String, String>,
    /** Страна сотовой сети (ISO): сверху списка стран. */
    country: String?,
    suggested: Pair<Currency, String>?,
    recent: List<Currency>,
    official: OfficialRates?,
    unionPay: UnionPayRates?,
    onSpend: (Currency, String?) -> Unit,
    newCard: () -> BankCard,
    onFinish: (BankCard?) -> Unit,
    onOpenEditor: (BankCard) -> Unit,
    /** С какого шага начать (снимки CI). */
    startStep: Int = 0,
) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    var step by rememberSaveable { mutableIntStateOf(startStep.coerceIn(0, STEPS - 1)) }
    var countryOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = step > 0 && !countryOpen) { step-- }
    // Страну сети подставляем один раз: поворот экрана или возврат в приложение не должны
    // затирать то, что человек уже выбрал сам на третьем шаге.
    var suggestionApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(suggested) {
        if (suggested != null && !suggestionApplied) {
            suggestionApplied = true
            onSpend(suggested.first, suggested.second)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Header(step, onBack = { step-- }, onSkip = { onFinish(null) })
            // Шаг сменяется на месте: новый шаг собирается каскадом — блоки выплывают снизу друг за другом.
            key(step) {
                val wave = rememberCascade(CascadeTiming.Step)
                val enter: (Int) -> Modifier = { Modifier.cascade(wave, it, CascadeTiming.Step) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .finishCascadeOnTouch(wave)
                        .padding(horizontal = 20.dp),
                ) {
                    when (step) {
                        0 -> ValueStep(official, enter) { step = 1 }
                        1 -> RulesStep(home, enter) { step = 2 }
                        else -> SetupStep(
                            spend = spend,
                            home = home,
                            place = place,
                            official = official,
                            unionPay = unionPay,
                            newCard = newCard,
                            onCountry = { countryOpen = true },
                            onFinish = onFinish,
                            onOpenEditor = onOpenEditor,
                            enter = enter,
                        )
                    }
                }
            }
        }
        // Страна — тот же экран, что из шапки главного, выезжает снизу поверх знакомства.
        AnimatedVisibility(
            visible = countryOpen,
            enter = if (reduceMotion) EnterTransition.None else slideInVertically(tween(260)) { it / 4 } + fadeIn(tween(200)),
            exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180)),
        ) {
            BackHandler { countryOpen = false }
            CountryScreen(
                spend = spend,
                home = home,
                onSpend = onSpend,
                onBack = { countryOpen = false },
                recent = recent,
                country = country,
                official = official,
            )
        }
    }
}

/** Шапка: «‹» слева (со второго шага), точки по центру, «Пропустить» справа — на всех шагах. */
@Composable
private fun Header(step: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp, start = 4.dp, end = 8.dp),
    ) {
        Dots(step)
        if (step > 0) {
            val back = stringResource(R.string.onboarding_back)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = back, onClick = onBack)
                    .semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) {
                // Шеврон «вниз», повёрнутый влево, — тонкий «‹».
                Icon(
                    painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(24.dp).rotate(90f),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.onboarding_skip), style = AppTheme.type.bodyStrong, color = colors.muted)
        }
    }
}

/** Точки шагов вверху: текущий — вытянутый и синий. */
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
                    .padding(horizontal = 3.dp)
                    .size(width = if (i == step) 20.dp else 7.dp, height = 7.dp)
                    .clip(CircleShape)
                    .background(if (i == step) colors.accent else colors.line),
            )
        }
    }
}

@Composable
internal fun Title(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.title.copy(fontSize = 27.sp, lineHeight = 33.sp),
        color = AppTheme.colors.ink,
        modifier = modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/**
 * Текст с полужирными местами: «Считаем **по вашим чекам**» → «по вашим чекам» жирнее и темнее остального,
 * чтобы при беглом просмотре глаз цеплялся за главное.
 */
@Composable
internal fun rich(text: String): AnnotatedString {
    val strong = AppTheme.colors.ink
    return remember(text, strong) {
        buildAnnotatedString {
            text.split("**").forEachIndexed { i, part ->
                if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(part) } else append(part)
            }
        }
    }
}

@Composable
internal fun Lead(text: String, modifier: Modifier = Modifier) {
    Text(
        rich(text),
        style = AppTheme.type.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
        color = AppTheme.colors.muted,
        modifier = modifier.padding(top = 8.dp, bottom = 16.dp),
    )
}

/** Главная кнопка шага; [soft] — спокойная (бледно-синяя): «Продолжить без чека». */
@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, soft: Boolean = false, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (soft) colors.accentSoft else colors.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong.copy(fontSize = 17.sp), color = if (soft) colors.accent else colors.onAccent)
    }
}

@Composable
internal fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = AppTheme.colors.muted,
        modifier = modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp),
    )
}
