package app.dirhamrub.data

import org.junit.Assert.*
import org.junit.Test

class ImageSizeTest {
    @Test fun `old near-double overshoot is capped without upscaling small images`() {
        assertEquals(ImageSize(1066, 1600), boundedImageSize(2048, 3072, 1600))
        assertEquals(ImageSize(1600, 1600), boundedImageSize(3199, 3199, 1600))
        assertEquals(ImageSize(160, 240), boundedImageSize(2048, 3072, 240))
        assertEquals(ImageSize(100, 200), boundedImageSize(100, 200, 1600))
    }

    @Test fun `portrait landscape huge and very narrow headers stay inside allocation bounds`() {
        for ((width, height) in listOf(4032 to 3024, 3024 to 4032, Int.MAX_VALUE to Int.MAX_VALUE, 1 to Int.MAX_VALUE)) {
            for (limit in listOf(1, 240, 1600, 4096)) {
                val size = boundedImageSize(width, height, limit)
                assertTrue(size.width in 1..limit && size.height in 1..limit)
                assertEquals(minOf(limit, maxOf(width, height)), maxOf(size.width, size.height))
                val sample = imageSampleSize(width, height, limit)
                assertTrue(sample > 0 && sample and (sample - 1) == 0)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupt image header is rejected before decoding`() {
        boundedImageSize(-1, 0, 1600)
    }
}
