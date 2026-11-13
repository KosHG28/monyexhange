package app.dirhamrub.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.dirhamrub.ui.theme.LocalReduceMotion

internal const val TRIP_COUNTRY = "country_selector"
internal const val TRIP_CARD = "primary_payment_card"
internal const val TRIP_PRICE = "price_input_field"

@OptIn(ExperimentalSharedTransitionApi::class)
internal data class TripSharedTransition(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val primaryCardId: String?,
)

internal val LocalTripSharedTransition = staticCompositionLocalOf<TripSharedTransition?> { null }

/** One overlay owns all three flights. ScaleToBounds keeps the original text layout intact. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.tripSharedBounds(key: String): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(trip.shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = trip.visibility,
            boundsTransform = { _, _ -> tween(320, easing = FastOutSlowInEasing) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                if (key == TRIP_CARD) ContentScale.FillBounds else ContentScale.Fit, Alignment.CenterStart),
            enter = fadeIn(tween(if (key == TRIP_CARD) 120 else 160, delayMillis = if (key == TRIP_CARD) 200 else 0)),
            exit = if (key == TRIP_CARD) fadeOut(tween(90, delayMillis = 230)) else fadeOut(tween(130)),
            zIndexInOverlay = if (key == TRIP_CARD) 2f else 1f,
        )
    }
}

@Composable
internal fun Modifier.tripOutgoing(duration: Int = 130): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(trip.visibility) { animateEnterExit(enter = EnterTransition.None, exit = fadeOut(tween(duration))) }
}

@Composable
internal fun Modifier.tripMainEnter(keypad: Boolean = false, badge: Boolean = false): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    val enter = when {
        keypad -> slideInVertically(tween(280, delayMillis = 60, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(220, delayMillis = 60))
        badge -> fadeIn(tween(120, delayMillis = 200))
        else -> fadeIn(tween(200, delayMillis = 120))
    }
    return with(trip.visibility) { animateEnterExit(enter = enter, exit = ExitTransition.None) }
}

/** Large card lettering is gone before the shared bounds shrink to an icon. */
@Composable
internal fun tripCardTextAlpha(): () -> Float {
    val trip = LocalTripSharedTransition.current ?: return { 1f }
    if (LocalReduceMotion.current) return { 1f }
    val alpha = trip.visibility.transition.animateFloat(transitionSpec = { tween(90) }, label = "tripCardLettering") {
        if (it == EnterExitState.PostExit) 0f else 1f
    }
    return { alpha.value }
}
