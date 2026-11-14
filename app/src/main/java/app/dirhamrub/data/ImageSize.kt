package app.dirhamrub.data

internal data class ImageSize(val width: Int, val height: Int)

/** Pixel limits, not density units; keep aspect ratio and never upscale a small receipt. */
internal fun boundedImageSize(width: Int, height: Int, maxSide: Int): ImageSize {
    require(width > 0 && height > 0 && maxSide > 0)
    val longest = maxOf(width, height)
    if (longest <= maxSide) return ImageSize(width, height)
    return ImageSize(
        (width.toLong() * maxSide / longest).coerceAtLeast(1).toInt(),
        (height.toLong() * maxSide / longest).coerceAtLeast(1).toInt(),
    )
}

internal fun imageSampleSize(width: Int, height: Int, maxSide: Int): Int {
    boundedImageSize(width, height, maxSide) // Validate corrupt headers before decoding.
    var sample = 1
    val longest = maxOf(width, height).toLong()
    while (sample <= Int.MAX_VALUE / 2 && longest / (sample.toLong() * 2) >= maxSide) sample *= 2
    return sample
}
