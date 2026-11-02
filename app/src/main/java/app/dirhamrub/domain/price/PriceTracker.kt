package app.dirhamrub.domain.price

import java.math.BigDecimal

/** Стабильная метка цены для отрисовки. */
data class TrackedPrice(
    val id: Long,
    val value: BigDecimal,
    val box: Box,
    val score: Float,
    val hasCurrency: Boolean,
    val isOld: Boolean,
    /** Прозрачность 0..1: плавное исчезновение после пропажи. */
    val alpha: Float,
    /** См. [PriceCandidate.confident]. */
    val confident: Boolean = true,
)

/**
 * Стабилизирует цены между кадрами:
 * - сопоставляет находку с меткой прошлых кадров по значению и близости (центр или IoU);
 * - сглаживает рамку экспоненциальным средним;
 * - показывает метку после [minHits] попаданий из [window] последних кадров;
 * - держит метку [holdMs] после пропажи, во второй половине срока — плавно гасит;
 * - если среди меток есть уверенная цена — догадки (числа без валюты) скрывает;
 * - если на одном месте две метки с разными числами (OCR читает то «200», то «20») — оставляет ту,
 *   что видна стабильнее, при равенстве — с бо́льшим скорингом;
 * - отдаёт не больше [maxLabels] лучших по скорингу.
 */
class PriceTracker(
    private val maxLabels: Int = 6,
    private val smoothing: Float = 0.35f,
    private val window: Int = 4,
    private val minHits: Int = 2,
    private val holdMs: Long = 400L,
) {
    private class Track(
        val id: Long,
        val value: BigDecimal,
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var score: Float,
        var hasCurrency: Boolean,
        var isOld: Boolean,
        var confident: Boolean,
        var history: Int,
        var lastSeenMs: Long,
        var shown: Boolean = false,
    ) {
        val box: Box get() = Box(left, top, right, bottom)
    }

    private val tracks = ArrayList<Track>()
    private var nextId = 1L
    private val windowMask = (1 shl window) - 1

    fun clear() {
        tracks.clear()
    }

    fun update(candidates: List<PriceCandidate>, nowMs: Long): List<TrackedPrice> {
        for (t in tracks) t.history = (t.history shl 1) and windowMask

        val matched = HashSet<Track>()
        for (c in candidates.sortedByDescending { it.score }) {
            val track = bestMatch(c, matched)
            if (track == null) {
                val t = Track(
                    id = nextId++, value = c.value,
                    left = c.box.left, top = c.box.top, right = c.box.right, bottom = c.box.bottom,
                    score = c.score, hasCurrency = c.hasCurrency, isOld = c.isOld, confident = c.confident,
                    history = 1, lastSeenMs = nowMs,
                )
                tracks += t
                matched += t
            } else {
                track.left += smoothing * (c.box.left - track.left)
                track.top += smoothing * (c.box.top - track.top)
                track.right += smoothing * (c.box.right - track.right)
                track.bottom += smoothing * (c.box.bottom - track.bottom)
                track.score += smoothing * (c.score - track.score)
                track.hasCurrency = track.hasCurrency || c.hasCurrency
                track.isOld = c.isOld
                track.confident = track.confident || c.confident
                track.history = track.history or 1
                track.lastSeenMs = nowMs
                matched += track
            }
        }

        val it = tracks.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (!t.shown && Integer.bitCount(t.history) >= minHits) t.shown = true
            val expired = if (t.shown) nowMs - t.lastSeenMs > holdMs else t.history == 0
            if (expired) it.remove()
        }

        val visible = ArrayList<Track>()
        // Есть уверенная цена (с валютой или копейками) — догадки без валюты не показываем: в соседних кадрах
        // OCR не всегда видит валюту, и голые числа (артикул, размер) успевали проскочить серыми метками.
        val shown = tracks.filter { it.shown }
        val anyConfident = shown.any { it.confident && !it.isOld }
        for (t in shown.filter { it.confident || !anyConfident }.sortedWith(compareByDescending<Track> { Integer.bitCount(it.history) }.thenByDescending { it.score })) {
            if (visible.none { samePlace(it.box, t.box) }) visible += t
        }
        return visible
            .sortedByDescending { it.score }
            .take(maxLabels)
            .map { t ->
                TrackedPrice(t.id, t.value, t.box, t.score, t.hasCurrency, t.isOld, alpha(t, nowMs), t.confident)
            }
    }

    /** Одно место кадра: рамки сильно пересекаются или центр одной внутри другой. */
    private fun samePlace(a: Box, b: Box): Boolean =
        a.iou(b) > SAME_PLACE_IOU || a.contains(b.centerX, b.centerY) || b.contains(a.centerX, a.centerY)

    private fun alpha(t: Track, nowMs: Long): Float {
        val missing = nowMs - t.lastSeenMs
        val fadeStart = holdMs / 2
        if (missing <= fadeStart) return 1f
        return (1f - (missing - fadeStart).toFloat() / (holdMs - fadeStart)).coerceIn(0f, 1f)
    }

    private fun bestMatch(c: PriceCandidate, taken: Set<Track>): Track? {
        var best: Track? = null
        var bestDistance = Float.MAX_VALUE
        for (t in tracks) {
            if (t in taken || t.value.compareTo(c.value) != 0) continue
            val box = t.box
            val distance = box.centerDistance(c.box)
            val near = distance <= NEAR_FACTOR * maxOf(box.height, c.box.height)
            if (!near && box.iou(c.box) < MIN_IOU) continue
            if (distance < bestDistance) {
                best = t
                bestDistance = distance
            }
        }
        return best
    }

    private companion object {
        const val NEAR_FACTOR = 1.5f
        const val MIN_IOU = 0.3f
        const val SAME_PLACE_IOU = 0.3f
    }
}
