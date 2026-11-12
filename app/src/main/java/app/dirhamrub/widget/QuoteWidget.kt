package app.dirhamrub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.dirhamrub.MainActivity
import app.dirhamrub.R
import app.dirhamrub.data.WalletRepository
import app.dirhamrub.data.ratesDataStore
import app.dirhamrub.data.settingsDataStore
import app.dirhamrub.data.unionPayDataStore
import app.dirhamrub.domain.MC
import app.dirhamrub.domain.Numbers
import app.dirhamrub.domain.wallet.Countries
import app.dirhamrub.domain.wallet.QuickSums
import app.dirhamrub.domain.wallet.RateBasis
import app.dirhamrub.domain.wallet.RateEngine
import app.dirhamrub.domain.wallet.approximate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pro: виджет «Курс по чеку» на рабочем столе — страна и ходовая сумма в местной валюте («🇹🇷 1 000 TRY»),
 * крупно — сколько это рублей по лучшей карте, ниже — какая карта. Касание — приложение. Без Pro — подсказка,
 * касание открывает экран Pro. Считает так же, как главный экран, по сохранённым карте и курсам; без интернета.
 */
class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = render(context)
                for (id in ids) manager.updateAppWidget(id, views)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quote)
        val state = WalletRepository(context.settingsDataStore, context.unionPayDataStore, context.ratesDataStore).load()
        if (!state.settings.pro) {
            views.setTextViewText(R.id.widget_place, context.getString(R.string.widget_title))
            views.setTextViewText(R.id.widget_sum, context.getString(R.string.pro_badge))
            views.setTextViewText(R.id.widget_card, context.getString(R.string.widget_locked))
            views.setOnClickPendingIntent(R.id.widget_root, open(context, MainActivity.SCREEN_PRO))
            return views
        }
        val wallet = state.wallet
        val spend = wallet.spend
        val home = wallet.home
        val unionPay = state.unionPay.takeIf { state.settings.unionPay }
        val reference = state.official?.withUnionPay(unionPay)
        val quote = RateEngine.quoteAll(wallet, reference, unionPay)
        val best = quote.best
        val perUnit = best?.homePerUnit ?: quote.official
        val amount = QuickSums.chips(spend).getOrElse(4) { QuickSums.chips(spend).last() }
        val flag = Countries.placeFor(spend, wallet.place, null)?.flag ?: spend.flag
        views.setTextViewText(R.id.widget_place, "$flag ${Numbers.moneyWhole(amount)} ${spend.code}")
        if (perUnit == null || spend == home) {
            views.setTextViewText(R.id.widget_sum, "—")
            views.setTextViewText(R.id.widget_card, context.getString(R.string.widget_no_rate))
        } else {
            val approx = best == null || best.basis.approximate || best.basis == RateBasis.OFFICIAL
            val sum = Numbers.moneyWhole(amount.multiply(perUnit, MC))
            views.setTextViewText(R.id.widget_sum, "${if (approx) "≈ " else ""}$sum ${home.mark}")
            views.setTextViewText(
                R.id.widget_card,
                best?.let { context.getString(R.string.widget_best, it.card.name) } ?: context.getString(R.string.widget_cbr),
            )
        }
        views.setOnClickPendingIntent(R.id.widget_root, open(context, null))
        return views
    }

    private fun open(context: Context, screen: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (screen != null) intent.putExtra(MainActivity.EXTRA_SCREEN, screen)
        return PendingIntent.getActivity(
            context,
            screen?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Перерисовать все виджеты (сменили страну, карты, курсы, купили Pro). Виджетов нет — ничего. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, QuoteWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, QuoteWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
