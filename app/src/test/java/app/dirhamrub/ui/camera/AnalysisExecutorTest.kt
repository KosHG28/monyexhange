package app.dirhamrub.ui.camera

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AnalysisExecutorTest {
    @Test fun `idle worker is freed and late OCR completion still executes`() {
        val executor = newAnalysisExecutor(idleMillis = 25)
        try {
            val first = CountDownLatch(1)
            executor.execute { first.countDown() }
            assertTrue(first.await(2, TimeUnit.SECONDS))
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (executor.poolSize != 0 && System.nanoTime() < end) Thread.sleep(10)
            assertEquals(0, executor.poolSize)
            assertFalse(executor.isShutdown)
            val late = CountDownLatch(1)
            executor.execute { late.countDown() }
            assertTrue(late.await(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
    }
}
