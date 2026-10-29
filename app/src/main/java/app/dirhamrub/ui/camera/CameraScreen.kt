package app.dirhamrub.ui.camera

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.accessibility.AccessibilityManager
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dirhamrub.R
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Currency
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.motion
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Один фоновый поток анализа на всё приложение. Его не останавливаем при уходе с экрана:
 * запоздалый результат ML Kit иначе упал бы в остановленный executor.
 * Обычное статическое поле, а не `object` с `by lazy`: в release-сборке R8 такой объект
 * на первом кадре экрана камеры давал NullPointerException.
 */
private val analysisExecutor: Executor =
    Executors.newSingleThreadExecutor { r -> Thread(r, "price-analysis").apply { isDaemon = true } }

private const val TAG = "DirhamCamera"

/** Камера или распознавание не запустились: сообщение с текстом ошибки и кнопка «Назад». */
@Composable
private fun CameraError(error: Throwable?, onBack: () -> Unit) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.camera_error), style = type.title, color = colors.ink)
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(
                text = error?.let { "${it::class.java.simpleName}: ${it.message.orEmpty()}" }.orEmpty(),
                style = type.mono.copy(fontSize = 12.sp),
                color = colors.muted,
            )
            Spacer(Modifier.heightIn(min = 20.dp))
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.accent)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.camera_back), style = type.bodyStrong, color = colors.onAccent)
            }
        }
    }
}

/** Полупрозрачная подложка кнопок поверх видоискателя. */
private val Scrim = Color(0xB3101820)

/**
 * AR-камера ценников: живое превью, поверх каждой найденной цены — сумма в итоговой валюте
 * по выбранной карте и по курсу ЦБ.
 * [onToCalculator] получает цены в валюте трат (одну или отмеченные) и возвращает в калькулятор.
 */
@Composable
fun CameraScreen(
    wallet: Wallet,
    quote: WalletQuote,
    onSelectCard: (String) -> Unit,
    onToCalculator: (List<BigDecimal>) -> Unit,
    onBack: () -> Unit,
) {
    LightSystemBars()
    CameraPermissionGate(onBack = onBack) {
        CameraContent(wallet, quote, onSelectCard, onToCalculator, onBack)
    }
}

/** Над тёмным видоискателем значки системных панелей должны быть светлыми. */
@Composable
private fun LightSystemBars() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val status = controller?.isAppearanceLightStatusBars
        val nav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) controller?.isAppearanceLightStatusBars = status
            if (nav != null) controller?.isAppearanceLightNavigationBars = nav
        }
    }
}

@Composable
private fun CameraContent(
    wallet: Wallet,
    quote: WalletQuote,
    onSelectCard: (String) -> Unit,
    onToCalculator: (List<BigDecimal>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val mainTypeface = remember { ResourcesCompat.getFont(context, R.font.inter_tight_semibold) }
    val subTypeface = remember { ResourcesCompat.getFont(context, R.font.inter_tight_medium) }
    val metrics = remember(density) {
        with(density) {
            LabelMetrics(
                mainTextPx = 17.sp.toPx(),
                subTextPx = 11.5.sp.toPx(),
                padX = 12.dp.toPx(),
                padY = 6.dp.toPx(),
                lineGap = 2.dp.toPx(),
                gap = 3.dp.toPx(),
                margin = 8.dp.toPx(),
                radius = 12.dp.toPx(),
                tail = 6.dp.toPx(),
                mainTypeface = mainTypeface,
                subTypeface = subTypeface,
            )
        }
    }
    val pipeline = remember(metrics) { PricePipeline(metrics) }
    val selectedQuote = quote.of(wallet.selected?.id)
    val homePerUnit = selectedQuote?.homePerUnit ?: quote.official
    LaunchedEffect(pipeline, wallet.spend, wallet.home, homePerUnit, quote.official) {
        pipeline.setRates(wallet.spend, wallet.home, homePerUnit, quote.official.takeIf { selectedQuote != null })
    }

    // Камера и распознавание: анализ ~720p на отдельном потоке, всегда только последний кадр.
    val executor = analysisExecutor
    val recognizerResult = remember { runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) } }
    val recognizer = recognizerResult.getOrNull()
    if (recognizer == null) {
        // Вместо вылета — понятное сообщение; текст ошибки виден и попадает в logcat.
        val error = recognizerResult.exceptionOrNull()
        LaunchedEffect(error) { Log.e(TAG, "Распознавание текста не запустилось", error) }
        CameraError(error, onBack)
        return
    }
    var bindError by remember { mutableStateOf<Throwable?>(null) }
    val camera = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
            previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
        }
    }
    val analyzer = remember(pipeline) {
        MlKitAnalyzer(listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
            // Ошибка разбора одного кадра не должна ронять приложение: пропускаем кадр.
            try {
                pipeline.onText(result.getValue(recognizer), SystemClock.uptimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Кадр не разобран", e)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        try {
            camera.bindToLifecycle(lifecycleOwner)
        } catch (e: Exception) {
            Log.e(TAG, "Камера не подключилась", e)
            bindError = e
        }
        onDispose {
            camera.clearImageAnalysisAnalyzer()
            camera.unbind()
            // Распознаватель закрываем последним, когда анализатор уже снят.
            recognizer.close()
        }
    }

    val previewHolder = remember { arrayOfNulls<PreviewView>(1) }
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(paused, analyzer) {
        if (paused) {
            frozen = previewHolder[0]?.bitmap?.asImageBitmap()
            camera.clearImageAnalysisAnalyzer()
            pipeline.setPaused(true)
        } else {
            frozen = null
            pipeline.setPaused(false)
            camera.setImageAnalysisAnalyzer(executor, analyzer)
        }
    }

    var torch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(torch) { camera.enableTorch(torch) }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(800)
            focusPoint = null
        }
    }

    var sheet by remember { mutableStateOf<PriceLabel?>(null) }
    var basket by rememberSaveable { mutableStateOf(listOf<String>()) }

    val frame = pipeline.frames.collectAsState()
    val nothingFound by remember { derivedStateOf { frame.value.labels.isEmpty() } }
    val a11y = remember {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = camera
                    previewHolder[0] = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        frozen?.let { bitmap ->
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        val topReserve = with(density) { (WindowInsets.safeDrawing.getTop(this) + 72.dp.roundToPx()).toFloat() }
        val bottomReserve = with(density) { (WindowInsets.safeDrawing.getBottom(this) + 80.dp.roundToPx()).toFloat() }
        val hitSlop = with(density) { 8.dp.toPx() }
        PriceOverlay(
            frame = frame,
            metrics = metrics,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { pipeline.setViewport(it.width.toFloat(), it.height.toFloat(), topReserve, bottomReserve) }
                .pointerInput(paused) {
                    if (!paused) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                val zs = camera.zoomState.value
                                val min = zs?.minZoomRatio ?: 1f
                                val max = zs?.maxZoomRatio ?: 1f
                                camera.setZoomRatio(((zs?.zoomRatio ?: 1f) * zoom).coerceIn(min, max))
                            }
                        }
                    }
                }
                .pointerInput(paused) {
                    detectTapGestures { p ->
                        val hit = frame.value.labels.lastOrNull { l ->
                            (p.x >= l.pill.left - hitSlop && p.x <= l.pill.right + hitSlop &&
                                p.y >= l.pill.top - hitSlop && p.y <= l.pill.bottom + hitSlop) ||
                                (p.x >= l.box.left && p.x <= l.box.right && p.y >= l.box.top && p.y <= l.box.bottom)
                        }
                        if (hit != null) {
                            sheet = hit
                        } else if (!paused) {
                            val view = previewHolder[0] ?: return@detectTapGestures
                            val point = view.meteringPointFactory.createPoint(p.x, p.y)
                            camera.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build(),
                            )
                            focusPoint = p
                        }
                    }
                },
        )
        focusPoint?.let { FocusRing(it) }

        // Для TalkBack: невидимые кнопки поверх меток (Canvas сам по себе недоступен).
        if (a11y) {
            for (l in frame.value.labels) {
                val desc = stringResource(
                    if (l.isOld) R.string.camera_label_old_a11y else R.string.camera_label_a11y,
                    Numbers.money(l.value),
                    wallet.spend.code,
                    l.homeAmount?.let(Numbers::moneyWhole) ?: "—",
                    wallet.home.symbol,
                )
                with(density) {
                    Box(
                        Modifier
                            .offset { IntOffset(l.pill.left.roundToInt(), l.pill.top.roundToInt()) }
                            .size(l.pill.width().toDp(), l.pill.height().toDp())
                            .clearAndSetSemantics {
                                contentDescription = desc
                                role = Role.Button
                                onClick { sheet = l; true }
                            },
                    )
                }
            }
        }

        bindError?.let { CameraError(it, onBack) }

        TopControls(
            wallet = wallet,
            torch = torch,
            paused = paused,
            onBack = onBack,
            onSelectCard = onSelectCard,
            onTorch = { torch = !torch },
            onPause = { paused = !paused },
        )

        BottomStrip(
            basket = basket,
            hint = when {
                paused -> stringResource(R.string.camera_paused)
                nothingFound -> stringResource(R.string.camera_hint, "${wallet.spend.flag} ${wallet.spend.code}")
                else -> null
            },
            spend = wallet.spend,
            home = wallet.home,
            homePerUnit = homePerUnit,
            onToCalculator = { onToCalculator(basket.mapNotNull { Numbers.parse(it) }) },
            onClear = { basket = emptyList() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    sheet?.let { label ->
        PriceSheet(
            amount = label.value,
            spend = wallet.spend,
            home = wallet.home,
            isOld = label.isOld,
            quote = quote,
            selectedId = wallet.selected?.id,
            onToCalculator = {
                sheet = null
                onToCalculator(listOf(label.value))
            },
            onAdd = {
                basket = basket + label.value.toPlainString()
                sheet = null
            },
            onDismiss = { sheet = null },
        )
    }
}

/** Кольцо фокуса: сжимается к точке касания (без анимации, если она отключена). */
@Composable
private fun FocusRing(at: Offset) {
    var shown by remember(at) { mutableStateOf(false) }
    LaunchedEffect(at) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 1.4f, motion(200), label = "focus")
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White, radius = 28.dp.toPx() * scale, center = at, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun TopControls(
    wallet: Wallet,
    torch: Boolean,
    paused: Boolean,
    onBack: () -> Unit,
    onSelectCard: (String) -> Unit,
    onTorch: () -> Unit,
    onPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(R.drawable.ic_arrow_back, stringResource(R.string.camera_back), onBack)
        Spacer(Modifier.weight(1f))
        CardSwitch(wallet, onSelectCard)
        Spacer(Modifier.weight(1f))
        RoundButton(
            if (torch) R.drawable.ic_flash_on else R.drawable.ic_flash_off,
            stringResource(if (torch) R.string.camera_torch_off else R.string.camera_torch_on),
            onTorch,
        )
        Spacer(Modifier.width(8.dp))
        RoundButton(
            if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            stringResource(if (paused) R.string.camera_resume else R.string.camera_pause),
            onPause,
            highlighted = paused,
        )
    }
}

@Composable
private fun RoundButton(icon: Int, description: String, onClick: () -> Unit, highlighted: Boolean = false) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (highlighted) colors.accent else Scrim)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/** Карта для меток: показывает валюту и название выбранной, тап — следующая карта. Без карт — «ЦБ». */
@Composable
private fun CardSwitch(wallet: Wallet, onSelect: (String) -> Unit) {
    val colors = AppTheme.colors
    val card = wallet.selected
    val title = card?.name ?: stringResource(R.string.camera_official)
    val desc = stringResource(R.string.camera_card_switch, title)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .clickable(role = Role.Button, enabled = wallet.cards.size > 1) {
                val i = wallet.cards.indexOfFirst { it.id == card?.id }
                onSelect(wallet.cards[(i + 1) % wallet.cards.size].id)
            }
            .semantics { contentDescription = desc }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (card != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(card.currency.symbol.take(2), style = AppTheme.type.bodyStrong, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = AppTheme.type.bodyStrong,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    }
}

@Composable
private fun BottomStrip(
    basket: List<String>,
    hint: String?,
    spend: Currency,
    home: Currency,
    homePerUnit: BigDecimal?,
    onToCalculator: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (basket.isNotEmpty()) {
            val total = basket.fold(BigDecimal.ZERO) { acc, s -> acc.add(Numbers.parse(s) ?: BigDecimal.ZERO, MC) }
            val homeTotal = homePerUnit?.let { total.multiply(it, MC) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.camera_basket, basket.size, Numbers.money(total), spend.symbol),
                        style = type.label,
                        color = colors.muted,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.camera_basket_total, homeTotal?.let(Numbers::moneyWhole) ?: "—", home.symbol),
                        style = type.number.copy(fontSize = 20.sp, lineHeight = 26.sp),
                        color = colors.ink,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.camera_basket_clear),
                        tint = colors.muted,
                    )
                }
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accent)
                        .clickable(role = Role.Button, onClick = onToCalculator)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.camera_basket_to_calc), style = type.bodyStrong, color = colors.onAccent)
                }
            }
        } else if (hint != null) {
            Text(
                hint,
                style = type.bodyStrong,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Scrim)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
