package app.dirhamrub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Откуда карта «перелетает» в настройку карты: где на главном мини-карта каждой строки (координаты окна) и какая
 * сейчас в полёте — её мини-карта в строке пустая («улетела»).
 */
class CardFlight {
    private val origins = HashMap<String, Rect>()
    var flying by mutableStateOf<String?>(null)

    fun originOf(id: String): Rect? = origins[id]?.takeIf { it.width > 0f && it.height > 0f }

    internal fun place(id: String, bounds: Rect) {
        origins[id] = bounds
    }
}

val LocalCardFlight = staticCompositionLocalOf { CardFlight() }

/** Мини-карта строки [id]: запоминает своё место для перелёта и прячется, пока карта «в полёте». */
fun Modifier.flightOrigin(id: String): Modifier = composed {
    val flight = LocalCardFlight.current
    this
        .onGloballyPositioned { flight.place(id, it.boundsInWindow()) }
        .graphicsLayer { alpha = if (flight.flying == id) 0f else 1f }
}

/**
 * Перелёт карты для [FullScreen]: из [origin] (мини-карта на главном) в место превью на экране настройки
 * ([target] — его сообщает сам экран). [progress] 0 — карта на главном, 1 — на месте. Пока летит, превью на экране
 * прячется ([flying]), а поверх всего рисуется [card] — та же карта; ей передаётся, насколько проявить надписи.
 */
class CardMorph(val origin: Rect, val cardId: String) {
    var target by mutableStateOf<Rect?>(null)
    var card by mutableStateOf<(@Composable (textAlpha: () -> Float) -> Unit)?>(null)
    internal val progress = Animatable(0f)
    internal var closing by mutableStateOf(false)
    internal var settled by mutableStateOf(false)

    /** Летит (туда или обратно): настоящее превью на экране спрятано. */
    val flying: Boolean get() = !settled || closing

    /** Фон экрана проявляется быстрее карты, содержимое — во второй половине полёта. */
    internal val backgroundAlpha: Float get() = (progress.value * 1.4f).coerceAtMost(1f)
    internal val contentAlpha: Float get() = ((progress.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
}

/** Летящая карта поверх экрана: от мини-карты к превью, надписи проявляются во второй половине пути. */
@Composable
internal fun CardMorphOverlay(morph: CardMorph) {
    val target = morph.target ?: return
    val card = morph.card ?: return
    if (!morph.flying) return
    val flight = LocalCardFlight.current
    DisposableEffect(morph) {
        flight.flying = morph.cardId
        onDispose { if (flight.flying == morph.cardId) flight.flying = null }
    }
    val density = LocalDensity.current
    val o = morph.origin
    Box(
        Modifier
            .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
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
