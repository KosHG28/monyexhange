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
    private val CBR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Both children belong to the caller; the losing HTTP request is disconnected on cancellation. */
    suspend fun fetch(): Fetched? = withContext(Dispatchers.IO) {
        raceRates({ xml(XML_URL) }, { json(JSON_URL) })
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

    private suspend fun xml(url: String): OfficialRates? = recoverable {
        readHttp(url)?.takeIf { it.code == HttpURLConnection.HTTP_OK }?.body
            ?.let { CbrParser.parseXml(String(it, Charset.forName("windows-1251"))) }
    }

    private suspend fun json(url: String): OfficialRates? = recoverable {
        readHttp(url)?.takeIf { it.code == HttpURLConnection.HTTP_OK }?.body
            ?.let { CbrParser.parseJson(String(it, Charsets.UTF_8)) }
    }
}
