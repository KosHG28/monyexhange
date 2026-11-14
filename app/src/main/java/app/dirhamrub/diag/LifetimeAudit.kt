package app.dirhamrub.diag

import android.graphics.Bitmap
import android.os.Debug
import android.util.Log
import java.lang.ref.WeakReference

/** Debug-only weak observations; neither pixels nor Activities are held by the audit. */
internal object LifetimeAudit {
    private val receipts = ArrayList<WeakReference<Bitmap>>()
    fun watch(bitmap: Bitmap) {
        receipts.removeAll { it.get() == null }
        receipts += WeakReference(bitmap)
    }
    fun report() {
        receipts.removeAll { it.get() == null }
        val runtime = Runtime.getRuntime()
        Log.i("KpcAudit", "aliveReceipts=${receipts.size} javaBytes=${runtime.totalMemory() - runtime.freeMemory()} nativeBytes=${Debug.getNativeHeapAllocatedSize()}")
    }
}
