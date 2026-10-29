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

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    fun read(context: Context): String? =
        file(context).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    fun clear(context: Context) {
        file(context).delete()
    }

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
