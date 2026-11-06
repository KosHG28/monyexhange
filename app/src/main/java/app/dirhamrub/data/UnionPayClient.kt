package app.dirhamrub.data

import app.dirhamrub.domain.wallet.UnionPayParser
import app.dirhamrub.domain.wallet.UnionPayRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Курсы UnionPay — файл, который загружает их страница курсов (`m.unionpayintl.com/ru/rate/`):
 * `jfimg/ГГГГММДД.json`, ~140 КБ (сжатый — меньше). Это не официальный API: если файл переедет,
 * приложение останется на сохранённых курсах, а без них — на ЦБ и чеках.
 */
object UnionPayClient {
    private const val URL_FORMAT = "https://m.unionpayintl.com/jfimg/%s.json"
    private const val TIMEOUT_MS = 10_000
    private const val DAYS_BACK = 6L
    private val BEIJING = ZoneId.of("Asia/Shanghai")

    /**
     * Последние курсы: файл за сегодня по пекинскому времени, а если его нет (выходные — действует
     * пятничный курс, праздники, ещё не вышел) — за предыдущие дни. Сетевая ошибка — null сразу.
     */
    suspend fun fetch(): UnionPayRates? = withContext(Dispatchers.IO) {
        val today = LocalDate.now(BEIJING)
        for (back in 0L..DAYS_BACK) {
            val day = today.minusDays(back).format(DateTimeFormatter.BASIC_ISO_DATE)
            val (code, body) = get(URL_FORMAT.format(day)) ?: return@withContext null
            when (code) {
                // Файл не официальный: что бы в нём ни пришло, разбор не должен ронять приложение.
                HttpURLConnection.HTTP_OK -> return@withContext runCatching { UnionPayParser.parse(String(body, Charsets.UTF_8)) }.getOrNull()
                HttpURLConnection.HTTP_NOT_FOUND -> continue
                else -> return@withContext null
            }
        }
        null
    }

    private fun get(url: String): Pair<Int, ByteArray>? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "KursPoCheku/1.0 (Android)")
            val code = connection.responseCode
            code to (if (code == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
