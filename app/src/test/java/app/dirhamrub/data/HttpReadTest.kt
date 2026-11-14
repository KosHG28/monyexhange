package app.dirhamrub.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HttpReadTest {
    private class Connection(val bytes: ByteArray, val blocked: Boolean = false) : HttpURLConnection(URL("https://test.invalid")) {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val disconnects = AtomicInteger()
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnects.incrementAndGet(); closed.countDown() }
        override fun getResponseCode(): Int { entered.countDown(); if (blocked) closed.await(5, TimeUnit.SECONDS); return 200 }
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }
    @Test fun `cancellation disconnects stalled socket without retaining waiting coroutine`() = runBlocking {
        val connection = Connection(byteArrayOf(), blocked = true)
        val reading = async(Dispatchers.Default) { readHttp("fake") { connection } }
        assertTrue(connection.entered.await(5, TimeUnit.SECONDS))
        reading.cancelAndJoin()
        assertTrue(connection.closed.await(5, TimeUnit.SECONDS))
        assertEquals(1, connection.disconnects.get())
    }
    @Test fun `oversized response is rejected and connection is closed`() = runBlocking {
        val connection = Connection(ByteArray(2 * 1024 * 1024 + 1))
        assertNull(readHttp("fake") { connection })
        assertEquals(1, connection.disconnects.get())
    }
    @Test fun `recoverable does not swallow cancellation`() {
        try { recoverable<Unit> { throw CancellationException("stop") }; fail() } catch (_: CancellationException) { }
    }
}
