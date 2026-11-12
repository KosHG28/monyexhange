package app.dirhamrub.data

/**
 * Покупка Pro — разовая, навсегда. Сейчас подключена тестовая заглушка ([StubProBilling]): оплаты нет,
 * покупка сразу «проходит». Настоящая оплата (RuStore) встанет на её место, не меняя экранов.
 */
interface ProBilling {
    /** Цена для кнопки «Купить за …». */
    val price: String

    /** Тестовая заглушка: показываем пометку и даём отменить покупку. */
    val isTest: Boolean

    /** Купить Pro; true — куплено. */
    suspend fun purchase(): Boolean
}

object StubProBilling : ProBilling {
    override val price: String = "59 ₽"
    override val isTest: Boolean = true

    override suspend fun purchase(): Boolean = true
}
