package app.dirhamrub.ui.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentDragTest {
    @Test
    fun `the dragged card follows the pointer and crosses slots without jumping`() {
        val drag = PaymentDrag(listOf("a", "b", "c"))
        drag.place("a", Rect(0f, 0f, 100f, 58f))
        drag.place("b", Rect(0f, 64f, 100f, 122f))
        drag.place("c", Rect(0f, 128f, 100f, 186f))
        assertTrue(drag.start(Offset(50f, 142f)))
        drag.dragBy(-70f)
        assertEquals(listOf("a", "c", "b"), drag.order)
        drag.place("c", Rect(0f, 64f, 100f, 122f))
        assertEquals(-6f, drag.translationOf("c"), 0f)
        drag.dragBy(-100f)
        assertEquals(listOf("c", "a", "b"), drag.finish())
    }

    @Test
    fun `a long press without moving does not persist an order`() {
        val drag = PaymentDrag(listOf("a", "b"))
        drag.place("a", Rect(0f, 0f, 100f, 60f))
        drag.place("b", Rect(0f, 66f, 100f, 126f))
        assertTrue(drag.start(Offset(15f, 30f)))
        assertNull(drag.finish())
    }
}
