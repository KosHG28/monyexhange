package app.dirhamrub.ui.components

import org.junit.Assert.*
import org.junit.Test

class TripChoreographyTest {
    @Test fun `seven independent identities and all animation windows stay within one second`() {
        assertEquals(7, setOf(TRIP_COUNTRY, TRIP_HERO, TRIP_CARD, TRIP_TITLE, TRIP_RATE, TRIP_PRICE, TRIP_VALUE).size)
        assertEquals(850, TRIP_BOUNDS_DURATION)
        assertEquals(1000, TRIP_DURATION)
        TripEnter.entries.forEach { kind ->
            for (index in -1..10) {
                val w = tripEnterWindow(kind, index)
                assertTrue(w.delay >= 0 && w.duration > 0 && w.end <= TRIP_DURATION)
            }
        }
    }
    @Test fun `keyboard starts during flight and builds bottom to top every 55 ms`() {
        val rows = (4 downTo 0).map { tripEnterWindow(TripEnter.KEYPAD, it) }
        assertEquals(listOf(260, 315, 370, 425, 480), rows.map { it.delay })
        assertTrue(rows.all { it.delay < TRIP_BOUNDS_DURATION })
        assertEquals(880, rows.last().end)
    }
    @Test fun `cards actions and badge appear during the shared flight`() {
        assertEquals(TripWindow(280, 480), tripEnterWindow(TripEnter.PAYMENT, 0))
        assertEquals(TripWindow(360, 480), tripEnterWindow(TripEnter.PAYMENT, 1))
        assertEquals(TripWindow(420, 380), tripEnterWindow(TripEnter.ADD))
        assertEquals(TripWindow(650, 200), tripEnterWindow(TripEnter.BADGE))
        assertEquals(TRIP_BOUNDS_DURATION, tripEnterWindow(TripEnter.BADGE).end)
        assertTrue(tripEnterWindow(TripEnter.PRIMARY_DETAILS).end < tripEnterWindow(TripEnter.BADGE).delay)
    }
    @Test fun `old and new hero price and rate text never overlap`() {
        val incoming = tripTextEnterWindow()
        assertEquals(TripWindow(260, 240), incoming)
        for(key in listOf(TRIP_HERO, TRIP_RATE, TRIP_VALUE)) {
            val outgoing = tripTextExitWindow(key)
            assertTrue(outgoing.end <= 180)
            assertTrue("$key must be fully gone before incoming text", outgoing.end < incoming.delay)
        }
        assertEquals(150, tripTextExitWindow(TRIP_HERO).end)
        assertEquals(180, tripTextExitWindow(TRIP_VALUE).end)
    }
    @Test fun `onboarding extras leave before 450 ms and chips stagger`() {
        TripExit.entries.forEach { kind ->
            for(index in 0..2) assertTrue(tripExitWindow(kind, index).end <= 450)
        }
        assertEquals(listOf(60, 80, 100), (0..2).map { tripExitWindow(TripExit.CHIPS, it).delay })
    }
}
