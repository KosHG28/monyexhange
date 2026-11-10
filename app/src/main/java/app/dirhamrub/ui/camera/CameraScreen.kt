package app.dirhamrub.ui.camera

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.accessibility.AccessibilityManager
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dirhamrub.R
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Wallet
import app.dirhamrub.domain.wallet.WalletQuote
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.LocalReduceMotion
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.math.BigDecimal
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
internal val Scrim = Color(0xB3101820)

/** Через сколько удержания пальца метки скрываются (короткий тап не мигает). */
private const val REVEAL_DELAY_MS = 220L
private const val HOLD_HINT_MS = 4000L
private const val MODE_TOAST_MS = 1600L

/** Высота затемнения под верхними кнопками (без системной панели). */
private val TOP_SCRIM = 96.dp

/** Кнопки зума: те, что поддерживает камера. */
internal val ZOOM_PRESETS = listOf(1f, 2f, 4f)

/**
 * AR-камера ценников: живое превью, поверх каждой найденной цены — сумма в итоговой валюте
 * по выбранной карте и по курсу ЦБ.
 * [onToCalculator] получает цены в валюте трат (одну или отмеченные) и возвращает в калькулятор.
 */
@Composable
fun CameraScreen(
    wallet: Wallet,
    quote: WalletQuote,
    embedded: Boolean,
    onEmbeddedChange: (Boolean) -> Unit,
    onSelectCard: (String) -> Unit,
    onToCalculator: (List<BigDecimal>) -> Unit,
    onBack: () -> Unit,
    /** Переключиться на режим «Обменник» (табло); null — переключателя нет. */
    onExchangeMode: (() -> Unit)? = null,
) {
    LightSystemBars()
    CameraPermissionGate(onBack = onBack) {
        CameraContent(wallet, quote, embedded, onEmbeddedChange, onSelectCard, onToCalculator, onBack, onExchangeMode)
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
    embedded: Boolean,
    onEmbeddedChange: (Boolean) -> Unit,
    onSelectCard: (String) -> Unit,
    onToCalculator: (List<BigDecimal>) -> Unit,
    onBack: () -> Unit,
    onExchangeMode: (() -> Unit)?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val mainTypeface = remember { ResourcesCompat.getFont(context, R.font.inter_tight_semibold) }
    val metrics = remember(density) {
        with(density) {
            LabelMetrics(
                mainTextPx = 18.sp.toPx(),
                subTextPx = 14.sp.toPx(),
                padX = 12.dp.toPx(),
                padY = 7.dp.toPx(),
                gap = 4.dp.toPx(),
                margin = 8.dp.toPx(),
                radius = 12.dp.toPx(),
                tail = 6.dp.toPx(),
                mainTypeface = mainTypeface,
                subTypeface = mainTypeface,
            )
        }
    }
    val pipeline = remember(metrics) { PricePipeline(metrics) }
    val selectedQuote = quote.of(wallet.selected?.id)
    val homePerUnit = selectedQuote?.homePerUnit ?: quote.official
    // Сравнение с самой дешёвой из остальных карт — для карточки фокуса (как на главном экране).
    val comparison = quote.compare(wallet.selected?.id)?.takeIf { Numbers.percent(it.percent) != "0" }
    LaunchedEffect(pipeline, embedded) { pipeline.setEmbedded(embedded) }
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
        val mlKit = MlKitAnalyzer(listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
            // Ошибка разбора одного кадра не должна ронять приложение: пропускаем кадр.
            try {
                pipeline.onText(result.getValue(recognizer), SystemClock.uptimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Кадр не разобран", e)
            }
        }
        // Перед распознаванием — цвета ценников вокруг найденных цен, для вписывания сумм.
        TagColorAnalyzer(mlKit, pipeline)
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

    // Текущий зум — для кнопок 1× 2× 4× (щипок тоже его меняет).
    var zoom by remember { mutableStateOf<ZoomState?>(null) }
    DisposableEffect(camera, lifecycleOwner) {
        val observer = Observer<ZoomState> { zoom = it }
        camera.zoomState.observe(lifecycleOwner, observer)
        onDispose { camera.zoomState.removeObserver(observer) }
    }

    // Название режима коротко всплывает под кнопками после переключения.
    var modeToast by remember { mutableStateOf<Boolean?>(null) }
    var modeToastKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(modeToastKey) {
        if (modeToastKey > 0) {
            delay(MODE_TOAST_MS)
            modeToast = null
        }
    }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(800)
            focusPoint = null
        }
    }

    var sheet by remember { mutableStateOf<PriceLabel?>(null) }
    val reveal = remember { mutableStateOf(false) }
    var basket by rememberSaveable { mutableStateOf(listOf<String>()) }

    var bottomHeight by remember { mutableIntStateOf(0) }

    val frame = pipeline.frames.collectAsState()
    val nothingFound by remember { derivedStateOf { frame.value.labels.isEmpty() } }
    // Подсказка «удерживайте — оригинал»: один раз за сеанс камеры, на несколько секунд после первой цены.
    var holdHint by remember { mutableStateOf(false) }
    var holdHintShown by remember { mutableStateOf(false) }
    val hasLabels = !nothingFound
    LaunchedEffect(hasLabels, embedded) {
        if (hasLabels && embedded && !holdHintShown) {
            holdHintShown = true
            holdHint = true
            delay(HOLD_HINT_MS)
            holdHint = false
        }
    }
    // Карточка фокуса перерисовывается только когда меняется её содержимое, а не на каждом кадре OCR.
    val focus by remember {
        derivedStateOf {
            frame.value.focus?.let { FocusInfo(it.id, it.value, it.isOld, it.unsure, it.homeAmount, it.officialAmount) }
        }
    }
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

        // Сверху — ряд кнопок, снизу — зум, карточка цены в фокусе и список: метки туда не ставим.
        // Низ меряется по самой высокой раскладке за сеанс: метки не прыгают, когда карточка появляется.
        val topReserve = with(density) { (WindowInsets.safeDrawing.getTop(this) + 64.dp.roundToPx()).toFloat() }
        val bottomReserve = with(density) {
            maxOf(bottomHeight, WindowInsets.safeDrawing.getBottom(this) + 120.dp.roundToPx()).toFloat()
        }
        val hitSlop = with(density) { 8.dp.toPx() }
        var overlaySize by remember { mutableStateOf(IntSize.Zero) }
        LaunchedEffect(overlaySize, topReserve, bottomReserve) {
            if (overlaySize != IntSize.Zero) {
                pipeline.setViewport(overlaySize.width.toFloat(), overlaySize.height.toFloat(), topReserve, bottomReserve)
            }
        }
        if (!paused) Reticle(topReserve, bottomReserve)
        PriceOverlay(
            frame = frame,
            metrics = metrics,
            reveal = reveal,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize = it }
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
                    detectTapGestures(
                        // Удержание пальца — настоящие ценники без меток (короткий тап не мигает).
                        onPress = {
                            coroutineScope {
                                val show = launch {
                                    delay(REVEAL_DELAY_MS)
                                    reveal.value = true
                                }
                                tryAwaitRelease()
                                show.cancel()
                                reveal.value = false
                            }
                        },
                    ) { p ->
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

        // Лёгкое затемнение под кнопками: читаются на любом фоне.
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { WindowInsets.safeDrawing.getTop(this).toDp() } + TOP_SCRIM)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
        )
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopControls(
                wallet = wallet,
                viaUnionPay = quote.viaUnionPay,
                torch = torch,
                paused = paused,
                embedded = embedded,
                onBack = onBack,
                onSelectCard = onSelectCard,
                onTorch = { torch = !torch },
                onPause = { paused = !paused },
                onToggleMode = {
                    onEmbeddedChange(!embedded)
                    modeToast = !embedded
                    modeToastKey++
                },
            )
            // Режим камеры: ценники или табло обменника.
            if (onExchangeMode != null) ModeSwitch(onExchangeMode)
            // Короткие подсказки — под кнопками, а не над карточкой: низ экрана не прыгает.
            TopToast(
                text = when {
                    modeToast == true -> stringResource(R.string.camera_mode_now_embed)
                    modeToast == false -> stringResource(R.string.camera_mode_now_bubbles)
                    holdHint -> stringResource(R.string.camera_hold_hint)
                    else -> null
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { if (it.height > bottomHeight) bottomHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!paused) {
                ZoomChips(zoom, onZoom = { camera.setZoomRatio(it) }, modifier = Modifier.align(Alignment.End))
            }
            // Подробности — у одной цены, ближайшей к центру: на самом кадре только короткие плашки.
            // Карточка выезжает снизу с лёгкой пружинкой; пока уезжает — показывает последнюю цену.
            val reduceMotion = LocalReduceMotion.current
            val lastFocus = remember { arrayOfNulls<FocusInfo>(1) }
            focus?.let { lastFocus[0] = it }
            AnimatedVisibility(
                visible = focus != null,
                enter = if (reduceMotion) {
                    EnterTransition.None
                } else {
                    slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it / 2 } + fadeIn(tween(180))
                },
                exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)),
            ) {
                val info = focus ?: lastFocus[0] ?: return@AnimatedVisibility
                FocusCard(
                    info = info,
                    spend = wallet.spend,
                    home = wallet.home,
                    cardName = wallet.selected?.name,
                    cardQuote = selectedQuote,
                    viaUnionPay = quote.viaUnionPay,
                    comparison = comparison,
                    onOpen = { sheet = frame.value.labels.firstOrNull { it.id == info.id } },
                    onAdd = { basket = basket + info.value.toPlainString() },
                    onSelectCard = onSelectCard,
                )
            }
            BottomStrip(
                basket = basket,
                hint = when {
                    paused && nothingFound -> stringResource(R.string.camera_paused)
                    nothingFound -> stringResource(R.string.camera_hint, "${wallet.spend.flag} ${wallet.spend.code}")
                    else -> null
                },
                spend = wallet.spend,
                home = wallet.home,
                homePerUnit = homePerUnit,
                onToCalculator = { onToCalculator(basket.mapNotNull { Numbers.parse(it) }) },
                onClear = { basket = emptyList() },
            )
        }
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
