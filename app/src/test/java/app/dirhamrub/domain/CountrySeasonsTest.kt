package app.dirhamrub.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CountrySeasonsTest {
    private fun event(iso: String, date: String) = CountrySeasons.current(iso, LocalDate.parse(date))

    @Test fun `полный каталог без повторов стран и с датами`() {
        val all = CountrySeasons.all
        assertEquals(37, all.size)
        assertEquals(all.size, all.map { it.iso }.distinct().size)
        assertTrue(all.map { it.iso }.containsAll(listOf("RU", "BY", "KZ", "KG", "TJ", "UZ", "TM", "AZ", "AM", "MD")))
        assertTrue(all.all { it.bali || it.dateRange != null || it.dates.isNotEmpty() || (it.start != null && it.end != null) })
    }

    @Test fun `зимний сезон проходит через новый год включая високосный день`() {
        assertNotNull(event("RU", "2026-12-01"))
        assertNotNull(event("RU", "2027-01-01"))
        assertNotNull(event("RU", "2028-02-29"))
        assertNull(event("RU", "2027-03-01"))
        assertNull(event("RU", "2026-07-01"))
    }

    @Test fun `сакура только весной с включёнными границами`() {
        assertNull(event("JP", "2026-02-28"))
        assertNotNull(event("JP", "2026-03-01"))
        assertNotNull(event("JP", "2026-05-31"))
        assertNull(event("JP", "2026-06-01"))
    }

    @Test fun `подвижные праздники не повторяются в неверный день следующего года`() {
        assertNotNull(event("TH", "2026-11-24"))
        assertNull(event("TH", "2027-11-24"))
        assertNotNull(event("VN", "2026-02-17"))
        assertNull(event("VN", "2027-02-17"))
        assertNotNull(event("VN", "2027-02-06"))
        assertNotNull(event("CN", "2028-10-03"))
        assertNull(event("CN", "2031-10-03"))
    }

    @Test fun `карнавал ограничен известным интервалом`() {
        assertNotNull(event("IT", "2026-01-31"))
        assertNotNull(event("IT", "2026-02-17"))
        assertNull(event("IT", "2026-02-18"))
        assertNull(event("IT", "2027-02-10"))
    }

    @Test fun `Галунган следует циклу 210 дней`() {
        assertNotNull(event("ID", "2025-04-23"))
        assertNotNull(event("ID", "2025-11-19"))
        assertNotNull(event("ID", "2026-06-17"))
        assertNull(event("ID", "2026-06-25"))
    }

    @Test fun `страна не известна или не выбрана — без пасхалки`() {
        assertNull(CountrySeasons.current(null, LocalDate.of(2026, 4, 1)))
        assertNull(event("XX", "2026-04-01"))
    }
}
