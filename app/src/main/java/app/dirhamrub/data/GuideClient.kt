package app.dirhamrub.data

import app.dirhamrub.domain.wallet.CountryGuideParser
import app.dirhamrub.domain.wallet.CountryGuides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Справочник «Чем платить в стране»: тот же файл, что встроен в приложение (`assets/countries.txt`),
 * но из ветки main репозитория — правка там доходит до пользователей без нового релиза.
 * Скачивается вместе с курсами ЦБ (по той же настройке «Обновлять курсы»); не скачался — остаётся прежний.
 */
object GuideClient {
    private const val URL_RAW = "https://raw.githubusercontent.com/KosHG28/monyexhange/main/app/src/main/assets/countries.txt"
    private const val TIMEOUT_MS = 10_000

    /** Текст файла, если он разбирается (битый или чужой ответ не сохраняем). */
    suspend fun fetch(): Pair<String, CountryGuides>? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(URL_RAW).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "KursPoCheku/1.0 (Android)")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val text = connection.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
                CountryGuideParser.parse(text)?.let { text to it }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
