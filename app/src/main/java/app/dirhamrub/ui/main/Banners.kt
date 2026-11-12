package app.dirhamrub.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.ui.theme.AppTheme
import java.util.Locale

/** «Казахстан» по коду страны; если система не знает код — сам код. */
private fun countryName(iso: String): String =
    runCatching { Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.forLanguageTag("ru")) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(iso, ignoreCase = true) }
        ?: iso

/** «🇰🇿 Казахстан: считать в KZT?» — [Да] [×]. Страна — из сотовой сети, без интернета. */
@Composable
internal fun CountryBanner(offer: CountryOffer, onCountry: (accept: Boolean) -> Unit) {
    val c = offer.currency
    OfferBanner(
        lead = c.flag,
        text = stringResource(R.string.country_offer, countryName(offer.iso), c.code),
        action = stringResource(R.string.country_offer_switch),
        actionA11y = stringResource(R.string.country_offer_yes, c.code),
        onAnswer = onCountry,
    )
}

/** «📷 Камера в шторке…: Добавить ×» — предложение добавить плитку быстрых настроек (Android 13+). */
@Composable
internal fun TileBanner(onAnswer: (accept: Boolean) -> Unit) {
    OfferBanner(
        lead = "📷",
        text = stringResource(R.string.tile_offer),
        action = stringResource(R.string.tile_offer_add),
        actionA11y = stringResource(R.string.tile_offer_add_a11y),
        onAnswer = onAnswer,
    )
}

/** Плашка-предложение над картами: значок, текст, кнопка действия и «×». */
@Composable
private fun OfferBanner(lead: String, text: String, action: String, actionA11y: String, onAnswer: (accept: Boolean) -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lead, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = type.body.copy(fontSize = 14.sp),
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button) { onAnswer(true) }
                .semantics { contentDescription = actionA11y }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = type.bodyStrong.copy(fontSize = 14.sp), color = colors.accent)
        }
        val close = stringResource(R.string.country_offer_close)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = close) { onAnswer(false) }
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}
