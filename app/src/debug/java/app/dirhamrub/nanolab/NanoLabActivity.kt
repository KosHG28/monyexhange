package app.dirhamrub.nanolab

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.dirhamrub.ui.theme.AppTheme
import app.dirhamrub.ui.theme.DirhamTheme
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Лаборатория Gemini Nano — только в отладочной сборке (отдельный значок «Nano-тест»).
 * Проверяет, есть ли на телефоне модель (AICore), и как она по-русски справляется с двумя задачами
 * будущего помощника: понять, о чём вопрос (выбрать расчёт), и пересказать готовые факты.
 * Числа в фактах — примерные, как в демо; сама модель ничего не считает.
 * Строки здесь не вынесены в strings.xml: экран для разработки, в релиз не попадает.
 */
class NanoLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { DirhamTheme { NanoLab() } }
    }
}

/** Вопрос, какой расчёт он должен выбрать, и факты, которые наш код дал бы для ответа. */
private data class Probe(val question: String, val intent: String, val facts: String)

private val PROBES = listOf(
    Probe(
        "Какой картой платить за 167 дирхамов?", "compare_cards",
        "Карта в юанях: 3 749 ₽ (+2,2 % к ЦБ). Наличные AED: 3 791 ₽ (+3,3 %). Карта в тенге: 3 881 ₽ (+5,8 %). По курсу ЦБ: 3 669 ₽.",
    ),
    Probe(
        "выгодно менять доллары в аэропорту?", "exchange",
        "Доллары куплены по 92,10 ₽. Чтобы обмен был не хуже оплаты картой в юанях (22,45 ₽ за AED), за 1 USD нужно получить не меньше 4,10 AED.",
    ),
    Probe(
        "сколько оставить на чай в кафе в дубае если счет 240 aed", "tips",
        "В ОАЭ в кафе часто уже включён сервис 10 %. Если не включён, обычно оставляют 10–15 %: 24–36 AED ≈ 540–810 ₽ картой в юанях.",
    ),
    Probe(
        "Что дешевле — снять наличные в банкомате или платить картой?", "atm_vs_card",
        "Снятие: за 1 000 AED списали 22 950 ₽, это 22,95 ₽ за AED. Карта в юанях: 22,45 ₽ за AED. Платить картой дешевле на 2,2 %, на 1 000 AED — на 500 ₽.",
    ),
    Probe(
        "насколько банк дороже цб", "overpay",
        "Карта в юанях +2,2 %, карта в тенге +5,8 %, наличные +3,3 % к курсу ЦБ 21,97 ₽ за AED.",
    ),
    Probe("курс дирхама сегодня", "rate", "ЦБ РФ на 9 ноября: 1 AED = 21,97 ₽, за неделю +0,4 %."),
    Probe("Сколько рублей 1500 дирхам", "convert", "1 500 AED = 33 675 ₽ картой в юанях, 32 955 ₽ по курсу ЦБ."),
    Probe(
        "когда лучше пополнить карту в юанях", "topup",
        "Чек пополнения 3 ноября: 15 000 ₽ → 1 470 CNY, 10,20 ₽ за юань. ЦБ сегодня 10,05 ₽. При пополнении банк берёт ≈ +1,5 % к ЦБ.",
    ),
    Probe("посоветуй хороший ресторан", "unknown", ""),
    Probe("сколько будет 2+2", "unknown", ""),
)

private const val INTENTS = "compare_cards, convert, rate, overpay, atm_vs_card, exchange, tips, topup, unknown"

private const val ROUTER = """Ты классификатор вопросов приложения «Курс по чеку» (курсы валют и оплата картами в поездке).
Ответь ОДНИМ словом из списка: $INTENTS.
compare_cards — какой картой или чем платить; convert — сколько рублей стоит сумма; rate — курс валюты;
overpay — насколько банк дороже ЦБ; atm_vs_card — снять наличные или платить картой; exchange — менять ли деньги в обменнике;
tips — чаевые и сервис; topup — когда пополнять карту; unknown — всё остальное.
Только слово, без пояснений."""

private const val ANSWER = """Ты помощник приложения «Курс по чеку». Отвечай по-русски, одним-двумя короткими предложениями.
Используй только числа из раздела «Факты». Ничего не считай сам и не придумывай курсы, комиссии и цены.
Если фактов нет или вопрос не о деньгах в поездке, ответь: «Этого я не знаю. Спросите про карты, курс или чаевые»."""

private fun statusName(s: Int): String = when (s) {
    FeatureStatus.AVAILABLE -> "AVAILABLE — модель готова"
    FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE — можно скачать"
    FeatureStatus.DOWNLOADING -> "DOWNLOADING — скачивается"
    FeatureStatus.UNAVAILABLE -> "UNAVAILABLE — телефон не поддерживает"
    else -> "неизвестно ($s)"
}

private fun Throwable.describe(): String =
    if (this is GenAiException) "${javaClass.simpleName} code=$errorCode aicore=$aiCoreErrorCode: $message" else "${javaClass.simpleName}: $message"

/** Один запрос к модели: системная инструкция отдельно, если модель её поддерживает, иначе — в начале текста. */
private suspend fun GenerativeModel.ask(system: String, user: String, sys: Boolean, maxTokens: Int, temp: Float): String {
    val request = if (sys) {
        generateContentRequest(SystemInstruction(system), TextPart(user)) {
            temperature = temp
            maxOutputTokens = maxTokens
        }
    } else {
        generateContentRequest(TextPart("$system\n\n$user")) {
            temperature = temp
            maxOutputTokens = maxTokens
        }
    }
    return generateContent(request).candidates.firstOrNull()?.text?.trim().orEmpty()
}

@Composable
private fun NanoLab() {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember { runCatching { Generation.getClient() } }
    DisposableEffect(Unit) { onDispose { model.getOrNull()?.close() } }
    val log = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf<Job?>(null) }
    var working by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(false) }
    var free by remember { mutableStateOf("") }

    fun add(line: String) = log.add(line)
    fun withModel(block: suspend (GenerativeModel) -> Unit) {
        val m = model.getOrElse {
            add("✗ Клиент не создан: ${it.describe()}")
            return
        }
        if (busy?.isActive == true) return
        busy = scope.launch {
            working = true
            try {
                block(m)
            } catch (e: Exception) {
                add("✗ ${e.describe()}")
            } finally {
                working = false
            }
        }
    }

    LaunchedEffect(Unit) {
        add("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        withModel { m ->
            add("Статус: ${statusName(m.checkStatus())}")
            add("Модель: " + runCatching { m.getBaseModelName() }.getOrElse { "— (${it.describe()})" })
            add("Лимит токенов: " + runCatching { m.getTokenLimit().toString() }.getOrElse { "— (${it.describe()})" })
            systemPrompt = runCatching { m.isSystemPromptAvailable() }.getOrDefault(false)
            add("Системная инструкция: ${if (systemPrompt) "есть" else "нет — пишем в начало запроса"}")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Text("Gemini Nano — проверка", style = AppTheme.type.title, color = colors.ink, modifier = Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("Статус", Modifier.weight(1f)) {
                withModel { m -> add("Статус: ${statusName(m.checkStatus())}") }
            }
            LabButton("Скачать", Modifier.weight(1f)) {
                withModel { m ->
                    m.download().collect { st ->
                        when (st) {
                            is DownloadStatus.DownloadStarted -> add("Загрузка: начата, ${st.bytesToDownload / 1_000_000} МБ")
                            is DownloadStatus.DownloadProgress -> add("Загрузка: ${st.totalBytesDownloaded / 1_000_000} МБ")
                            is DownloadStatus.DownloadFailed -> add("✗ Загрузка: ${st.e.describe()}")
                            else -> add("Загрузка: готово")
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabButton("10 вопросов", Modifier.weight(1f)) {
                withModel { m ->
                    var hits = 0
                    PROBES.forEachIndexed { i, p ->
                        val t0 = System.nanoTime()
                        val intent = m.ask(ROUTER, p.question, systemPrompt, 8, 0f)
                        val t1 = System.nanoTime()
                        val user = "Вопрос: ${p.question}\nФакты: ${p.facts.ifEmpty { "нет" }}"
                        val answer = m.ask(ANSWER, user, systemPrompt, 160, 0.2f)
                        val t2 = System.nanoTime()
                        val ok = intent.lowercase().contains(p.intent)
                        if (ok) hits++
                        add(
                            "${i + 1}. ${p.question}\n" +
                                "   тема: «$intent» ${if (ok) "✓" else "✗ ждали ${p.intent}"} · ${(t1 - t0) / 1_000_000} мс\n" +
                                "   ответ (${(t2 - t1) / 1_000_000} мс): $answer",
                        )
                    }
                    add("Итого темы: $hits из ${PROBES.size}")
                }
            }
            LabButton("Копировать", Modifier.weight(1f)) {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("nano", log.joinToString("\n")))
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = free,
                onValueChange = { free = it },
                textStyle = AppTheme.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (free.isEmpty()) Text("Свой вопрос…", style = AppTheme.type.body, color = colors.muted)
                    inner()
                },
            )
            LabButton("Спросить", Modifier.padding(start = 8.dp)) {
                val q = free.trim()
                if (q.isNotEmpty()) {
                    withModel { m ->
                        val t0 = System.nanoTime()
                        val intent = m.ask(ROUTER, q, systemPrompt, 8, 0f)
                        val answer = m.ask(ANSWER, "Вопрос: $q\nФакты: нет", systemPrompt, 160, 0.2f)
                        add("? $q\n   тема: «$intent» · ответ без фактов: $answer · ${(System.nanoTime() - t0) / 1_000_000} мс")
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(log) { line -> Text(line, style = AppTheme.type.caption, color = colors.ink) }
            if (working) item { Text("Работаем…", style = AppTheme.type.caption, color = colors.muted) }
        }
    }
}

@Composable
private fun LabButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.bodyStrong, color = AppTheme.colors.onAccent)
    }
}
