package app.dirhamrub.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import app.dirhamrub.ui.theme.LocalReduceMotion

internal const val TRIP_DURATION = 1000
internal const val TRIP_BOUNDS_DURATION = 950
internal val TripEasing = CubicBezierEasing(0.20f, 0.90f, 0.30f, 1.0f)
internal const val TRIP_COUNTRY = "country_badge"
internal const val TRIP_HERO = "hero_total_sum"
internal const val TRIP_CARD = "primary_card_surface"
internal const val TRIP_TITLE = "primary_card_title"
internal const val TRIP_RATE = "exchange_rate_line"
internal const val TRIP_PRICE = "price_input_box"
internal const val TRIP_VALUE = "price_input_value"

internal enum class TripExit { HEADER, TITLE, CHIPS, SCREENSHOT, FOOTER, DECORATION }
internal enum class TripEnter { HEADER, PRIMARY_DETAILS, PAYMENT, ADD, KEYPAD, BADGE, PRICE_LABEL, GENERIC }

/** Clamp each delayed animation to the same one-second timeline, including late keyboard rows. */
internal data class TripWindow(val delay: Int, val duration: Int) { val end: Int get() = delay + duration }
internal fun tripWindow(delay: Int, duration: Int) = TripWindow(delay.coerceIn(0, TRIP_DURATION), duration.coerceIn(0, TRIP_DURATION - delay.coerceIn(0, TRIP_DURATION)))
internal fun tripEnterWindow(kind: TripEnter, index: Int = 0): TripWindow = when (kind) {
    TripEnter.HEADER -> tripWindow(320 + index.coerceIn(0, 1) * 70, 480)
    TripEnter.PRIMARY_DETAILS -> tripWindow(650, 300)
    TripEnter.PAYMENT -> tripWindow(420 + index.coerceIn(0, 2) * 90, 520)
    TripEnter.ADD -> tripWindow(560, 450)
    TripEnter.KEYPAD -> tripWindow(380 + (4 - index.coerceIn(0, 4)) * 55, 400)
    TripEnter.BADGE -> tripWindow(720, 220)
    TripEnter.PRICE_LABEL -> tripWindow(480, 260)
    TripEnter.GENERIC -> tripWindow(320, 350)
}

internal fun tripExitWindow(kind: TripExit, index: Int = 0): TripWindow {
    val (delay, requested) = when(kind) {
        TripExit.HEADER -> 0 to 380
        TripExit.TITLE -> 40 to 420
        TripExit.CHIPS -> (60 + index.coerceIn(0, 2) * 20) to 400
        TripExit.SCREENSHOT -> 90 to 420
        TripExit.FOOTER -> 0 to 420
        TripExit.DECORATION -> 0 to 180
    }
    return TripWindow(delay, minOf(requested, 450 - delay))
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal data class TripSharedTransition(val shared: SharedTransitionScope, val visibility: AnimatedVisibilityScope, val primaryCardId: String?)
internal val LocalTripSharedTransition = staticCompositionLocalOf<TripSharedTransition?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.tripSharedBounds(key: String): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    val text = key in setOf(TRIP_HERO, TRIP_TITLE, TRIP_RATE, TRIP_VALUE)
    return with(trip.shared) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key), animatedVisibilityScope = trip.visibility,
            boundsTransform = { _, _ -> tween(TRIP_BOUNDS_DURATION, easing = TripEasing) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(if (key == TRIP_CARD || key == TRIP_PRICE) ContentScale.FillBounds else ContentScale.Fit, Alignment.CenterStart),
            enter = when { text -> fadeIn(tween(300, delayMillis = 320)); key == TRIP_CARD -> fadeIn(tween(220, delayMillis = 720)); else -> fadeIn(tween(300)) },
            exit = when { text -> fadeOut(tween(300, delayMillis = 320)); key == TRIP_CARD -> fadeOut(tween(220, delayMillis = 720)); else -> fadeOut(tween(300)) },
            zIndexInOverlay = when(key) { TRIP_TITLE -> 5f; TRIP_CARD -> 4f; TRIP_HERO, TRIP_VALUE -> 6f; else -> 3f },
        )
    }
}

@Composable
internal fun Modifier.tripOutgoing(duration: Int = 200): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(trip.visibility) { animateEnterExit(enter = EnterTransition.None, exit = fadeOut(tween(duration))) }
}

@Composable
internal fun Modifier.tripExit(kind: TripExit, index: Int = 0): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    val window = tripExitWindow(kind, index)
    fun <T> spec() = tween<T>(window.duration, delayMillis = window.delay, easing = TripEasing)
    val exit = when(kind) {
        TripExit.HEADER -> slideOutVertically(spec()) { -it } + fadeOut(tween(320))
        TripExit.TITLE -> slideOut(spec()) { IntOffset(-it.width / 8, -it.height / 2) } + scaleOut(tween(420), targetScale = 0.92f) + fadeOut(tween(340))
        TripExit.CHIPS -> slideOutHorizontally(spec()) { it / 3 } + fadeOut(tween(320))
        TripExit.SCREENSHOT -> slideOutHorizontally(spec()) { -it / 3 } + scaleOut(tween(420), targetScale = 0.90f) + fadeOut(tween(320))
        TripExit.FOOTER -> slideOutVertically(spec()) { it } + fadeOut(tween(320))
        TripExit.DECORATION -> fadeOut(tween(180))
    }
    return with(trip.visibility) { animateEnterExit(enter = EnterTransition.None, exit = exit) }
}

@Composable
internal fun Modifier.tripMainEnter(kind: TripEnter = TripEnter.GENERIC, index: Int = 0): Modifier {
    val trip = LocalTripSharedTransition.current ?: return this
    if (LocalReduceMotion.current) return this
    val window = tripEnterWindow(kind, index)
    fun <T> spec() = tween<T>(window.duration, delayMillis = window.delay, easing = TripEasing)
    val enter = when(kind) {
        TripEnter.HEADER -> slideInVertically(spec()) { -it } + fadeIn(tween(360, delayMillis = window.delay))
        TripEnter.PAYMENT -> slideInHorizontally(spec()) { it / 3 } + fadeIn(tween(minOf(380, window.duration), delayMillis = window.delay))
        TripEnter.ADD -> slideInVertically(spec()) { it / 2 } + fadeIn(tween(350, delayMillis = window.delay))
        TripEnter.KEYPAD -> slideInVertically(spec()) { it / 2 } + scaleIn(spec(), initialScale = 0.93f) + fadeIn(spec())
        else -> fadeIn(tween(window.duration, delayMillis = window.delay))
    }
    return with(trip.visibility) { animateEnterExit(enter = enter, exit = ExitTransition.None) }
}
