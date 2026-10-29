package app.dirhamrub.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import app.dirhamrub.R
import app.dirhamrub.domain.Card

@get:StringRes
val Card.titleRes: Int
    get() = when (this) {
        Card.KZT -> R.string.card_kzt
        Card.CNY -> R.string.card_cny
    }

@get:StringRes
val Card.shortRes: Int
    get() = when (this) {
        Card.KZT -> R.string.card_kzt_short
        Card.CNY -> R.string.card_cny_short
    }

@Composable
@ReadOnlyComposable
fun Card.title(): String = stringResource(titleRes)

@Composable
@ReadOnlyComposable
fun Card.short(): String = stringResource(shortRes)
