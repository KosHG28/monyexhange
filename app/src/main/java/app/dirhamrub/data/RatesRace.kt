package app.dirhamrub.data

import app.dirhamrub.domain.wallet.OfficialRates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/** Keep the official source's grace period, then cancel the losing request instead of orphaning it. */
internal suspend fun raceRates(
    primary: suspend () -> OfficialRates?,
    mirror: suspend () -> OfficialRates?,
    graceMillis: Long = 1_500,
): CbrClient.Fetched? = coroutineScope {
    val result = CompletableDeferred<CbrClient.Fetched?>()
    val primaryDone = CompletableDeferred<Unit>()
    val failed = AtomicInteger()
    fun fail() { if (failed.incrementAndGet() == 2) result.complete(null) }
    val first = launch {
        val value = primary()
        primaryDone.complete(Unit)
        if (value == null) fail() else result.complete(CbrClient.Fetched(value, CbrClient.SOURCE_CBR))
    }
    val second = launch {
        val value = mirror()
        if (value == null) fail() else {
            withTimeoutOrNull(graceMillis) { primaryDone.await() }
            result.complete(CbrClient.Fetched(value, CbrClient.SOURCE_MIRROR))
        }
    }
    try { result.await() } finally { first.cancel(); second.cancel() }
}
