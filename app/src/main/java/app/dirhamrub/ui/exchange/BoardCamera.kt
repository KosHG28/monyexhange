package app.dirhamrub.ui.exchange

import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dirhamrub.R
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.price.BoardParser
import app.dirhamrub.domain.price.BoardRate
import app.dirhamrub.domain.wallet.BankCard
import app.dirhamrub.domain.wallet.CardKind
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.ExchangeMath
import app.dirhamrub.domain.wallet.Exchanger
import app.dirhamrub.domain.wallet.OfficialRates
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.camera.CameraPermissionGate
import app.dirhamrub.ui.camera.OcrMapper
import app.dirhamrub.ui.main.signedPercent
import app.dirhamrub.ui.theme.AppTheme
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors

private const val TAG = "BoardCamera"

/** Затемнение под кнопками поверх видоискателя. */
private val Scrim = Color(0xB3101820)

/**
 * Камера в режиме «Обменник»: наводите на табло, строка вашей валюты ([give]) подсвечивается,
 * её курс покупки сразу пересчитывается в итоговую валюту и сравнивается с лучшей картой.
 * «Сохранить» — [onRecognized] (дальше — проверка в форме обменника), «Ценник» — [onPriceMode].
 */
@Composable
fun BoardCamera(
    give: Currency,
    local: Currency,
    home: Currency,
    cards: List<BankCard>,
    quote: WalletQuote,
    official: OfficialRates?,
    onRecognized: (Exchanger) -> Unit,
    onPriceMode: (() -> Unit)?,
    onBack: () -> Unit,
) {
    CameraPermissionGate(onBack = onBack) {
        BoardContent(give, local, home, cards, quote, official, onRecognized, onPriceMode, onBack)
    }
}

@Composable
private fun BoardContent(
    initialGive: Currency,
    local: Currency,
    home: Currency,
    cards: List<BankCard>,
    quote: WalletQuote,
    official: OfficialRates?,
    onRecognized: (Exchanger) -> Unit,
    onPriceMode: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var giveCode by rememberSaveable { mutableStateOf(initialGive.code) }
    val give = Currency.byCode(giveCode) ?: initialGive
    // Строки табло с последнего устойчивого кадра (одинаковые курсы два кадра подряд — не мерцают).
    val rates = remember { MutableStateFlow<List<BoardRate>>(emptyList()) }
    val shown by rates.collectAsState()
    val recognizer = remember { runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull() }
    val executor = remember { Executors.newSingleThreadExecutor { r -> Thread(r, "board-analysis").apply { isDaemon = true } } }
    val camera = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
        }
    }
    DisposableEffect(lifecycleOwner, recognizer) {
        if (recognizer != null) {
            var previous: List<BoardRate> = emptyList()
            val analyzer = MlKitAnalyzer(listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                try {
                    val lines = OcrMapper.map(result.getValue(recognizer)).flatMap { it.lines }
                    val now = BoardParser.parse(lines, local) { c -> official?.cross(c, local) }
                    // Показываем только то, что совпало с прошлым кадром: случайное прочтение не мигает.
                    val stable = now.filter { r -> previous.any { it.currency == r.currency && it.buy.compareTo(r.buy) == 0 } }
                    previous = now
                    if (stable.isNotEmpty() || now.isEmpty()) rates.value = stable
                } catch (e: Exception) {
                    Log.e(TAG, "Кадр табло не разобран", e)
                }
            }
            camera.setImageAnalysisAnalyzer(executor, analyzer)
            try {
                camera.bindToLifecycle(lifecycleOwner)
            } catch (e: Exception) {
                Log.e(TAG, "Камера не подключилась", e)
            }
        }
        onDispose {
            camera.clearImageAnalysisAnalyzer()
            camera.unbind()
            recognizer?.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = camera
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        val row = shown.firstOrNull { it.currency == give }
        // Подсветка строки вашей валюты (координаты анализа — уже в координатах превью).
        val highlight = colors.good
        Canvas(Modifier.fillMaxSize()) {
            val r = row?.box ?: return@Canvas
            val pad = 6.dp.toPx()
            drawRoundRect(
                color = highlight,
                topLeft = Offset(r.left - pad, r.top - pad),
                size = androidx.compose.ui.geometry.Size(r.width + pad * 2, r.height + pad * 2),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(3.dp.toPx()),
            )
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ModeChip(stringResource(R.string.camera_mode_back), selected = false, onClick = onBack)
                Spacer(Modifier.weight(1f))
                if (onPriceMode != null) ModeChip(stringResource(R.string.camera_mode_price), selected = false, onClick = onPriceMode)
                Spacer(Modifier.padding(start = 6.dp))
                ModeChip(stringResource(R.string.camera_mode_exchange), selected = true) {}
            }
            Spacer(Modifier.weight(1f))
            // Что несёте: найденные на табло валюты из частых — переключатель.
            val found = (EXCHANGE_GIVE + shown.map { it.currency }).filter { it != local }.distinct()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (c in found) {
                    val has = shown.any { it.currency == c }
                    ModeChip("${c.flag} ${c.code}" + if (has) " ✓" else "", selected = c == give) { giveCode = c.code }
                }
            }
            Spacer(Modifier.height(8.dp))
            Verdict(row, give, local, home, cards, quote, official, onRecognized)
        }
    }
}

@Composable
private fun Verdict(
    row: BoardRate?,
    give: Currency,
    local: Currency,
    home: Currency,
    cards: List<BankCard>,
    quote: WalletQuote,
    official: OfficialRates?,
    onRecognized: (Exchanger) -> Unit,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Column(
        Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.bg)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (row == null) {
            Text(stringResource(R.string.board_hint, give.code), style = type.body, color = colors.ink)
            return@Column
        }
        val ex = Exchanger(UUID.randomUUID().toString(), "", give, local, row.buy, row.sell, System.currentTimeMillis())
        val bought = homePerGive(give, home, cards, official)
        val cost = bought?.let(ex::homePerLocal)
        val bestCard = quote.quotes.filter { it.card.kind == CardKind.CARD }.minByOrNull { it.homePerUnit }
        Text(
            stringResource(R.string.board_buy, give.symbol, Numbers.rateShort(row.buy), local.code) +
                (ex.spreadPercent?.let { " · " + stringResource(R.string.exchange_spread_short, Numbers.percent(it)) } ?: ""),
            style = type.caption,
            color = colors.muted,
        )
        if (cost != null) {
            val unit = Numbers.unitFor(cost)
            Text(
                "${if (unit.compareTo(BigDecimal.ONE) == 0) "1" else Numbers.moneyWhole(unit)} ${local.code} = " +
                    "${Numbers.rateShort(cost.multiply(unit))} ${home.symbol}",
                style = type.title.copy(fontSize = 22.sp),
                color = colors.ink,
            )
        }
        if (cost != null && bestCard != null) {
            val vs = ExchangeMath.percentVs(cost, bestCard.homePerUnit)
            if (vs != null) {
                Text(
                    "💳 " + bestCard.card.name + ": " + stringResource(
                        if (vs.signum() > 0) R.string.board_card_better else R.string.board_exchange_better,
                        Numbers.percent(vs.abs()),
                    ),
                    style = type.body.copy(fontSize = 14.sp),
                    color = if (vs.signum() > 0) colors.warn else colors.good,
                )
            }
        }
        if (ex.expensive) Text(stringResource(R.string.exchange_spread_bad, Numbers.percent(ex.spreadPercent!!)), style = type.caption, color = colors.danger)
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accent)
                .clickable(role = Role.Button) { onRecognized(ex) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.board_save), style = type.bodyStrong, color = colors.onAccent)
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else Scrim)
            .then(if (selected) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong.copy(fontSize = 14.sp), color = if (selected) colors.onAccent else Color.White)
    }
}
