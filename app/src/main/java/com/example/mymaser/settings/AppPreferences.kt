package com.example.mymaser.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
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
import org.json.JSONException
import org.json.JSONObject

const val DEFAULT_MASER_PERCENTAGE = 10f
const val MIN_MASER_PERCENTAGE = 1f
const val MAX_MASER_PERCENTAGE = 100f
val PRESET_MASER_PERCENTAGES = listOf(10f, 20f)

enum class ThemeMode(val key: String, val nightMode: Int) {
    System("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light("light", AppCompatDelegate.MODE_NIGHT_NO),
    Dark("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

private const val THEME_MIRROR_PREFS = "theme_mirror"
private const val THEME_MIRROR_KEY = "theme_mode"

/**
 * Makes [mode] the app-wide night mode, so window backgrounds and system UI match it before
 * Compose draws. Also mirrors it to SharedPreferences, which, unlike DataStore, can be read
 * synchronously at start-up.
 */
fun Context.applyThemeMode(mode: ThemeMode) {
    getSharedPreferences(THEME_MIRROR_PREFS, Context.MODE_PRIVATE).edit {
        putString(THEME_MIRROR_KEY, mode.key)
    }
    AppCompatDelegate.setDefaultNightMode(mode.nightMode)
}

/** The last theme passed to [applyThemeMode], or System if it never ran. */
fun Context.mirroredThemeMode(): ThemeMode = ThemeMode.fromKey(
    getSharedPreferences(THEME_MIRROR_PREFS, Context.MODE_PRIVATE).getString(THEME_MIRROR_KEY, null)
)

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

fun Preferences.maserPercentage(): Float = this[SettingsKeys.MASER_PERCENTAGE] ?: DEFAULT_MASER_PERCENTAGE

fun Preferences.maserBasis(): MaserBasis = MaserBasis.fromKey(this[SettingsKeys.MASER_BASIS])

fun Preferences.aiEnabled(): Boolean = this[SettingsKeys.AI_ENABLED] ?: false

fun Context.maserPercentageFlow(): Flow<Float> = dataStore.data
    .map { it.maserPercentage() }
    .distinctUntilChanged()

fun Context.maserBasisFlow(): Flow<MaserBasis> = dataStore.data
    .map { it.maserBasis() }
    .distinctUntilChanged()

fun Context.aiEnabledFlow(): Flow<Boolean> = dataStore.data
    .map { it.aiEnabled() }
    .distinctUntilChanged()

data class GmailSettings(
    val connected: Boolean = false,
    val needsReconnect: Boolean = false,
    val keywords: String = DEFAULT_GMAIL_KEYWORDS
)

fun Preferences.gmailSettings(): GmailSettings = GmailSettings(
    connected = this[SettingsKeys.GMAIL_CONNECTED] ?: false,
    needsReconnect = this[SettingsKeys.GMAIL_NEEDS_RECONNECT] ?: false,
    keywords = this[SettingsKeys.GMAIL_KEYWORDS] ?: DEFAULT_GMAIL_KEYWORDS
)

fun Context.gmailSettingsFlow(): Flow<GmailSettings> = dataStore.data
    .map { it.gmailSettings() }
    .distinctUntilChanged()

/** Names as documents write them mapped to the user's own, see [com.example.mymaser.ai.aliasKey]. */
fun Context.nameAliasesFlow(): Flow<Map<String, String>> = dataStore.data
    .map { decodeAliases(it[SettingsKeys.NAME_ALIASES]) }
    .distinctUntilChanged()

suspend fun Context.rememberNameAliases(aliases: Map<String, String>) {
    if (aliases.isEmpty()) return
    dataStore.edit {
        val merged = decodeAliases(it[SettingsKeys.NAME_ALIASES]) - aliases.keys + aliases
        val newest = merged.entries.toList().takeLast(MAX_ALIASES).associate { e -> e.toPair() }
        it[SettingsKeys.NAME_ALIASES] = JSONObject(newest).toString()
    }
}

private const val MAX_ALIASES = 300

private fun decodeAliases(json: String?): Map<String, String> {
    if (json.isNullOrEmpty()) return emptyMap()
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    } catch (_: JSONException) {
        emptyMap()
    }
}

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

fun Preferences.reminderSettings(): ReminderSettings = ReminderSettings(
    enabled = this[SettingsKeys.REMINDERS_ENABLED] ?: false,
    expectedEntries = this[SettingsKeys.REMINDER_EXPECTED] ?: true,
    balance = this[SettingsKeys.REMINDER_BALANCE] ?: true,
    yearInReview = this[SettingsKeys.REMINDER_REVIEW] ?: true
)

fun Preferences.reminderMinuteOfDay(): Int =
    this[SettingsKeys.REMINDER_MINUTE_OF_DAY] ?: DEFAULT_REMINDER_MINUTE_OF_DAY

fun Context.reminderSettingsFlow(): Flow<ReminderSettings> = dataStore.data
    .map { it.reminderSettings() }
    .distinctUntilChanged()

fun Context.reminderMinuteOfDayFlow(): Flow<Int> = dataStore.data
    .map { it.reminderMinuteOfDay() }
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
