package app.dirhamrub.domain.wallet

import java.time.LocalDate

/** Платёжная система по идентификатору приложения карты (AID). */
enum class EmvBrand { VISA, MASTERCARD, UNIONPAY, MIR, OTHER }

/**
 * Что видно по карте через NFC: платёжная система, страна банка (ISO 3166, числом: 643 — Россия) и валюта карты.
 * Номер карты и срок не разбираем и не храним — только эти три поля.
 */
data class EmvInfo(val brand: EmvBrand, val issuerCountry: Int?, val currency: Currency?) {
    /**
     * Сеть для справочника и курсов: UnionPay — российского банка или зарубежного (по стране банка), МИР,
     * Visa/Mastercard зарубежного банка. Visa/Mastercard российского банка за границей не работают — null.
     */
    val network: CardNetwork?
        get() = when (brand) {
            EmvBrand.UNIONPAY -> if (issuerCountry == RUSSIA) CardNetwork.UNIONPAY else CardNetwork.UNIONPAY_FOREIGN
            EmvBrand.MIR -> CardNetwork.MIR
            EmvBrand.VISA, EmvBrand.MASTERCARD -> if (issuerCountry == RUSSIA) null else CardNetwork.VISA_MC_FOREIGN
            EmvBrand.OTHER -> null
        }

    private companion object {
        const val RUSSIA = 643
    }
}

/** Один элемент BER-TLV: тег (целым), значение и вложенные элементы (у составных). */
class Tlv(val tag: Int, val value: ByteArray, val children: List<Tlv>)

/**
 * Разговор с банковской картой по стандарту EMV (бесконтактно, ISO 14443-4): команды и разбор ответов.
 * Порядок: SELECT PPSE → список приложений (AID) → SELECT AID (в ответе — PDOL) → GET PROCESSING OPTIONS
 * (в ответе — AFL, где лежат записи) → READ RECORD по AFL → в записях тег 9F42 (валюта карты) и 5F28 (страна банка).
 */
object Emv {
    /** SELECT «2PAY.SYS.DDF01» — каталог бесконтактных приложений карты. */
    val SELECT_PPSE: ByteArray = select("2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII))

    fun select(aid: ByteArray): ByteArray = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    /** READ RECORD [record] из файла [sfi]. */
    fun readRecord(sfi: Int, record: Int): ByteArray =
        byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), 0x00)

    /** GET PROCESSING OPTIONS с данными терминала по списку карты [pdol] (null — карта ничего не просит). */
    fun gpo(pdol: ByteArray?, date: LocalDate, random: Int): ByteArray {
        val data = pdolData(pdol, date, random)
        return byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, (data.size + 2).toByte(), 0x83.toByte(), data.size.toByte()) +
            data + byteArrayOf(0x00)
    }

    /** Ответ карты успешен: последние два байта — 90 00. */
    fun ok(response: ByteArray?): Boolean =
        response != null && response.size >= 2 && response[response.size - 2] == 0x90.toByte() && response[response.size - 1] == 0x00.toByte()

    /** Данные ответа без статуса 90 00. */
    fun body(response: ByteArray): ByteArray = if (response.size >= 2) response.copyOf(response.size - 2) else ByteArray(0)

    /** Разбор BER-TLV; битые данные — то, что успели разобрать. */
    fun parse(data: ByteArray): List<Tlv> {
        val out = ArrayList<Tlv>()
        var i = 0
        while (i < data.size) {
            val first = data[i].toInt() and 0xFF
            // Заполнители между элементами.
            if (first == 0x00 || first == 0xFF) {
                i++
                continue
            }
            var tag = first
            i++
            if (first and 0x1F == 0x1F) {
                while (i < data.size) {
                    val b = data[i].toInt() and 0xFF
                    tag = (tag shl 8) or b
                    i++
                    if (b and 0x80 == 0) break
                }
            }
            if (i >= data.size) break
            var len = data[i].toInt() and 0xFF
            i++
            if (len and 0x80 != 0) {
                val n = len and 0x7F
                if (n == 0 || n > 3 || i + n > data.size) break
                len = 0
                repeat(n) {
                    len = (len shl 8) or (data[i].toInt() and 0xFF)
                    i++
                }
            }
            if (len < 0 || i + len > data.size) break
            val value = data.copyOfRange(i, i + len)
            i += len
            val constructed = (tagFirstByte(tag) and 0x20) != 0
            out += Tlv(tag, value, if (constructed) parse(value) else emptyList())
        }
        return out
    }

    /** Первое вхождение тега на любой глубине. */
    fun find(items: List<Tlv>, tag: Int): Tlv? {
        for (t in items) {
            if (t.tag == tag) return t
            find(t.children, tag)?.let { return it }
        }
        return null
    }

    fun find(data: ByteArray, tag: Int): ByteArray? = find(parse(data), tag)?.value

    /** Приложения карты из ответа на SELECT PPSE: AID (тег 4F) по приоритету (тег 87, меньше — важнее). */
    fun aids(ppse: ByteArray): List<ByteArray> {
        val entries = ArrayList<Pair<ByteArray, Int>>()
        fun walk(items: List<Tlv>) {
            for (t in items) {
                if (t.tag == 0x61) {
                    val aid = t.children.firstOrNull { it.tag == 0x4F }?.value ?: continue
                    val priority = t.children.firstOrNull { it.tag == 0x87 }?.value?.firstOrNull()?.toInt()?.and(0x0F) ?: 15
                    entries += aid to priority
                } else {
                    walk(t.children)
                }
            }
        }
        walk(parse(ppse))
        return entries.sortedBy { it.second }.map { it.first }
    }

    /** Платёжная система по началу AID (RID — первые 5 байт). */
    fun brandOf(aid: ByteArray): EmvBrand {
        val rid = aid.take(5).joinToString("") { "%02X".format(it) }
        return when (rid) {
            "A000000003" -> EmvBrand.VISA
            "A000000004", "A000000005" -> EmvBrand.MASTERCARD
            "A000000333" -> EmvBrand.UNIONPAY
            "A000000658" -> EmvBrand.MIR
            else -> EmvBrand.OTHER
        }
    }

    /**
     * Данные терминала по списку карты (PDOL: теги с длинами без значений). Терминал — российский, сумма — ноль,
     * чтобы карта отдала записи; всё, чего не знаем, — нулями нужной длины.
     */
    fun pdolData(pdol: ByteArray?, date: LocalDate, random: Int): ByteArray {
        if (pdol == null) return ByteArray(0)
        val out = ArrayList<Byte>()
        var i = 0
        while (i < pdol.size) {
            var tag = pdol[i].toInt() and 0xFF
            i++
            if (tag and 0x1F == 0x1F) {
                while (i < pdol.size) {
                    val b = pdol[i].toInt() and 0xFF
                    tag = (tag shl 8) or b
                    i++
                    if (b and 0x80 == 0) break
                }
            }
            if (i >= pdol.size) break
            val len = pdol[i].toInt() and 0xFF
            i++
            val value = terminalValue(tag, date, random)
            for (k in 0 until len) out += if (value != null && k < value.size) value[k] else 0
        }
        return out.toByteArray()
    }

    private fun terminalValue(tag: Int, date: LocalDate, random: Int): ByteArray? = when (tag) {
        // Возможности бесконтактного терминала (TTQ): EMV-режим, онлайн, подпись не нужна.
        0x9F66 -> bytes(0xF0, 0x20, 0x40, 0x00)
        // Страна терминала и валюта операции — Россия, рубль.
        0x9F1A, 0x5F2A -> bytes(0x06, 0x43)
        // Дата операции, BCD ГГММДД.
        0x9A -> bytes(bcd(date.year % 100), bcd(date.monthValue), bcd(date.dayOfMonth))
        0x9C -> bytes(0x00)
        // Случайное число терминала.
        0x9F37 -> bytes(random ushr 24 and 0xFF, random ushr 16 and 0xFF, random ushr 8 and 0xFF, random and 0xFF)
        // Тип терминала: торговый, офлайн с онлайном.
        0x9F35 -> bytes(0x22)
        else -> null
    }

    /** Где лежат записи (AFL) из ответа на GET PROCESSING OPTIONS: тройки (файл, первая запись, последняя). */
    fun afl(gpoResponse: ByteArray): List<Triple<Int, Int, Int>> {
        val items = parse(gpoResponse)
        val raw = find(items, 0x94)?.value
            ?: items.firstOrNull { it.tag == 0x80 }?.value?.let { if (it.size > 2) it.copyOfRange(2, it.size) else null }
            ?: return emptyList()
        val out = ArrayList<Triple<Int, Int, Int>>()
        var i = 0
        while (i + 4 <= raw.size) {
            val sfi = (raw[i].toInt() and 0xFF) shr 3
            val first = raw[i + 1].toInt() and 0xFF
            val last = raw[i + 2].toInt() and 0xFF
            if (sfi in 1..30 && first in 1..last) out += Triple(sfi, first, last)
            i += 4
        }
        return out
    }

    /**
     * Итог по ответам карты: [aid] — выбранное приложение, [responses] — ответы на SELECT AID, GPO и READ RECORD
     * (без статуса). Валюта карты — тег 9F42, страна банка — 5F28, оба числом ISO.
     */
    fun info(aid: ByteArray, responses: List<ByteArray>): EmvInfo {
        var currency: Currency? = null
        var country: Int? = null
        for (r in responses) {
            val items = parse(r)
            if (currency == null) currency = find(items, 0x9F42)?.value?.let { currencyOf(bcdNumber(it)) }
            if (country == null) country = find(items, 0x5F28)?.value?.let { bcdNumber(it) }
        }
        return EmvInfo(brandOf(aid), country, currency)
    }

    /** Валюта приложения по числовому коду ISO 4217 (643 — рубль, 398 — тенге). */
    fun currencyOf(numeric: Int?): Currency? {
        numeric ?: return null
        val code = java.util.Currency.getAvailableCurrencies().firstOrNull { it.numericCode == numeric }?.currencyCode ?: return null
        return Currency.entries.firstOrNull { it.name == code }
    }

    /** «06 43» → 643: коды стран и валют в EMV записаны двоично-десятично. */
    fun bcdNumber(value: ByteArray): Int? {
        var n = 0
        for (b in value) {
            val hi = (b.toInt() and 0xF0) shr 4
            val lo = b.toInt() and 0x0F
            if (hi > 9 || lo > 9) return null
            n = n * 100 + hi * 10 + lo
        }
        return n
    }

    private fun tagFirstByte(tag: Int): Int {
        var t = tag
        while (t > 0xFF) t = t ushr 8
        return t
    }

    private fun bcd(n: Int): Int = ((n / 10) shl 4) or (n % 10)

    private fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }
}
