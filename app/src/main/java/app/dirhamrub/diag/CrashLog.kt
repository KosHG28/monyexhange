package app.dirhamrub.diag

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Записывает необработанное исключение в файл, чтобы показать его при следующем запуске:
 * так отчёт о падении можно скопировать прямо с телефона, без adb.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val SEEN = "last_crash_seen.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    /** Новое падение, ещё не показанное (диалог при запуске). */
    fun read(context: Context): String? =
        file(context).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    /** Показали — убираем из «нового», но храним как последнее: его видно в настройках. */
    fun clear(context: Context) {
        val new = file(context)
        if (new.exists()) runCatching { new.copyTo(seen(context), overwrite = true) }
        new.delete()
    }

    /** Последнее падение — новое или уже показанное; null — падений не было. */
    fun last(context: Context): String? =
        read(context) ?: seen(context).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    /** «Очистить» в настройках. */
    fun forget(context: Context) {
        file(context).delete()
        seen(context).delete()
    }

    private fun seen(context: Context) = File(context.applicationContext.filesDir, SEEN)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private class Handler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                val version = context.packageManager.getPackageInfo(context.packageName, 0)
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                file(context).writeText(
                    buildString {
                        appendLine("Время: ${LocalDateTime.now()}")
                        appendLine("Версия: ${version.versionName} (${versionCode(version)})")
                        appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                        appendLine("Поток: ${thread.name}")
                        appendLine()
                        append(trace)
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }

        @Suppress("DEPRECATION")
        private fun versionCode(info: android.content.pm.PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
}
