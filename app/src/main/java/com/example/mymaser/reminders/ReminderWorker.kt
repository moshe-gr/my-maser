package com.example.mymaser.reminders

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.RateTable
import com.example.mymaser.currency.maserBalance
import com.example.mymaser.currency.toDisplay
import com.example.mymaser.dataStore
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.history.migrateToMultiCurrency
import com.example.mymaser.history.predictDueEntries
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.balanceSetupFlow
import com.example.mymaser.settings.displayCurrencyFlow
import com.example.mymaser.settings.reminderSettingsFlow
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/** Daily: refreshes exchange rates, then posts any reminders that are due and not sent yet. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = context.reminderSettingsFlow().first()
        if (!settings.enabled) return Result.success()

        migrateToMultiCurrency(context)
        CurrencyRepository.getInstance(context).ensureRates()

        val db = HistoryDb.getInstance(context)
        val originals = db.getHistoryDao().getAll()
        val rates = RateTable(db.getExchangeRateDao().getAll())
        val currency = context.displayCurrencyFlow().first()
        val adjustment = context.balanceSetupFlow().first()?.adjustment
        val prefs = context.dataStore.data.first()
        val now = ZonedDateTime.now()

        val display = toDisplay(originals, currency, rates, now.zone)
        val input = ReminderInput(
            history = display.countable(currency),
            balance = maserBalance(display, adjustment, currency, rates),
            displayCurrency = currency,
            predictions = predictDueEntries(
                originals,
                prefs[SettingsKeys.DISMISSED_PREDICTIONS].orEmpty(),
                now
            ),
            settings = settings,
            alreadySent = prefs[SettingsKeys.SENT_REMINDERS].orEmpty(),
            now = now
        )

        createNotificationChannels(context)
        val sent = computeReminders(input)
            .filter { postReminder(context, it) }
            .map { it.key }
        if (sent.isNotEmpty()) {
            context.dataStore.edit {
                val all = it[SettingsKeys.SENT_REMINDERS].orEmpty() + sent
                it[SettingsKeys.SENT_REMINDERS] = pruneSentKeys(all, now.toLocalDate())
            }
        }
        return Result.success()
    }
}
