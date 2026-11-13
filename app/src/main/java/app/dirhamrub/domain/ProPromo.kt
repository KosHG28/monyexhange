package app.dirhamrub.domain

/** Временная офлайн-активация Pro общим промокодом, до подключения оплаты. */
object ProPromo {
    fun accepts(code: String): Boolean = code.trim().equals("KIMCHI", ignoreCase = true)

    /** Неверный код не меняет уже сохранённые настройки, в том числе действующую Pro. */
    fun activate(settings: AppSettings, code: String): AppSettings =
        if (accepts(code)) settings.copy(pro = true) else settings
}
