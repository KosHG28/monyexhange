package app.dirhamrub.ui.theme

import android.view.Surface
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CardReflectionTest {
    @Test
    fun `the highlight follows both axes and stays where the phone is held`() {
        val filter = CardTiltFilter()
        assertEquals(Offset.Zero, filter.read(matrix(), 0))
        val tilt = matrix(yaw = -0.22f, pitch = 0.19f)
        val first = filter.read(tilt, SAMPLE_NS)
        assertTrue(first.x < -0.15f && first.y > 0.15f)
        val settled = hold(filter, tilt, 2, 40)
        assertTrue(settled.x < -0.5f && settled.y > 0.45f)
        assertEquals(settled, hold(filter, tilt, 41, 600))
        assertEquals(Offset.Zero, hold(filter, matrix(), 601, 660))
    }

    @Test
    fun `resume returns a previous highlight to neutral smoothly`() {
        val filter = CardTiltFilter()
        val previous = Offset(-0.8f, 0.6f)
        filter.reset(previous)
        val rest = matrix(yaw = 0.42f, pitch = -0.35f)
        assertEquals(previous, filter.read(rest, 0))
        val moving = filter.read(rest, SAMPLE_NS)
        assertTrue(moving.x > previous.x && moving.x < 0f)
        assertTrue(moving.y < previous.y && moving.y > 0f)
        assertEquals(Offset.Zero, hold(filter, rest, 2, 50))
    }

    @Test
    fun `landscape remaps tilt and small sensor noise stays unpublished`() {
        val filter = CardTiltFilter()
        val rotation = Surface.ROTATION_90
        filter.read(matrix(), 0, rotation)
        for (sample in 1..30) {
            val jitter = matrix(yaw = 0.0008f, pitch = -0.0008f)
            assertEquals(Offset.Zero, filter.read(jitter, sample * SAMPLE_NS, rotation))
        }
        val landscape = filter.read(matrix(pitch = 0.42f), 31 * SAMPLE_NS, rotation)
        assertTrue(landscape.x < -0.3f)
        assertEquals(0f, landscape.y, 0.001f)
    }

    private fun hold(filter: CardTiltFilter, pose: FloatArray, first: Int, last: Int): Offset {
        for (sample in first until last) filter.read(pose, sample * SAMPLE_NS)
        return filter.read(pose, last * SAMPLE_NS)
    }

    private fun matrix(yaw: Float = 0f, pitch: Float = 0f): FloatArray {
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        return floatArrayOf(cy, sy * sp, sy * cp, 0f, cp, -sp, -sy, cy * sp, cy * cp)
    }

    private companion object {
        const val SAMPLE_NS = 33_000_000L
    }
}
