package app.dirhamrub.ui.camera

import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt

/**
 * Обёртка над анализатором ML Kit: перед распознаванием кадра берёт из него цвета ценников вокруг
 * найденных на прошлых кадрах цен — бумагу (вокруг рамки, отдельно у левого и правого края строки:
 * ценник бывает освещён неровно) и цифры (внутри) — для вписывания сумм.
 * Читается по несколько десятков пикселей на цену прямо из YUV, без копирования кадра.
 */
class TagColorAnalyzer(
    private val delegate: ImageAnalysis.Analyzer,
    private val pipeline: PricePipeline,
) : ImageAnalysis.Analyzer {
    /** Сенсор → экран; приходит от CameraController, потому что координаты — VIEW_REFERENCED. */
    @Volatile private var sensorToView: Matrix? = null
    private val viewToSensor = Matrix()
    private val viewToBuffer = Matrix()
    private val outer = FloatArray(OUTER_POINTS * 2)
    private val inner = FloatArray(INNER_COLUMNS * INNER_ROWS * 2)
    private val outerColors = IntArray(OUTER_POINTS)
    private val sideColors = IntArray(SIDE_POINTS)
    private val innerColors = IntArray(INNER_COLUMNS * INNER_ROWS)

    override fun getDefaultTargetResolution(): Size? = delegate.defaultTargetResolution

    override fun getTargetCoordinateSystem(): Int = ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED

    override fun updateTransform(matrix: Matrix?) {
        sensorToView = matrix?.let { Matrix(it) }
        delegate.updateTransform(matrix)
    }

    override fun analyze(image: ImageProxy) {
        val toView = sensorToView
        if (toView != null && pipeline.embedded) {
            try {
                sample(image, toView)
            } catch (e: Exception) {
                // Цвета — украшение: без них сумма впишется на светлой подложке, кадр всё равно распознаётся.
                Log.w(TAG, "Цвета ценника не взяты", e)
            }
        }
        delegate.analyze(image)
    }

    private fun sample(image: ImageProxy, toView: Matrix) {
        val targets = pipeline.sampleTargets()
        if (targets.isEmpty() || !toView.invert(viewToSensor)) return
        viewToBuffer.set(viewToSensor)
        viewToBuffer.postConcat(image.imageInfo.sensorToBufferTransformMatrix)
        val yuv = Yuv(image)
        for (t in targets) {
            val h = t.height
            if (h <= 0f || t.width <= 0f) continue
            // Точки считаются в системе строки (ровной), затем поворачиваются на её наклон:
            // у наклонённого ценника «над ценой» — это над строкой, а не над рамкой экрана (там стол).
            val rad = Math.toRadians(t.angle.toDouble())
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()
            val halfW = t.width / 2f
            val halfH = h / 2f
            val off = h * 0.35f
            var k = 0
            fun put(arr: FloatArray, lx: Float, ly: Float) {
                arr[k++] = t.cx + lx * cos - ly * sin
                arr[k++] = t.cy + lx * sin + ly * cos
            }
            // Бумага: над, под и по бокам от строки, на треть её высоты в стороне.
            for (i in 0 until EDGE_POINTS) {
                val lx = -halfW + t.width * (i + 0.5f) / EDGE_POINTS
                put(outer, lx, -halfH - off)
                put(outer, lx, halfH + off)
            }
            for (side in 0..1) {
                val lx = if (side == 0) -halfW - off else halfW + off
                put(outer, lx, -h * 0.2f)
                put(outer, lx, h * 0.2f)
            }
            // Цифры: сетка внутри строки.
            k = 0
            for (r in 0 until INNER_ROWS) for (cIdx in 0 until INNER_COLUMNS) {
                put(inner, -halfW + t.width * (cIdx + 0.5f) / INNER_COLUMNS, -halfH + h * (r + 1f) / (INNER_ROWS + 1))
            }
            viewToBuffer.mapPoints(outer)
            viewToBuffer.mapPoints(inner)
            for (i in 0 until OUTER_POINTS) outerColors[i] = yuv.rgb(outer[2 * i], outer[2 * i + 1])
            for (i in innerColors.indices) innerColors[i] = yuv.rgb(inner[2 * i], inner[2 * i + 1])

            val paper = medianByLuma(outerColors)
            // Цифры — самые непохожие на бумагу точки внутри (тёмные на светлом и светлые на тёмном).
            val ink = farthest(innerColors, paper)
            // Бумага у начала и у конца строки: подложка — переходом между ними, как падает свет.
            val start = medianByLuma(side(START_INDICES))
            val end = medianByLuma(side(END_INDICES))
            pipeline.setColors(t.id, start, end, ink)
        }
    }

    /** Точки бумаги у одного края строки (индексы в [outerColors]). */
    private fun side(indices: IntArray): IntArray {
        for (i in indices.indices) sideColors[i] = outerColors[indices[i]]
        return sideColors
    }

    /** Цвет с медианной яркостью: блик или чужая надпись рядом не перекрашивают бумагу. */
    private fun medianByLuma(colors: IntArray): Int {
        val sorted = colors.sortedBy { luma(it) }
        return sorted[sorted.size / 2]
    }

    /** Среднее трёх точек, сильнее всего отличающихся от [paper]. */
    private fun farthest(colors: IntArray, paper: Int): Int {
        val top = colors.sortedByDescending { distance(it, paper) }.take(3)
        var r = 0
        var g = 0
        var b = 0
        for (c in top) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        return rgb(r / top.size, g / top.size, b / top.size)
    }

    /** Пиксели YUV_420_888: яркость и цветность (U, V — вдвое реже по обеим осям). */
    private class Yuv(image: ImageProxy) {
        private val y = image.planes[0]
        private val u = image.planes[1]
        private val v = image.planes[2]
        private val width = image.width
        private val height = image.height

        fun rgb(fx: Float, fy: Float): Int {
            val x = fx.roundToInt().coerceIn(0, width - 1)
            val row = fy.roundToInt().coerceIn(0, height - 1)
            val yy = y.buffer.get(row * y.rowStride + x * y.pixelStride).toInt() and 0xFF
            val cx = x / 2
            val cy = row / 2
            val uu = (u.buffer.get(cy * u.rowStride + cx * u.pixelStride).toInt() and 0xFF) - 128
            val vv = (v.buffer.get(cy * v.rowStride + cx * v.pixelStride).toInt() and 0xFF) - 128
            return rgb(
                (yy + 1.402f * vv).roundToInt(),
                (yy - 0.344f * uu - 0.714f * vv).roundToInt(),
                (yy + 1.772f * uu).roundToInt(),
            )
        }
    }

    private companion object {
        const val TAG = "DirhamCamera"
        const val EDGE_POINTS = 5
        const val OUTER_POINTS = EDGE_POINTS * 2 + 4
        const val INNER_COLUMNS = 7
        const val INNER_ROWS = 3
        /**
         * Точки бумаги у начала строки: над и под двумя первыми столбцами (0–3) и слева сбоку (10, 11);
         * у конца — над и под двумя последними (6–9) и справа (12, 13).
         */
        val START_INDICES = intArrayOf(0, 1, 2, 3, 10, 11)
        val END_INDICES = intArrayOf(6, 7, 8, 9, 12, 13)
        const val SIDE_POINTS = 6

        fun rgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        fun luma(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

        fun distance(a: Int, b: Int): Int {
            val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
            val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
            val db = (a and 0xFF) - (b and 0xFF)
            return dr * dr + dg * dg + db * db
        }
    }
}
