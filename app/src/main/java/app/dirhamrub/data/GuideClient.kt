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

    /** Текст файла, если он разбирается (битый или чужой ответ не сохраняем). */
    suspend fun fetch(): Pair<String, CountryGuides>? = withContext(Dispatchers.IO) {
        recoverable {
            val reply = readHttp(URL_RAW)?.takeIf { it.code == HttpURLConnection.HTTP_OK }
                ?: return@recoverable null
            val text = String(reply.body, Charsets.UTF_8)
            CountryGuideParser.parse(text)?.let { text to it }
        }
    }
}
