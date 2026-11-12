package com.example.mymaser.currency

import com.example.mymaser.history.History
import com.example.mymaser.history.localDate
import com.example.mymaser.history.maserDelta
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap
import kotlin.math.abs
import org.json.JSONObject

/** Exchange rates by currency and day; lookups use the latest rate on or before the day. */
class RateTable(rates: List<ExchangeRate>) {

    private val byCurrency: Map<String, TreeMap<Long, Double>> = rates
        .groupBy { it.currency }
        .mapValues { (_, list) -> TreeMap(list.associate { it.epochDay to it.ratePerEur }) }

    fun ratePerEur(currency: String, epochDay: Long): Double? =
        if (currency == BASE_CURRENCY) 1.0 else byCurrency[currency]?.floorEntry(epochDay)?.value

    /** Converts [amount] using the rates of [epochDay], or null if either rate is unknown. */
    fun convert(amount: Double, from: String, to: String, epochDay: Long): Double? {
        if (from == to) return amount
        val fromRate = ratePerEur(from, epochDay) ?: return null
        val toRate = ratePerEur(to, epochDay) ?: return null
        return amount / fromRate * toRate
    }

    companion object {
        val EMPTY = RateTable(emptyList())
    }
}

fun History.epochDay(zone: ZoneId): Long = localDate(zone).toEpochDay()

/** Entries already in [displayCurrency]; the rest are still waiting for a rate. */
fun List<History>.countable(displayCurrency: String) = filter { it.currency == displayCurrency }

/**
 * Entries as shown in [displayCurrency]. Entries that can't be converted yet keep their original
 * amount and currency and are counted in [unconverted], so totals can leave them out.
 */
data class DisplayHistory(val entries: List<History>, val unconverted: Int) {
    fun countable(displayCurrency: String) = entries.countable(displayCurrency)
}

fun toDisplay(
    entries: List<History>,
    displayCurrency: String,
    rates: RateTable,
    zone: ZoneId
): DisplayHistory {
    var unconverted = 0
    val shown = entries.map { entry ->
        if (entry.currency == displayCurrency) {
            entry
        } else {
            rates.convert(entry.amount.toDouble(), entry.currency, displayCurrency, entry.epochDay(zone))
                ?.let { entry.copy(amount = it.toFloat(), currency = displayCurrency) }
                ?: entry.also { unconverted++ }
        }
    }
    return DisplayHistory(shown, unconverted)
}

/** A one-off correction carried over from the old stored running total. */
data class BalanceAdjustment(val amount: Double, val currency: String, val epochDay: Long)

/** The old running total minus what the entries add up to, or null when they already agree. */
fun openingAdjustment(storedTotal: Float?, entries: List<History>): Double? {
    val stored = storedTotal ?: return null
    val difference = stored - entries.sumOf { it.maserDelta.toDouble() }
    return difference.takeIf { abs(it) >= 0.01 }
}

/** Maser still owed, in [displayCurrency]; entries without a rate yet are left out. */
fun maserBalance(
    display: DisplayHistory,
    adjustment: BalanceAdjustment?,
    displayCurrency: String,
    rates: RateTable
): Double {
    val fromEntries = display.countable(displayCurrency).sumOf { it.maserDelta.toDouble() }
    val fromAdjustment = adjustment?.let {
        rates.convert(it.amount, it.currency, displayCurrency, it.epochDay)
    } ?: 0.0
    return fromEntries + fromAdjustment
}

/** Parses a Frankfurter response, either a single day or a date range, into per-euro rates. */
fun parseRates(json: String): List<ExchangeRate> {
    val root = JSONObject(json)
    val rates = root.getJSONObject("rates")

    fun ratesOf(day: LocalDate, values: JSONObject) = values.keys().asSequence().map { currency ->
        ExchangeRate(day.toEpochDay(), currency, values.getDouble(currency))
    }

    return if (root.has("start_date")) {
        rates.keys().asSequence()
            .flatMap { date -> ratesOf(LocalDate.parse(date), rates.getJSONObject(date)) }
            .toList()
    } else {
        ratesOf(LocalDate.parse(root.getString("date")), rates).toList()
    }
}
