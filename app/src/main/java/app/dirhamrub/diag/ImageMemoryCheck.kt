package app.dirhamrub.diag

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import app.dirhamrub.data.ReceiptImages
import java.io.File

/** Opt-in emulator decoder check. No camera, gallery UI, personal wallet or permanent fixtures. */
internal fun checkImageMemory(context: Context) {
    val file = File.createTempFile("receipt-memory-", ".jpg", context.cacheDir)
    try {
        val source = Bitmap.createBitmap(2048, 3072, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(android.graphics.Color.WHITE)
            file.outputStream().use { check(source.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
        } finally {
            source.recycle()
        }
        val allocations = mutableListOf<String>()
        for (limit in listOf(240, 1600)) {
            val bitmap = checkNotNull(ReceiptImages.decode(file, limit))
            try {
                check(maxOf(bitmap.width, bitmap.height) == limit)
                allocations += "$limit:${bitmap.width}x${bitmap.height}:${bitmap.allocationByteCount}"
            } finally {
                bitmap.recycle()
            }
            check(bitmap.isRecycled)
        }
        val fromUri = checkNotNull(ReceiptImages.decode(context, Uri.fromFile(file), 1600))
        try { check(maxOf(fromUri.width, fromUri.height) == 1600) } finally { fromUri.recycle() }
        Log.i("KpcImages", "PASS limits and private bitmap release ${allocations.joinToString()}")
    } catch (error: Exception) {
        Log.e("KpcImages", "FAIL decoder memory check", error)
    } finally {
        file.delete()
    }
}
