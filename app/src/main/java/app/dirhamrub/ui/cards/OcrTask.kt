package app.dirhamrub.ui.cards

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A non-cancellable native task owns its input until completion, even after UI cancellation. */
internal suspend fun <T, R> awaitOwnedTask(start: () -> Task<T>, release: () -> Unit, transform: (T) -> R): R? =
    suspendCancellableCoroutine { continuation ->
        if (!continuation.isActive) {
            release()
            return@suspendCancellableCoroutine
        }
        val task = try {
            start()
        } catch (cancelled: CancellationException) {
            release()
            continuation.cancel(cancelled)
            return@suspendCancellableCoroutine
        } catch (_: Exception) {
            release()
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        task.addOnCompleteListener({ command -> command.run() }) { completed ->
            val result = if (continuation.isActive && completed.isSuccessful)
                runCatching { transform(completed.result) }.getOrNull() else null
            try {
                release()
            } finally {
                continuation.resume(result)
            }
        }
    }
