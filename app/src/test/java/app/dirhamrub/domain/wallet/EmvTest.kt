package app.dirhamrub.domain.wallet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EmvTest {
    private fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Элемент TLV с короткой длиной: тег (hex) + значение. */
    private fun tlv(tag: String, value: ByteArray): ByteArray = hex(tag) + byteArrayOf(value.size.toByte()) + value

    @Test
    fun `каталог приложений — AID по приоритету`() {
        // 6F → 84 «2PAY.SYS.DDF01», A5 → BF0C → два 61: UnionPay (приоритет 2) и Visa (приоритет 1).
        val up = tlv("61", tlv("4F", hex("A000000333010101")) + tlv("87", hex("02")))
        val visa = tlv("61", tlv("4F", hex("A0000000031010")) + tlv("87", hex("01")) + tlv("50", "VISA".toByteArray()))
        val ppse = tlv("6F", tlv("84", "2PAY.SYS.DDF01".toByteArray()) + tlv("A5", tlv("BF0C", up + visa)))
        val aids = Emv.aids(ppse)
        assertEquals(2, aids.size)
        assertEquals(EmvBrand.VISA, Emv.brandOf(aids[0]))
        assertEquals(EmvBrand.UNIONPAY, Emv.brandOf(aids[1]))
        // Ответ с длиной в два байта (81 xx) тоже разбирается.
        val long = hex("70 81 05 9F42 02 0398")
        assertEquals(Currency.KZT, Emv.info(hex("A0000000031010"), listOf(long)).currency)
    }

    @Test
    fun `бренды по RID`() {
        assertEquals(EmvBrand.MASTERCARD, Emv.brandOf(hex("A0000000041010")))
        assertEquals(EmvBrand.MIR, Emv.brandOf(hex("A0000006581010")))
        assertEquals(EmvBrand.OTHER, Emv.brandOf(hex("A0000000651010")))
    }

    @Test
    fun `данные терминала по PDOL`() {
        // 9F66 04, 9F02 06, 9F1A 02, 9A 03, 9F37 04, 5F2A 02.
        val pdol = hex("9F66 04 9F02 06 9F1A 02 9A 03 9F37 04 5F2A 02")
        val data = Emv.pdolData(pdol, LocalDate.of(2026, 11, 12), 0x01020304)
        assertArrayEquals(
            hex("F0204000 000000000000 0643 261112 01020304 0643"),
            data,
        )
        val gpo = Emv.gpo(pdol, LocalDate.of(2026, 11, 12), 0x01020304)
        assertEquals(0x80.toByte(), gpo[0])
        assertEquals((data.size + 2).toByte(), gpo[4])
        assertEquals(0x83.toByte(), gpo[5])
        // Без PDOL — пустой шаблон 83 00.
        assertArrayEquals(hex("80A8000002830000"), Emv.gpo(null, LocalDate.of(2026, 11, 12), 0))
    }

    @Test
    fun `AFL из обоих форматов ответа GPO`() {
        // Формат 2: 77 → 82 (AIP) и 94 (AFL): файл 1 записи 1–2, файл 2 запись 1.
        val f2 = hex("77 0E 82 02 1980 94 08 08010200 10010100")
        assertEquals(listOf(Triple(1, 1, 2), Triple(2, 1, 1)), Emv.afl(f2))
        // Формат 1: 80 → AIP (2 байта) + AFL.
        val f1 = hex("80 06 1980 18010300")
        assertEquals(listOf(Triple(3, 1, 3)), Emv.afl(f1))
    }

    @Test
    fun `валюта карты и страна банка — тенге, Казахстан, зарубежная Visa`() {
        val record = hex("70 0E 5F28 02 0398 9F42 02 0398 5F25 03 260101")
        val info = Emv.info(hex("A0000000031010"), listOf(hex("6F 00"), record))
        assertEquals(EmvBrand.VISA, info.brand)
        assertEquals(398, info.issuerCountry)
        assertEquals(Currency.KZT, info.currency)
        assertEquals(CardNetwork.VISA_MC_FOREIGN, info.network)
    }

    @Test
    fun `UnionPay российского банка и рублёвая Visa`() {
        val ru = hex("70 0A 5F28 02 0643 9F42 02 0156")
        val up = Emv.info(hex("A000000333010101"), listOf(ru))
        assertEquals(CardNetwork.UNIONPAY, up.network)
        assertEquals(Currency.CNY, up.currency)
        val visaRu = Emv.info(hex("A0000000031010"), listOf(hex("70 0A 5F28 02 0643 9F42 02 0643")))
        assertNull(visaRu.network)
        assertEquals(Currency.RUB, visaRu.currency)
        assertEquals(CardNetwork.MIR, Emv.info(hex("A0000006581010"), emptyList()).network)
        assertEquals(CardNetwork.UNIONPAY_FOREIGN, Emv.info(hex("A000000333010101"), listOf(hex("70 04 5F28 02 0417"))).network)
    }

    @Test
    fun `битые данные не роняют разбор`() {
        assertTrue(Emv.parse(hex("6F 20 84")).isEmpty())
        assertTrue(Emv.afl(hex("77 02 82")).isEmpty())
        assertNull(Emv.bcdNumber(hex("0A43")))
        assertTrue(Emv.ok(hex("6F00 9000")))
        assertEquals(false, Emv.ok(hex("6A82")))
    }
}
