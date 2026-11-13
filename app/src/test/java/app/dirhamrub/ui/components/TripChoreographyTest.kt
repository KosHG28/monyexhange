package app.dirhamrub.ui.components

import org.junit.Assert.*
import org.junit.Test

class TripChoreographyTest {
    @Test fun `seven independent shared identities and a one second deadline`() {
        assertEquals(7, setOf(TRIP_COUNTRY, TRIP_HERO, TRIP_CARD, TRIP_TITLE, TRIP_RATE, TRIP_PRICE, TRIP_VALUE).size)
        assertEquals(950, TRIP_BOUNDS_DURATION)
        assertEquals(1000, TRIP_DURATION)
        TripEnter.entries.forEach { kind ->
            for (index in -1..10) {
                val w = tripEnterWindow(kind, index)
                assertTrue(w.delay >= 0 && w.duration > 0 && w.end <= TRIP_DURATION)
            }
        }
    }
    @Test fun `keyboard builds bottom to top with 55 ms spacing and ends at exactly 1000 ms`() {
        val rows = (4 downTo 0).map { tripEnterWindow(TripEnter.KEYPAD, it) }
        assertEquals(listOf(380, 435, 490, 545, 600), rows.map { it.delay })
        assertEquals(1000, rows.last().end)
    }
    @Test fun `late card rows and add button are trimmed to the deadline`() {
        assertEquals(940, tripEnterWindow(TripEnter.PAYMENT, 0).end)
        assertEquals(1000, tripEnterWindow(TripEnter.PAYMENT, 1).end)
        assertEquals(1000, tripEnterWindow(TripEnter.ADD).end)
        assertEquals(720, tripEnterWindow(TripEnter.BADGE).delay)
        assertEquals(480, tripEnterWindow(TripEnter.PRICE_LABEL).delay)
        assertEquals(650, tripEnterWindow(TripEnter.PRIMARY_DETAILS).delay)
    }
    @Test fun `onboarding extras leave before 450 ms and chips stagger`() {
        TripExit.entries.forEach { kind ->
            for(index in 0..2) assertTrue(tripExitWindow(kind, index).end <= 450)
        }
        assertEquals(listOf(60, 80, 100), (0..2).map { tripExitWindow(TripExit.CHIPS, it).delay })
    }
}
