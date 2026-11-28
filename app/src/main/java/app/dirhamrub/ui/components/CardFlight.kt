package app.dirhamrub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

internal enum class CardFlightSource { CALCULATOR, WALLET }

internal data class CardFlightKey(val cardId: String, val source: CardFlightSource)

/** The same payment can be present on both screens, with a different origin on each. */
internal class CardFlight {
    private val origins = mutableStateMapOf<CardFlightKey, Rect>()
    var flying by mutableStateOf<CardFlightKey?>(null)

    fun originOf(key: CardFlightKey): Rect? = origins[key]

    internal fun place(key: CardFlightKey, bounds: Rect?) {
        if (bounds == null) origins.remove(key) else origins[key] = bounds
    }
}

internal val LocalCardFlight = staticCompositionLocalOf<CardFlight?> { null }

internal fun Modifier.flightOrigin(cardId: String, source: CardFlightSource = CardFlightSource.CALCULATOR): Modifier = composed {
    val flight = LocalCardFlight.current ?: return@composed this
    val key = CardFlightKey(cardId, source)
    DisposableEffect(key, flight) { onDispose { flight.place(key, null) } }
    this
        .onGloballyPositioned { coordinates ->
            flight.place(key, coordinates.cardFlightBounds().takeUnless { it.isEmpty })
        }
        .graphicsLayer { alpha = if (flight.flying == key) 0f else 1f }
}

internal fun LayoutCoordinates.cardFlightBounds(): Rect =
    Rect(positionInRoot(), Size(size.width.toFloat(), size.height.toFloat()))

/**
 * Перелёт карты для [FullScreen]: из [origin] (мини-карта на главном) в место превью на экране настройки
 * ([target] — его сообщает сам экран). [progress] 0 — карта на главном, 1 — на месте. Пока летит, превью на экране
 * прячется ([flying]), а поверх всего рисуется [card] — та же карта; ей передаётся, насколько проявить надписи.
 */
class CardMorph internal constructor(val origin: Rect, internal val key: CardFlightKey) {
    var target by mutableStateOf<Rect?>(null)
    var card by mutableStateOf<(@Composable (textAlpha: () -> Float) -> Unit)?>(null)
    internal val progress = Animatable(0f)
    internal var closing by mutableStateOf(false)
    internal var returnOrigin by mutableStateOf<Rect?>(null)
    internal var movingTarget by mutableStateOf<Rect?>(null)
    internal var fallback by mutableStateOf(false)
    internal var settled by mutableStateOf(false)

    /** Летит (туда или обратно): настоящее превью на экране спрятано. */
    val flying: Boolean get() = !fallback && (!settled || closing)

    /** Фон экрана проявляется быстрее карты, содержимое — во второй половине полёта. */
    internal val backgroundAlpha: Float get() = (progress.value * 1.4f).coerceAtMost(1f)
    internal val contentAlpha: Float get() = ((progress.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
}

/** Летящая карта поверх экрана: от мини-карты к превью, надписи проявляются во второй половине пути. */
@Composable
internal fun CardMorphOverlay(morph: CardMorph) {
    val target = morph.movingTarget ?: morph.target ?: return
    val card = morph.card ?: return
    if (!morph.flying) return
    val flight = LocalCardFlight.current ?: return
    DisposableEffect(morph) {
        flight.flying = morph.key
        onDispose { if (flight.flying == morph.key) flight.flying = null }
    }
    val density = LocalDensity.current
    val o = morph.returnOrigin ?: morph.origin
    Box(
        Modifier
            .absoluteOffset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .graphicsLayer {
                val p = morph.progress.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = (o.left - target.left) * (1f - p)
                translationY = (o.top - target.top) * (1f - p)
                scaleX = lerp(o.width / target.width, 1f, p)
                scaleY = lerp(o.height / target.height, 1f, p)
            },
    ) {
        card { ((morph.progress.value - 0.45f) / 0.55f).coerceIn(0f, 1f) }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
