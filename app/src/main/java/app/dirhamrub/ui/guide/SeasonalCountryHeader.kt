package app.dirhamrub.ui.guide

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.CountrySeasons
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.ui.theme.*
import java.time.LocalDate

@Composable
internal fun SeasonalCountryHeader(country: String, currency: Currency, iso: String?, onOpen: (CountryEasterEgg) -> Unit) {
    val event = CountrySeasons.current(iso, LocalDate.now())
    val egg = countryEasterEggs[iso]
    if (event == null || egg == null) { Header(country, currency); return }
    var expanded by rememberSaveable(iso) { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    val colors = AppTheme.colors
    val palette = colors.easterEggPalette(egg.tone)
    val haptics = rememberHaptics()
    fun toggle(open: Boolean) {
        if (expanded == open) return
        expanded = open
        haptics.play(Haptic.TICK)
        if (open) onOpen(egg)
    }
    Column {
        Header(country, currency, countryLabel = {
            val title = stringResource(egg.titleRes)
            val description = stringResource(R.string.season_badge_a11y, title)
            val state = stringResource(if (expanded) R.string.season_tip_open else R.string.season_tip_closed)
            val interactions = remember { MutableInteractionSource() }
            val pressed by interactions.collectIsPressedAsState()
            val background by animateColorAsState(
                if (pressed) palette.icon.copy(alpha = 0.22f) else if (expanded) palette.icon.copy(alpha = 0.14f) else colors.seasonBadgeBackground,
                if (reduce) snap() else tween(160), label = "seasonCapsuleBackground",
            )
            val rotation by animateFloatAsState(if (expanded) -12f else 0f,
                if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "seasonBadgeAngle")
            Row(Modifier.widthIn(max = 360.dp).heightIn(min = 48.dp).clip(CircleShape).background(background)
                .toggleable(value = expanded, interactionSource = interactions, indication = null, role = Role.Button, onValueChange = ::toggle)
                .semantics(mergeDescendants = true) { contentDescription = description; stateDescription = state }
                .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(country, style = AppTheme.type.body.copy(fontSize = 14.sp), color = palette.icon,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(7.dp))
                Icon(painterResource(egg.iconRes), contentDescription = null, tint = palette.icon,
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation })
            }
        })
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduce) EnterTransition.None else expandVertically(tween(240, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(200, delayMillis = 40)),
            exit = if (reduce) ExitTransition.None else shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
        ) {
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card)
                .padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(egg.titleRes), style = AppTheme.type.title, color = palette.icon)
                Text(stringResource(egg.descriptionRes), style = AppTheme.type.body, color = colors.ink, modifier = Modifier.padding(top = 6.dp))
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val background by animateColorAsState(if (pressed) palette.icon.copy(alpha = 0.18f) else colors.seasonBadgeBackground,
                    if (reduce) snap() else tween(160), label = "seasonHideBackground")
                Text(stringResource(R.string.season_hide), style = AppTheme.type.bodyStrong, color = palette.icon,
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clip(CircleShape).background(background)
                        .clickable(interactionSource = interactions, indication = null, role = Role.Button) { toggle(false) }
                        .padding(horizontal = 14.dp).wrapContentHeight())
            }
        }
    }
}
