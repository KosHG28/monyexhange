package app.dirhamrub.data

import app.dirhamrub.domain.wallet.CbrParser
import app.dirhamrub.domain.wallet.OfficialRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * Курсы ЦБ РФ на сегодня. Сначала официальный XML (`cbr.ru`, кодировка windows-1251),
 * при ошибке — JSON-зеркало `cbr-xml-daily.ru`. Сеть приложению нужна только для этого.
 */
object CbrClient {
    private const val XML_URL = "https://www.cbr.ru/scripts/XML_daily.asp"
    private const val JSON_URL = "https://www.cbr-xml-daily.ru/daily_json.js"
    private const val TIMEOUT_MS = 10_000

    suspend fun fetch(): OfficialRates? = withContext(Dispatchers.IO) {
        runCatching { get(XML_URL)?.let { CbrParser.parseXml(String(it, Charset.forName("windows-1251"))) } }.getOrNull()
            ?: runCatching { get(JSON_URL)?.let { CbrParser.parseJson(String(it, Charsets.UTF_8)) } }.getOrNull()
    }

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
