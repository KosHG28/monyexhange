package app.dirhamrub.data

import app.dirhamrub.domain.wallet.OfficialRates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class RatesRaceTest {
    private val rates = OfficialRates(LocalDate.of(2026, 11, 14), emptyMap())
    @Test fun `official response inside grace wins`() = runTest {
        val result = raceRates({ delay(1000); rates }, { rates })
        assertEquals(CbrClient.SOURCE_CBR, result!!.source)
        assertEquals(1000, currentTime)
    }
    @Test fun `mirror wins after grace and cancels pending official child`() = runTest {
        var cancelled = false
        val result = raceRates({ try { CompletableDeferred<Unit>().await(); rates } finally { cancelled = true } }, { rates })
        assertEquals(CbrClient.SOURCE_MIRROR, result!!.source)
        assertEquals(1500, currentTime)
        assertTrue(cancelled)
    }
    @Test fun `two failures terminate and caller cancellation cancels both children`() = runTest {
        assertNull(raceRates({ null }, { null }))
        var closed = 0
        val reading = async { raceRates({ try { delay(10_000); rates } finally { closed++ } }, { try { delay(10_000); rates } finally { closed++ } }) }
        runCurrent()
        reading.cancel()
        runCurrent()
        assertEquals(2, closed)
    }
}
