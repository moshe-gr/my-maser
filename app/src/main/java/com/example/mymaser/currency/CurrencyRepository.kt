package com.example.mymaser.currency

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.mymaser.dataStore
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.displayCurrency
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RateStatus { Idle, Loading, Failed }

/**
 * Downloads and caches ECB reference rates from frankfurter.dev. Only currency codes and dates
 * are sent; nothing about the user's entries leaves the device.
 */
class CurrencyRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = HistoryDb.getInstance(appContext)
    private val rateDao = db.getExchangeRateDao()
    private val historyDao = db.getHistoryDao()
    private val mutex = Mutex()
    private val _status = MutableStateFlow(RateStatus.Idle)

    val status: StateFlow<RateStatus> = _status.asStateFlow()

    val rates: Flow<RateTable> = rateDao.observeAll().map(::RateTable)

    /**
     * Makes sure every currency in use has rates from the first entry's date to today. Returns
     * false if a needed download failed, so callers can offer a retry.
     */
    suspend fun ensureRates(): Boolean = mutex.withLock {
        val prefs = appContext.dataStore.data.first()
        val entries = historyDao.getAll()
        val zone = ZoneId.systemDefault()
        val display = prefs.displayCurrency()
        val adjustmentCurrency = prefs[SettingsKeys.BALANCE_ADJUSTMENT_CURRENCY]
        val inUse = entries.map { it.currency }.toSet() + display + listOfNotNull(adjustmentCurrency)
        if (inUse.size <= 1) return@withLock true

        val symbols = inUse - BASE_CURRENCY
        val today = LocalDate.now(zone).toEpochDay()
        val firstNeeded = listOfNotNull(
            entries.minOfOrNull { it.epochDay(zone) },
            prefs[SettingsKeys.BALANCE_ADJUSTMENT_DAY]
        ).minOrNull() ?: today

        val missingHistory = symbols.any { currency ->
            val first = rateDao.firstDay(currency)
            first == null || first > firstNeeded + WEEKEND_SLACK_DAYS
        }
        val from = if (missingHistory) {
            firstNeeded
        } else {
            symbols.minOf { rateDao.lastDay(it) ?: firstNeeded } + 1
        }
        val fetchedToday = prefs[SettingsKeys.LAST_RATES_FETCH_DAY] == today
        if (from > today || (!missingHistory && fetchedToday)) return@withLock true

        _status.value = RateStatus.Loading
        try {
            val downloaded = fetchRange(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(today), symbols)
            rateDao.insertAll(downloaded)
            appContext.dataStore.edit { it[SettingsKeys.LAST_RATES_FETCH_DAY] = today }
            _status.value = RateStatus.Idle
            true
        } catch (e: CancellationException) {
            _status.value = RateStatus.Idle
            throw e
        } catch (_: Exception) {
            _status.value = RateStatus.Failed
            false
        }
    }

    private suspend fun fetchRange(
        start: LocalDate,
        end: LocalDate,
        currencies: Set<String>
    ): List<ExchangeRate> = withContext(Dispatchers.IO) {
        val url = URL("$API_BASE$start..$end?symbols=${currencies.sorted().joinToString(",")}")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            parseRates(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API_BASE = "https://api.frankfurter.dev/v1/"
        private const val TIMEOUT_MS = 20_000

        /** Markets close on weekends and holidays, so the first rate can precede the first entry. */
        private const val WEEKEND_SLACK_DAYS = 5

        @Volatile
        private var instance: CurrencyRepository? = null

        fun getInstance(context: Context): CurrencyRepository =
            instance ?: synchronized(this) {
                instance ?: CurrencyRepository(context).also { instance = it }
            }
    }
}
