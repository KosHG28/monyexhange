package app.dirhamrub.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/** Caller owns the decoded bitmap. Run on a worker thread. */
internal object ReceiptImages {
    fun decode(file: File, maxSide: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val size = boundedImageSize(info.size.width, info.size.height, maxSide)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.setTargetSize(size.width, size.height)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            legacy(bounds, maxSide) { options -> BitmapFactory.decodeFile(file.path, options) }
        }

    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val size = boundedImageSize(info.size.width, info.size.height, maxSide)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.setTargetSize(size.width, size.height)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            legacy(bounds, maxSide) { options ->
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }

    private fun legacy(bounds: BitmapFactory.Options, maxSide: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val sample = imageSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        val bitmap = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val target = boundedImageSize(bitmap.width, bitmap.height, maxSide)
        if (target.width == bitmap.width && target.height == bitmap.height) return bitmap
        return try {
            Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
        } finally {
            bitmap.recycle() // Private intermediate; never published to a UI or ML Kit.
        }
    }
}
