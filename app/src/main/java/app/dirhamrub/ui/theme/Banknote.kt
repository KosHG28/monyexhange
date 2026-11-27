package app.dirhamrub.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dirhamrub.domain.wallet.Currency
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal enum class BanknoteMotif {
    OVAL, ARCHES, WAVES, LOTUS, STAR, STEPPE, CORNERS, LATTICE, TULIP, ROSETTE, GUILLOCHE,
}

internal data class BanknoteTheme(
    val primaryColor: Color,
    val secondaryColor: Color,
    val foilColors: List<Color>,
    val motifType: BanknoteMotif,
    val isPolymer: Boolean,
    val patternSeed: Int,
)

private fun note(
    primary: Long,
    secondary: Long,
    motif: BanknoteMotif,
    vararg foil: Long,
    polymer: Boolean = false,
) = BanknoteTheme(Color(primary), Color(secondary), foil.map { Color(it) }, motif, polymer, 0)

private val banknoteThemes = mapOf(
    Currency.USD to note(0xFF3B5342, 0xFF5E7864, BanknoteMotif.OVAL, 0xFF73AC89, 0xFFDDD096, 0xFF72916A),
    Currency.EUR to note(0xFF2A4B73, 0xFF4973A3, BanknoteMotif.ARCHES, 0xFF9CAFFA, 0xFFACDEC9, 0xFFF2D7AC, 0xFFC4A9E9),
    Currency.JPY to note(0xFF2B3B4E, 0xFF495F78, BanknoteMotif.WAVES, 0xFFABA9C9, 0xFFF1D6E4, 0xFF9DBAD0),
    Currency.CNY to note(0xFF7D242B, 0xFFAD3B44, BanknoteMotif.LOTUS, 0xFFB35D57, 0xFFF4D376, 0xFFD09561),
    Currency.AED to note(0xFF1E5E56, 0xFF37857B, BanknoteMotif.STAR, 0xFF7BAAD2, 0xFF9DE3CF, 0xFFD9CC9F, polymer = true),
    Currency.VND to note(0xFF196660, 0xFF2E8F87, BanknoteMotif.LOTUS, 0xFF89B8D6, 0xFFB7EFE4, 0xFFECBCCA, polymer = true),
    Currency.KZT to note(0xFF1D6373, 0xFF358A9E, BanknoteMotif.STEPPE, 0xFF90BCAC, 0xFFF2D785, 0xFFA9955F),
    Currency.THB to note(0xFF59326B, 0xFF804D96, BanknoteMotif.CORNERS, 0xFFC2A1D8, 0xFFE8C79C, 0xFFB893CC),
    Currency.KRW to note(0xFF1E5945, 0xFF337D64, BanknoteMotif.LATTICE, 0xFF81B997, 0xFFCAE7BA, 0xFF9CBED1),
    Currency.TRY to note(0xFF8A3B2B, 0xFFB55440, BanknoteMotif.TULIP, 0xFFC28374, 0xFFF5D492, 0xFFE0A27B),
    Currency.RUB to note(0xFF1F4E46, 0xFF327065, BanknoteMotif.ROSETTE, 0xFF81BAA8, 0xFFDDCE92, 0xFF90BCB9),
)

private val generatedThemes = mutableMapOf<Currency, BanknoteTheme>()

internal fun banknoteTheme(currency: Currency): BanknoteTheme =
    banknoteThemes[currency] ?: synchronized(generatedThemes) {
        generatedThemes.getOrPut(currency) {
            val seed = currency.code.hashCode()
            val flagHues = flagHues(currency.flag)
            val hue = flagHues.firstOrNull() ?: (seed % 360).toFloat()
            val accentHue = flagHues.getOrNull(1) ?: ((hue + 24f) % 360f)
            BanknoteTheme(
                primaryColor = Color.hsl(hue, 0.36f, 0.23f),
                secondaryColor = Color.hsl(accentHue, 0.34f, 0.34f),
                foilColors = listOf(Color.hsl(hue, 0.38f, 0.62f), Color.hsl(accentHue, 0.47f, 0.81f), Color.hsl(hue, 0.35f, 0.58f)),
                motifType = BanknoteMotif.GUILLOCHE,
                isPolymer = false,
                patternSeed = seed,
            )
        }
    }

/** Use the flag already associated with the currency, sampling the chromatic regions of its glyph. */
private fun flagHues(flag: String): List<Float> {
    val px = 40
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 25f
        textAlign = Paint.Align.CENTER
    }
    Canvas(bitmap).drawText(flag, px / 2f, (px - paint.ascent() - paint.descent()) / 2f, paint)
    val pixels = IntArray(px * px)
    bitmap.getPixels(pixels, 0, px, 0, 0, px, px)
    bitmap.recycle()
    val bins = IntArray(12)
    val hsv = FloatArray(3)
    for (pixel in pixels) {
        if ((pixel ushr 24) < 200) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        if (hsv[1] >= 0.3f && hsv[2] >= 0.26f) bins[((hsv[0] + 15f) / 30f).toInt() % bins.size]++
    }
    return bins.indices.filter { bins[it] >= 16 }.sortedByDescending { bins[it] }.take(2).map { it * 30f }
}

/** The caller clips to its note silhouette. Tilt is read in draw, independently of the note layout. */
internal fun Modifier.banknoteSurface(
    theme: BanknoteTheme,
    corner: Dp,
    tilt: State<Offset>? = null,
    miniature: Boolean = false,
): Modifier = drawWithCache {
    val backdrop = Brush.linearGradient(listOf(theme.primaryColor, theme.secondaryColor, theme.primaryColor))
    val outline = Stroke(if (miniature) 0.45.dp.toPx() else 0.6.dp.toPx())
    val margin = (if (miniature) 1.5.dp else 4.dp).toPx()
    val ornament = noteOrnament(size, margin, theme)
    val frame = Path()
    val cornerMarks = Path()
    val gap = margin * 1.65f
    for (index in 0..3) {
        val x = if (index % 2 == 0) gap else size.width - gap
        val y = if (index < 2) gap else size.height - gap
        val dx = if (index % 2 == 0) margin else -margin
        val dy = if (index < 2) margin else -margin
        frame.moveTo(x, y + dy * 2f)
        frame.lineTo(x, y)
        frame.lineTo(x + dx * 2f, y)
        if (!miniature) {
            val ornamentCenter = Offset(x + dx * 0.65f, y + dy * 0.65f)
            for (point in 0..24) {
                val phase = point * PI / 12
                val r = margin * (0.26f + 0.08f * cos(phase * 6).toFloat())
                val px = ornamentCenter.x + r * cos(phase).toFloat()
                val py = ornamentCenter.y + r * sin(phase).toFloat()
                if (point == 0) cornerMarks.moveTo(px, py) else cornerMarks.lineTo(px, py)
            }
            for (n in 1..3) {
                cornerMarks.moveTo(x + dx * n / 2f, y)
                cornerMarks.lineTo(x + dx * n / 2f, y + dy * 0.38f)
                cornerMarks.moveTo(x, y + dy * n / 2f)
                cornerMarks.lineTo(x + dx * 0.38f, y + dy * n / 2f)
            }
        }
    }
    val foilWidth = (if (miniature) 3.dp else 16.dp).toPx()
    val foilX = size.width * 0.68f
    val foilTop = margin
    val foilHeight = (size.height - margin * 2f).coerceAtLeast(0f)
    val foilSize = Size(foilWidth, foilHeight)
    val foil = Brush.linearGradient(
        theme.foilColors.map { it.copy(alpha = if (theme.isPolymer) 0.43f else 0.35f) },
        start = Offset(foilX, 0f), end = Offset(foilX + foilWidth, size.height),
    )
    val gloss = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.20f), Color.Transparent, Color.Black.copy(alpha = 0.10f)),
    )
    onDrawBehind {
        drawRect(backdrop)
        if (!miniature && !theme.isPolymer) drawCardGrain(0.03f)
        if (theme.isPolymer) drawRect(gloss)
        drawPath(ornament, Color.White.copy(alpha = if (miniature) 0.20f else 0.13f), style = outline)
        val foilOrigin = Offset(foilX, foilTop)
        drawRoundRect(foil, foilOrigin, foilSize, CornerRadius(foilWidth * 0.28f))
        val angle = tilt?.value ?: Offset.Zero
        val shineY = size.height * (0.5f + angle.y * 0.35f)
        val shine = Offset(foilX + foilWidth / 2f, shineY)
        clipRect(foilX, foilTop, foilX + foilWidth, foilTop + foilHeight) {
            rotate(35f + angle.x * 20f, pivot = shine) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.23f + abs(angle.x) * 0.18f), Color.Transparent),
                        startY = shineY - size.height * 0.24f, endY = shineY + size.height * 0.24f,
                    ),
                    Offset(foilX - size.height, shineY - size.height * 0.24f),
                    Size(size.height * 2f + foilWidth, size.height * 0.48f),
                )
            }
        }
        if (theme.isPolymer) {
            val window = Size(foilWidth * 0.72f, size.height * 0.26f)
            val origin = Offset(foilX + (foilWidth - window.width) / 2f, size.height * 0.45f)
            drawRoundRect(theme.primaryColor.copy(alpha = 0.50f), origin, window, CornerRadius(foilWidth * 0.24f))
            drawRoundRect(Color.White.copy(alpha = 0.30f), origin, window, CornerRadius(foilWidth * 0.24f), style = outline)
        }
        for (inset in listOf(margin, gap)) {
            val radius = (corner.toPx() - inset).coerceAtLeast(0f)
            drawRoundRect(
                Color.White.copy(alpha = if (inset == margin) 0.34f else 0.15f),
                Offset(inset, inset),
                Size((size.width - 2f * inset).coerceAtLeast(0f), (size.height - 2f * inset).coerceAtLeast(0f)),
                CornerRadius(radius), style = outline,
            )
        }
        drawPath(frame, Color.White.copy(alpha = 0.25f), style = outline)
        drawPath(cornerMarks, Color.White.copy(alpha = 0.19f), style = outline)
    }
}

private fun noteOrnament(size: Size, margin: Float, theme: BanknoteTheme): Path = Path().apply {
    val w = size.width
    val h = size.height
    val cx = w * 0.47f
    val cy = h * 0.52f
    val unit = h * 0.17f
    when (theme.motifType) {
        BanknoteMotif.OVAL, BanknoteMotif.LOTUS, BanknoteMotif.ROSETTE -> {
            val petals = when (theme.motifType) {
                BanknoteMotif.OVAL -> 2
                BanknoteMotif.LOTUS -> 6
                else -> 10
            }
            for (i in 0..4) {
                for (step in 0..96) {
                    val theta = step * PI / 48
                    val radius = (1.18 + i * 0.14 + 0.23 * cos(petals * theta))
                    val x = cx + unit * radius.toFloat() * cos(theta).toFloat() * 1.38f
                    val y = cy + unit * radius.toFloat() * sin(theta).toFloat()
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
        BanknoteMotif.ARCHES, BanknoteMotif.WAVES -> {
            val arch = theme.motifType == BanknoteMotif.ARCHES
            val xStep = unit * if (arch) 1.5f else 1.18f
            for (row in 0..2) {
                val y = h * (0.34f + row * 0.23f)
                for (column in 0..4) {
                    val center = w * 0.24f + xStep * (column - (row % 2) * 0.5f)
                    for (depth in 1..3) {
                        val r = depth * xStep / 6f
                        moveTo(center - r, y)
                        quadraticTo(center, y - r * if (arch) 2.65f else 1.65f, center + r, y)
                        if (arch) lineTo(center + r, y + unit * 0.8f)
                    }
                }
            }
        }
        BanknoteMotif.STAR, BanknoteMotif.STEPPE, BanknoteMotif.CORNERS, BanknoteMotif.LATTICE -> {
            val points = if (theme.motifType == BanknoteMotif.STAR) 16 else 8
            val inset = if (theme.motifType == BanknoteMotif.STEPPE) 0.38f else 0.63f
            val centers = when (theme.motifType) {
                BanknoteMotif.CORNERS -> listOf(Offset(w * 0.20f, cy * 0.7f), Offset(w * 0.81f, cy * 1.3f))
                BanknoteMotif.LATTICE -> (0..4).map { Offset(w * 0.23f + unit * it, cy + unit * (it % 2 - 0.5f)) }
                else -> listOf(Offset(cx, cy))
            }
            for (center in centers) {
                for (layer in 1..3) {
                    val radius = unit * (0.75f + layer * 0.30f)
                    for (i in 0 until points) {
                        val theta = i * 2.0 * PI / points
                        val r = radius * if (i % 2 == 0) 1f else inset
                        val x = center.x + cos(theta).toFloat() * r
                        val y = center.y + sin(theta).toFloat() * r
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            }
        }
        BanknoteMotif.TULIP -> {
            for (i in 1..3) {
                val span = unit * (0.8f + i * 0.26f)
                moveTo(cx, cy + span)
                quadraticTo(cx - span, cy, cx - span, cy - span)
                lineTo(cx - span * 0.42f, cy - span * 0.52f)
                lineTo(cx, cy - span * 1.1f)
                lineTo(cx + span * 0.42f, cy - span * 0.52f)
                lineTo(cx + span, cy - span)
                quadraticTo(cx + span, cy, cx, cy + span)
            }
        }
        BanknoteMotif.GUILLOCHE -> {
            val step = (theme.patternSeed % 5 + 5) * 0.008f + 0.033f
            val slope = (theme.patternSeed % 17 - 8) * 0.025f
            for (i in -4..4) {
                for (point in 0..48) {
                    val t = point / 48f
                    val x = margin * 2f + t * (w - margin * 4f)
                    val y = cy + h * (i * step + (t - 0.5f) * slope +
                        0.13f * sin(t * PI * (3f + step * 24f) + i * 0.55f).toFloat())
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
        }
    }
}
