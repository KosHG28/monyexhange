package app.dirhamrub.ui.theme

import org.junit.Assert.*
import org.junit.Test

class FrameRatePolicyTest {
    @Test fun `idle has no high refresh vote or periodic expiry`() {
        val policy = FrameRatePolicy()
        policy.foreground(true)
        assertFalse(policy.boost(0))
        assertNull(policy.expiryDelay(0))
        assertFalse(policy.boost(Long.MAX_VALUE / 2))
    }

    @Test fun `touch boost expires without another tap or pointer release`() {
        val policy = FrameRatePolicy()
        policy.foreground(true)
        policy.interaction(100)
        assertTrue(policy.boost(100))
        assertEquals(1200L, policy.expiryDelay(100))
        assertTrue(policy.boost(1299))
        assertFalse(policy.boost(1300))
        assertNull(policy.expiryDelay(1300))
    }

    @Test fun `scroll and input extend boost until movement settles`() {
        val policy = FrameRatePolicy()
        policy.foreground(true)
        for (t in 0L..2000L step 100) {
            policy.interaction(t)
            assertTrue(policy.boost(t))
        }
        assertTrue(policy.boost(3199))
        assertFalse(policy.boost(3200))
    }

    @Test fun `overlapping animations cannot release each others request`() {
        val policy = FrameRatePolicy()
        policy.foreground(true)
        val first = Any(); val second = Any()
        policy.animation(first, true); policy.animation(second, true)
        policy.animation(first, false)
        assertTrue(policy.boost(100_000))
        policy.animation(first, false)
        assertTrue(policy.boost(100_000))
        policy.animation(second, false)
        assertFalse(policy.boost(100_000))
        assertNull(policy.expiryDelay(100_000))
    }

    @Test fun `background immediately releases boost and expires old interaction`() {
        val policy = FrameRatePolicy()
        policy.foreground(true); policy.interaction(0)
        policy.foreground(false)
        assertFalse(policy.boost(10))
        policy.interaction(20)
        policy.foreground(true)
        assertFalse(policy.boost(30))
    }

    @Test fun `reduced motion suppresses animation and touch boosts until disabled`() {
        val policy = FrameRatePolicy()
        val owner = Any()
        policy.foreground(true); policy.animation(owner, true)
        policy.reduced(true); policy.interaction(10)
        assertFalse(policy.boost(20))
        policy.reduced(false)
        assertTrue(policy.boost(30))
        policy.animation(owner, false)
        assertFalse(policy.boost(30))
    }

    @Test fun `finishing an animation preserves the remainder of a touch lease`() {
        val policy = FrameRatePolicy()
        val owner = Any()
        policy.foreground(true); policy.interaction(100); policy.animation(owner, true)
        assertNull(policy.expiryDelay(300))
        policy.animation(owner, false)
        assertTrue(policy.boost(300))
        assertEquals(1000L, policy.expiryDelay(300))
        assertFalse(policy.boost(1300))
    }

    @Test fun `sensor jitter does not invalidate a stationary screen`() {
        val gate = TiltPublicationGate()
        repeat(1000) { assertFalse(gate.accept(if (it % 2 == 0) 0.001f else -0.001f)) }
        assertTrue(gate.accept(0.008f))
        repeat(1000) { assertFalse(gate.accept(0.008f + if (it % 2 == 0) 0.001f else -0.001f)) }
        assertTrue(gate.accept(0f))
        assertFalse(gate.accept(0f))
        assertFalse(gate.accept(Float.NaN))
        assertFalse(gate.accept(Float.POSITIVE_INFINITY))
    }

    @Test fun `small accumulated movement still moves highlights and reset returns to rest`() {
        val gate = TiltPublicationGate()
        assertFalse(gate.accept(0.002f))
        assertFalse(gate.accept(0.004f))
        assertTrue(gate.accept(0.006f))
        assertTrue(gate.accept(-0.006f))
        gate.reset()
        assertFalse(gate.accept(0.001f))
    }
}
