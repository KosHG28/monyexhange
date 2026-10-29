package app.dirhamrub.domain

/** Карта, которой платим в ОАЭ. Названия для интерфейса — в ресурсах. */
enum class Card(val code: String, val symbol: String) {
    KZT("KZT", "₸"),
    CNY("CNY", "¥");

    val other: Card get() = if (this == KZT) CNY else KZT
}

/**
 * Валюта цен, которые вводятся и ищутся камерой: дирхамы в ОАЭ или тенге в Казахстане.
 * [code] — подпись к суммам («AED», «₸»).
 */
enum class Source(val code: String) {
    AED("AED"),
    KZT("₸"),
}

/** Строка «линии метро» на главном экране: валюта цен, рубли, валюта карты. */
enum class Line { SOURCE, RUB, CARD }
