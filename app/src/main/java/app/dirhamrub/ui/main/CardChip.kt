package app.dirhamrub.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dirhamrub.R
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardNetwork
import app.dirhamrub.ui.theme.AppTheme

@Composable
internal fun EmvChip(ink: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawEmvChip(ink, size) }
}

private fun DrawScope.drawEmvChip(ink: Color, size: Size) {
    val tint = ink.copy(alpha = 0.20f)
    val width = 1.dp.toPx().coerceAtMost(size.height * 0.11f)
    val inset = width / 2f
    drawRoundRect(
        color = tint,
        topLeft = Offset(inset, inset),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(4.dp.toPx().coerceAtMost(size.height / 4f)),
        style = Stroke(width),
    )
    val left = size.width * 0.26f
    val right = size.width * 0.74f
    val upper = size.height * 0.36f
    val lower = size.height * 0.64f
    drawLine(tint, Offset(left, upper), Offset(right, upper), width, StrokeCap.Round)
    drawLine(tint, Offset(left, lower), Offset(right, lower), width, StrokeCap.Round)
}

internal val PAYMENT_THUMBNAIL_WIDTH = 46.dp
internal val PAYMENT_THUMBNAIL_HEIGHT = 30.dp
internal val CARD_THUMBNAIL_RADIUS = 6.dp

/** The same chip and network mark are drawn at every size of the shared card face. */
@Composable
internal fun PlasticCardMarks(card: BankCard, ink: Color, modifier: Modifier = Modifier) {
    val mark = when (card.paymentNetwork) {
        CardNetwork.UNIONPAY, CardNetwork.UNIONPAY_FOREIGN -> stringResource(R.string.network_unionpay_short)
        CardNetwork.MIR -> stringResource(R.string.network_mir_short)
        CardNetwork.VISA_MC_FOREIGN -> stringResource(R.string.network_visa_mc_short)
        null -> card.currency.code
    }
    val measurer = rememberTextMeasurer()
    val caption = AppTheme.type.caption.copy(fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold)
    Box(modifier.drawWithCache {
        val text = measurer.measure(mark, style = caption, maxLines = 1, softWrap = false)
        val factor = minOf(size.width / PAYMENT_THUMBNAIL_WIDTH.toPx(), size.height / PAYMENT_THUMBNAIL_HEIGHT.toPx())
            .coerceIn(0f, 2.5f)
        val chip = Size(8.dp.toPx(), 6.dp.toPx())
        val left = 5.dp.toPx()
        val end = 3.dp.toPx()
        val bottom = 2.dp.toPx()
        onDrawBehind {
            if (factor > 0f) {
                translate(left * factor, (size.height - chip.height * factor) / 2f) {
                    scale(factor, pivot = Offset.Zero) { drawEmvChip(ink, chip) }
                }
                val x = (size.width - (text.size.width + end) * factor).coerceAtLeast(0f)
                val y = (size.height - (text.size.height + bottom) * factor).coerceAtLeast(0f)
                translate(x, y) {
                    scale(factor, pivot = Offset.Zero) { drawText(text, color = ink, topLeft = Offset.Zero) }
                }
            }
        }
    })
}
