package app.dirhamrub.domain

import app.dirhamrub.domain.RatesPolicy.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    @Test
    fun `раз в 6 часов — при запуске и возвращении, если курсы старые`() {
        val u = RatesUpdate.EVERY_6H
        assertFalse(RatesPolicy.due(u, Trigger.LAUNCH, now - 2 * hour, now))
        assertFalse(RatesPolicy.due(u, Trigger.RESUME, now - 5 * hour, now))
        assertTrue(RatesPolicy.due(u, Trigger.RESUME, now - 6 * hour, now))
        assertTrue(RatesPolicy.due(u, Trigger.LAUNCH, 0L, now))
        // Часы на телефоне перевели назад — курсы «из будущего» тоже скачиваем заново.
        assertTrue(RatesPolicy.due(u, Trigger.RESUME, now + hour, now))
        assertTrue(RatesPolicy.auto(u))
    }

    @Test
    fun `при запуске — всегда при запуске и никогда при возвращении`() {
        val u = RatesUpdate.LAUNCH
        assertTrue(RatesPolicy.due(u, Trigger.LAUNCH, now - hour, now))
        assertFalse(RatesPolicy.due(u, Trigger.RESUME, now - 10 * hour, now))
        assertTrue(RatesPolicy.due(u, Trigger.RESUME, 0L, now))
    }

    @Test
    fun `вручную — только кнопкой`() {
        val u = RatesUpdate.MANUAL
        assertFalse(RatesPolicy.due(u, Trigger.LAUNCH, 0L, now))
        assertFalse(RatesPolicy.due(u, Trigger.RESUME, now - 100 * hour, now))
        assertTrue(RatesPolicy.due(u, Trigger.BUTTON, now, now))
        assertFalse(RatesPolicy.auto(u))
    }

    @Test
    fun `только по Wi-Fi — сами не ходим по мобильной сети, кнопкой можно`() {
        assertTrue(RatesPolicy.network(Trigger.RESUME, wifiOnly = false, unmetered = false))
        assertFalse(RatesPolicy.network(Trigger.LAUNCH, wifiOnly = true, unmetered = false))
        assertFalse(RatesPolicy.network(Trigger.RESUME, wifiOnly = true, unmetered = false))
        assertTrue(RatesPolicy.network(Trigger.RESUME, wifiOnly = true, unmetered = true))
        assertTrue(RatesPolicy.network(Trigger.BUTTON, wifiOnly = true, unmetered = false))
    }
}
