package app.dirhamrub.data

import app.dirhamrub.domain.wallet.CbrParser
import app.dirhamrub.domain.wallet.OfficialRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Курсы ЦБ РФ на сегодня (и на дату неделю назад). Сначала официальный XML (`cbr.ru`, кодировка windows-1251),
 * при ошибке — JSON-зеркало `cbr-xml-daily.ru`. Сеть приложению нужна только для этого.
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
    private val CBR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    suspend fun fetch(): Fetched? = withContext(Dispatchers.IO) {
        xml(XML_URL)?.let { Fetched(it, SOURCE_CBR) } ?: json(JSON_URL)?.let { Fetched(it, SOURCE_MIRROR) }
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
