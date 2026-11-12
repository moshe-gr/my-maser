package com.example.mymaser.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** How close to the usual day of the month an entry must be to count as "due now". */
private const val DUE_WINDOW_DAYS = 2L

/** Recurring patterns older than this no longer push a name to the top. */
private const val RECURRING_LOOKBACK_DAYS = 400L

/** How many recent entries vote on the suggested amount. */
private const val AMOUNT_LOOKBACK = 6

/** Names compare ignoring case and surrounding spaces, so "Salary" and "salary " are one source. */
fun nameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

/**
 * Days between [now] and the nearest occurrence of [entry]'s day of month, looking at the
 * previous, current and next month. Days past a short month's end map to its last day, so a
 * payment on the 31st recurs on the 30th of a 30-day month.
 */
fun daysFromSameDayOfMonth(entry: LocalDate, now: LocalDate): Long =
    (-1L..1L).minOf { offset ->
        val target = YearMonth.from(now).plusMonths(offset).atDayClamped(entry.dayOfMonth)
        abs(ChronoUnit.DAYS.between(now, target))
    }

private fun History.isDueAround(now: ZonedDateTime): Boolean =
    daysFromSameDayOfMonth(localDate(now.zone), now.toLocalDate()) <= DUE_WINDOW_DAYS

private data class RankedName(val name: String, val dueNow: Boolean, val lastUsed: Long)

/**
 * Distinct names, most relevant first: sources that recently recurred around today's date come
 * first, then the rest by most recent use. Each name is shown as it was last spelled.
 */
fun rankSuggestedNames(entries: List<History>, now: ZonedDateTime): List<String> {
    val recurringSince = now.minusDays(RECURRING_LOOKBACK_DAYS).toInstant().toEpochMilli()
    return entries
        .groupBy { nameKey(it.name) }
        .filterKeys { it.isNotEmpty() }
        .values
        .map { group ->
            val latest = group.maxBy { it.timeStamp }
            RankedName(
                name = latest.name.trim(),
                dueNow = group.any { it.timeStamp >= recurringSince && it.isDueAround(now) },
                lastUsed = latest.timeStamp
            )
        }
        .sortedWith(compareByDescending<RankedName> { it.dueNow }.thenByDescending { it.lastUsed })
        .map { it.name }
}

/**
 * Filters [rankedNames] by [query], keeping their order but putting names that start with the
 * query ahead of names that only contain it. The exact name already typed is left out.
 */
fun matchSuggestions(rankedNames: List<String>, query: String, limit: Int): List<String> {
    val key = nameKey(query)
    val (prefix, contains) = rankedNames
        .filter { nameKey(it) != key && nameKey(it).contains(key) }
        .partition { nameKey(it).startsWith(key) }
    return (prefix + contains).take(limit)
}

/**
 * The amount to prefill for [name], or null when it has no history.
 *
 * Entries made around today's day of the month are preferred, since recurring payments land on
 * similar dates. Among the most recent of those, each entry votes for its amount with weight
 * 1, 1/2, 1/3… by recency: a raise wins over an older amount, a one-off is outvoted by a
 * regular amount repeated three times, and ties go to the most recent.
 */
fun suggestedAmount(
    entries: List<History>,
    name: String,
    isDonation: Boolean,
    now: ZonedDateTime
): Float? = suggestAmount(entries, name, isDonation, now)?.amount

/** An amount in the currency it was entered in. */
data class AmountSuggestion(val amount: Float, val currency: String)

/** [suggestedAmount] together with its currency; the same number in two currencies votes apart. */
fun suggestAmount(
    entries: List<History>,
    name: String,
    isDonation: Boolean,
    now: ZonedDateTime
): AmountSuggestion? {
    val key = nameKey(name)
    val matching = entries
        .filter { it.isDonation == isDonation && nameKey(it.name) == key }
        .sortedByDescending { it.timeStamp }
    val candidates = matching.filter { it.isDueAround(now) }
        .ifEmpty { matching }
        .take(AMOUNT_LOOKBACK)
    return candidates.withIndex()
        .groupBy({ AmountSuggestion(it.value.amount, it.value.currency) }, { it.index })
        .maxWithOrNull(
            compareBy<Map.Entry<AmountSuggestion, List<Int>>> { (_, ranks) ->
                ranks.sumOf { 1.0 / (it + 1) }
            }.thenByDescending { (_, ranks) -> ranks.min() }
        )
        ?.key
}
