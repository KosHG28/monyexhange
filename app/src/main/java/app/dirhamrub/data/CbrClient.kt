package app.dirhamrub.data

import app.dirhamrub.domain.wallet.CbrParser
import app.dirhamrub.domain.wallet.OfficialRates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Курсы ЦБ РФ на сегодня (и на дату неделю назад): официальный XML (`cbr.ru`, кодировка windows-1251)
 * и JSON-зеркало `cbr-xml-daily.ru`. Сеть приложению нужна только для курсов (ещё — UnionPay).
 */
object CbrClient {
    /** Откуда пришли курсы — для настроек: «cbr.ru» или зеркало. */
    data class Fetched(val rates: OfficialRates, val source: String)

    const val SOURCE_CBR = "cbr.ru"
    const val SOURCE_MIRROR = "cbr-xml-daily.ru"

    private const val XML_URL = "https://www.cbr.ru/scripts/XML_daily.asp"
    private const val JSON_URL = "https://www.cbr-xml-daily.ru/daily_json.js"
    private const val ARCHIVE_URL = "https://www.cbr-xml-daily.ru/archive/%04d/%02d/%02d/daily_json.js"
    private const val TIMEOUT_MS = 10_000
    private const val OFFICIAL_GRACE_MS = 1_500L
    private val CBR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Запросы, которых не ждём: проигравший дорабатывает в фоне до своего таймаута, его ответ не нужен. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Курсы на сегодня: cbr.ru и зеркало спрашиваются одновременно — при плохой связи с cbr.ru (или из-за
     * границы) не ждём его 10–20 секунд, а берём зеркало. Официальному XML — фора [OFFICIAL_GRACE_MS]:
     * ответило сначала зеркало — ещё чуть-чуть ждём cbr.ru. Не ответил никто — null.
     */
    suspend fun fetch(): Fetched? {
        val result = CompletableDeferred<Fetched?>()
        val xmlDone = CompletableDeferred<Unit>()
        val failed = AtomicInteger(0)
        fun fail() {
            if (failed.incrementAndGet() == 2) result.complete(null)
        }
        background.launch {
            val r = xml(XML_URL)
            xmlDone.complete(Unit)
            if (r != null) result.complete(Fetched(r, SOURCE_CBR)) else fail()
        }
        background.launch {
            val r = json(JSON_URL) ?: return@launch fail()
            withTimeoutOrNull(OFFICIAL_GRACE_MS) { xmlDone.await() }
            result.complete(Fetched(r, SOURCE_MIRROR))
        }
        return result.await()
    }

    /**
     * Курсы, действовавшие на [date] (для стрелки «за неделю»). ЦБ сам отдаёт курс на выходной;
     * в архиве зеркала выходных нет — пробуем на день-три раньше.
     */
    suspend fun fetchOn(date: LocalDate): OfficialRates? = withContext(Dispatchers.IO) {
        xml("$XML_URL?date_req=${date.format(CBR_DATE)}")
            ?: (0L..3L).firstNotNullOfOrNull { back ->
                val d = date.minusDays(back)
                json(ARCHIVE_URL.format(d.year, d.monthValue, d.dayOfMonth))
            }
    }

    private fun xml(url: String): OfficialRates? =
        runCatching { get(url)?.let { CbrParser.parseXml(String(it, Charset.forName("windows-1251"))) } }.getOrNull()

    private fun json(url: String): OfficialRates? =
        runCatching { get(url)?.let { CbrParser.parseJson(String(it, Charsets.UTF_8)) } }.getOrNull()

    private fun get(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "KursPoCheku/1.0 (Android)")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else null
        } finally {
            connection.disconnect()
        }
    }
}
