package app.dirhamrub.domain

/** Карта, которой платим в ОАЭ. Названия для интерфейса — в ресурсах. */
enum class Card(val code: String, val symbol: String) {
    KZT("KZT", "₸"),
    CNY("CNY", "¥");

    val other: Card get() = if (this == KZT) CNY else KZT
}

/** Строка «линии метро» на главном экране. */
enum class Line { AED, RUB, CARD }
