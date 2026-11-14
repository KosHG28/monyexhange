package app.dirhamrub.ui.cards

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OcrTaskTest {
    @Test fun `closing editor does not recycle an input still used by native OCR`() = runTest {
        val task = TaskCompletionSource<String>()
        var released = 0
        var transformed = false
        val reading = async {
            awaitOwnedTask({ task.task }, { released++ }) { transformed = true; it }
        }
        runCurrent()
        reading.cancel()
        runCurrent()
        assertEquals(0, released)
        task.setResult("late result")
        assertEquals(1, released)
        assertFalse(transformed)
    }

    @Test fun `successful result releases its private bitmap and recognizer before delivery`() = runTest {
        var released = 0
        val result = awaitOwnedTask({ Tasks.forResult("text") }, { released++ }) { it.uppercase() }
        assertEquals("TEXT", result)
        assertEquals(1, released)
    }

    @Test fun `failed recognition releases resources and returns no text`() = runTest {
        var released = 0
        val result = awaitOwnedTask({ Tasks.forException<String>(IllegalStateException("OCR failed")) }, { released++ }) { it }
        assertNull(result)
        assertEquals(1, released)
    }

    @Test fun `failure before SDK submission also releases input`() = runTest {
        var released = 0
        val result = awaitOwnedTask<String, String>({ error("No recognizer") }, { released++ }) { it }
        assertNull(result)
        assertEquals(1, released)
    }
}
