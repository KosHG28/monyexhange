package app.dirhamrub.diag

import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import java.lang.ref.WeakReference

/** Debug-only weak observations; neither pixels nor Activities are held by the audit. */
internal object LifetimeAudit {
    private data class Observation(val image: WeakReference<Bitmap>, val created: Long)
    private val receipts = ArrayList<Observation>()
    fun watch(bitmap: Bitmap) {
        receipts.removeAll { it.image.get() == null }
        receipts += Observation(WeakReference(bitmap), SystemClock.elapsedRealtime())
    }
    fun report() {
        receipts.removeAll { it.image.get() == null }
        val runtime = Runtime.getRuntime()
        val details = receipts.mapNotNull { observation -> observation.image.get()?.let {
            "${it.width}x${it.height}:ageMs=${SystemClock.elapsedRealtime() - observation.created}:recycled=${it.isRecycled}"
        } }
        Log.i("KpcAudit", "aliveReceipts=${receipts.size} javaBytes=${runtime.totalMemory() - runtime.freeMemory()} nativeBytes=${Debug.getNativeHeapAllocatedSize()} images=$details")
    }
}
