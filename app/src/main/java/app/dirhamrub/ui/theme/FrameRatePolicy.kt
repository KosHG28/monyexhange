package app.dirhamrub.ui.theme

/** Event-driven leases, with no perpetual frame clock or polling while the screen is idle. */
internal class FrameRatePolicy(private val touchBoostMs: Long = 1_200L) {
    private val animations = HashSet<Any>()
    private var interactionUntil = Long.MIN_VALUE
    private var foreground = false
    private var reduced = false

    fun foreground(active: Boolean) {
        foreground = active
        if (!active) interactionUntil = Long.MIN_VALUE
    }

    fun reduced(value: Boolean) {
        reduced = value
        if (value) interactionUntil = Long.MIN_VALUE
    }

    fun animation(owner: Any, active: Boolean) {
        if (active) animations += owner else animations -= owner
    }

    fun interaction(now: Long) {
        if (foreground && !reduced) interactionUntil = now + touchBoostMs
    }

    fun boost(now: Long): Boolean = foreground && !reduced && (animations.isNotEmpty() || now < interactionUntil)

    /** null: sleep until a new event; positive: wake once when the finite touch lease expires. */
    fun expiryDelay(now: Long): Long? =
        if (foreground && !reduced && animations.isEmpty() && now < interactionUntil) interactionUntil - now else null
}

/** Ignore sensor jitter below roughly one dp of highlight displacement on a 400-dp screen. */
internal class TiltPublicationGate(private val minimumDelta: Float = 0.005f) {
    private var last = 0f

    fun accept(value: Float): Boolean {
        if (!value.isFinite()) return false
        if (kotlin.math.abs(value - last) < minimumDelta && !(value == 0f && last != 0f)) return false
        last = value
        return true
    }

    fun reset() { last = 0f }
}
