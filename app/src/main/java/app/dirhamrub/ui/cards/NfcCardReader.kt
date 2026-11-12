package app.dirhamrub.ui.cards

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.dirhamrub.domain.wallet.Emv
import app.dirhamrub.domain.wallet.EmvInfo
import java.security.SecureRandom
import java.time.LocalDate

/** Что вышло с картой, приложенной к телефону. */
sealed interface NfcRead {
    data class Found(val info: EmvInfo) : NfcRead

    /** Карту увидели, но не прочитали (убрали слишком рано, не банковская, отказала). */
    data object Failed : NfcRead
}

/** Есть ли на телефоне NFC и включён ли он. */
fun nfcReady(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

/**
 * Пока [enabled] и экран на виду, телефон ждёт банковскую карту у задней панели (режим чтения NFC; оплата
 * телефоном в это время не мешает — она снова работает, как только экран закроют). Читаем только платёжную
 * систему, страну банка и валюту карты — номер и срок не разбираем и не храним. [onRead] — в главном потоке.
 */
@Composable
fun NfcCardReader(enabled: Boolean, onRead: (NfcRead) -> Unit) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onRead)
    DisposableEffect(enabled, context) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (!enabled || activity == null || adapter == null || !adapter.isEnabled) return@DisposableEffect onDispose { }
        val reader = NfcAdapter.ReaderCallback { tag ->
            val result = read(tag)
            activity.runOnUiThread { callback.value(result) }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        runCatching { adapter.enableReaderMode(activity, reader, flags, null) }
            .onFailure { Log.w(TAG, "Режим чтения NFC не включился", it) }
        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }
}

/** Разговор с картой: каталог приложений → приложение → GPO → записи. Ошибка на любом шаге — [NfcRead.Failed]. */
private fun read(tag: Tag): NfcRead {
    val iso = IsoDep.get(tag) ?: return NfcRead.Failed
    return try {
        iso.connect()
        iso.timeout = TIMEOUT_MS
        val ppse = iso.transceive(Emv.SELECT_PPSE)
        if (!Emv.ok(ppse)) return NfcRead.Failed
        val aid = Emv.aids(Emv.body(ppse)).firstOrNull() ?: return NfcRead.Failed
        val selected = iso.transceive(Emv.select(aid))
        if (!Emv.ok(selected)) return NfcRead.Failed
        val fci = Emv.body(selected)
        val responses = arrayListOf(fci)
        val pdol = Emv.find(fci, 0x9F38)
        val gpo = iso.transceive(Emv.gpo(pdol, LocalDate.now(), SecureRandom().nextInt()))
        if (Emv.ok(gpo)) {
            val body = Emv.body(gpo)
            responses += body
            // Записи читаем, пока не нашли валюту и страну: обычно хватает одной-двух.
            for ((sfi, first, last) in Emv.afl(body)) {
                for (rec in first..last) {
                    val r = iso.transceive(Emv.readRecord(sfi, rec))
                    if (Emv.ok(r)) responses += Emv.body(r)
                    val info = Emv.info(aid, responses)
                    if (info.currency != null && info.issuerCountry != null) return NfcRead.Found(info)
                }
            }
        }
        NfcRead.Found(Emv.info(aid, responses))
    } catch (e: Exception) {
        Log.w(TAG, "Карта не прочиталась", e)
        NfcRead.Failed
    } finally {
        runCatching { iso.close() }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "DirhamNfc"
private const val TIMEOUT_MS = 3000
