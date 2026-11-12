package com.example.mymaser.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.mymaser.currency.BalanceAdjustment
import com.example.mymaser.currency.DEFAULT_CURRENCY
import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.dataStore
import com.example.mymaser.gmail.DEFAULT_GMAIL_KEYWORDS
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.pruneDismissed
import com.example.mymaser.reminders.ReminderSettings
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val DEFAULT_MASER_PERCENTAGE = 10f
const val MIN_MASER_PERCENTAGE = 1f
const val MAX_MASER_PERCENTAGE = 100f
val PRESET_MASER_PERCENTAGES = listOf(10f, 20f)

enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

/** Which payslip figure ma'aser is counted from. */
enum class MaserBasis(val key: String) {
    Net("net"),
    Gross("gross");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Net
    }
}

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    val displayCurrency: String = DEFAULT_CURRENCY
)

/** The chosen display currency, else the one the old single-currency setting implied. */
fun Preferences.displayCurrency(): String =
    this[SettingsKeys.DISPLAY_CURRENCY]
        ?: SupportedCurrency.fromSymbol(this[SettingsKeys.LEGACY_CURRENCY_SYMBOL])?.code
        ?: DEFAULT_CURRENCY

fun Context.appearanceFlow(): Flow<Appearance> = dataStore.data
    .map {
        Appearance(
            themeMode = ThemeMode.fromKey(it[SettingsKeys.THEME_MODE]),
            displayCurrency = it.displayCurrency()
        )
    }
    .distinctUntilChanged()

fun Context.displayCurrencyFlow(): Flow<String> = appearanceFlow()
    .map { it.displayCurrency }
    .distinctUntilChanged()

fun Context.maserPercentageFlow(): Flow<Float> = dataStore.data
    .map { it[SettingsKeys.MASER_PERCENTAGE] ?: DEFAULT_MASER_PERCENTAGE }
    .distinctUntilChanged()

fun Context.maserBasisFlow(): Flow<MaserBasis> = dataStore.data
    .map { MaserBasis.fromKey(it[SettingsKeys.MASER_BASIS]) }
    .distinctUntilChanged()

fun Context.aiEnabledFlow(): Flow<Boolean> = dataStore.data
    .map { it[SettingsKeys.AI_ENABLED] ?: false }
    .distinctUntilChanged()

data class GmailSettings(
    val connected: Boolean = false,
    val needsReconnect: Boolean = false,
    val keywords: String = DEFAULT_GMAIL_KEYWORDS
)

fun Context.gmailSettingsFlow(): Flow<GmailSettings> = dataStore.data
    .map {
        GmailSettings(
            connected = it[SettingsKeys.GMAIL_CONNECTED] ?: false,
            needsReconnect = it[SettingsKeys.GMAIL_NEEDS_RECONNECT] ?: false,
            keywords = it[SettingsKeys.GMAIL_KEYWORDS] ?: DEFAULT_GMAIL_KEYWORDS
        )
    }
    .distinctUntilChanged()

data class BalanceSetup(val adjustment: BalanceAdjustment?)

/** Null until the multi-currency upgrade has run, so the balance is never shown half-migrated. */
fun Context.balanceSetupFlow(): Flow<BalanceSetup?> = dataStore.data
    .map { prefs ->
        if (prefs[SettingsKeys.MULTI_CURRENCY_MIGRATED] != true) return@map null
        val amount = prefs[SettingsKeys.BALANCE_ADJUSTMENT]
        val currency = prefs[SettingsKeys.BALANCE_ADJUSTMENT_CURRENCY]
        val day = prefs[SettingsKeys.BALANCE_ADJUSTMENT_DAY]
        BalanceSetup(
            if (amount != null && currency != null && day != null) {
                BalanceAdjustment(amount, currency, day)
            } else {
                null
            }
        )
    }
    .distinctUntilChanged()

/** Reminders are posted around this time of day unless the user picks another. */
const val DEFAULT_REMINDER_MINUTE_OF_DAY = 19 * 60

fun minuteOfDayToTime(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

fun Context.reminderSettingsFlow(): Flow<ReminderSettings> = dataStore.data
    .map {
        ReminderSettings(
            enabled = it[SettingsKeys.REMINDERS_ENABLED] ?: false,
            expectedEntries = it[SettingsKeys.REMINDER_EXPECTED] ?: true,
            balance = it[SettingsKeys.REMINDER_BALANCE] ?: true,
            yearInReview = it[SettingsKeys.REMINDER_REVIEW] ?: true
        )
    }
    .distinctUntilChanged()

fun Context.reminderMinuteOfDayFlow(): Flow<Int> = dataStore.data
    .map { it[SettingsKeys.REMINDER_MINUTE_OF_DAY] ?: DEFAULT_REMINDER_MINUTE_OF_DAY }
    .distinctUntilChanged()

/** Hides a due prediction (by its key) until its month has passed. */
suspend fun Context.dismissPrediction(key: String) {
    dataStore.edit {
        val current = it[SettingsKeys.DISMISSED_PREDICTIONS].orEmpty()
        it[SettingsKeys.DISMISSED_PREDICTIONS] = pruneDismissed(current + key, LocalDate.now())
    }
}

fun isValidMaserPercentage(value: Float) = value in MIN_MASER_PERCENTAGE..MAX_MASER_PERCENTAGE

/** "10%" for whole values, "12.5%" otherwise. */
fun formatPercentage(value: Float): String = "${formatAmountInput(value)}%"
