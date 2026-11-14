package app.dirhamrub.diag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import app.dirhamrub.data.ReceiptShots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Only a debug fixture on an isolated emulator; no picker, camera or personal screenshot. */
internal suspend fun seedReceiptFixture(context: Context) = withContext(Dispatchers.IO) {
    val file = File.createTempFile("demo-bank-", ".jpg", context.cacheDir)
    val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 48f }
        val canvas = Canvas(bitmap)
        canvas.drawText("Демонстрационный чек", 70f, 150f, paint)
        canvas.drawText("Тестовые данные", 70f, 240f, paint)
        canvas.drawText("125,00 EUR", 70f, 420f, paint)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        checkNotNull(ReceiptShots.save(context, Uri.fromFile(file), "demo-cny", ReceiptShots.TRANSFER_KIND))
    } finally {
        bitmap.recycle()
        file.delete()
    }
}
