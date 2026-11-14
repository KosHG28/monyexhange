package app.dirhamrub.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal inline fun <T> recoverable(block: () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

internal data class HttpReply(val code: Int, val body: ByteArray)

/** Cancellation releases the waiter immediately; blocking socket operations never run on the UI. */
internal suspend fun readHttp(
    url: String,
    open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
): HttpReply? = suspendCancellableCoroutine { continuation ->
    val connection = recoverable { open(url) }
    if (connection == null) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val closed = AtomicBoolean()
    fun close() { if (closed.compareAndSet(false, true)) runCatching { connection.disconnect() } }
    val executor = Dispatchers.IO.asExecutor()
    continuation.invokeOnCancellation { executor.execute { close() } }
    executor.execute {
        val reply = try {
            if (!continuation.isActive) null else {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "KursPoCheku/1.0 (Android)")
                val code = connection.responseCode
                val body = if (code == HttpURLConnection.HTTP_OK) connection.inputStream.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > MAX_RESPONSE_BYTES) throw IOException("Rate response exceeds limit")
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                } else ByteArray(0)
                HttpReply(code, body)
            }
        } catch (_: Exception) {
            null
        } finally {
            close()
        }
        continuation.resume(reply)
    }
}

private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
