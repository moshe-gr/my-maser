package com.example.mymaser.history

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class DueStatus { Upcoming, DueNow, Overdue }

/** A recurring entry expected around [expectedDate] that hasn't been logged yet. */
data class Prediction(
    val name: String,
    val isDonation: Boolean,
    val expectedDate: LocalDate,
    val amount: Float,
    val currency: String,
    val status: DueStatus,
    val isTaxOnly: Boolean = false
) {
    /** Identifies this source's occurrence in one month, for dismissals and sent reminders. */
    val key: String
        get() = predictionKey(name, isDonation, YearMonth.from(expectedDate))

    fun daysUntil(today: LocalDate): Long = ChronoUnit.DAYS.between(today, expectedDate)
}

fun predictionKey(name: String, isDonation: Boolean, month: YearMonth): String =
    "${if (isDonation) "d" else "i"}|${nameKey(name)}|$month"

private const val UPCOMING_DAYS = 3
private const val DUE_NOW_DAYS = 2
private const val OVERDUE_DAYS = 10

private fun statusFor(daysUntil: Long): DueStatus? = when {
    daysUntil in -DUE_NOW_DAYS..DUE_NOW_DAYS -> DueStatus.DueNow
    daysUntil in (DUE_NOW_DAYS + 1)..UPCOMING_DAYS -> DueStatus.Upcoming
    daysUntil in -OVERDUE_DAYS until -DUE_NOW_DAYS -> DueStatus.Overdue
    else -> null
}

/**
 * Recurring sources whose next occurrence is close to today and not logged yet: due within a few
 * days, due now, or up to [OVERDUE_DAYS] late. Sources in [dismissed] (by [Prediction.key]) are
 * skipped. Sorted by expected date.
 */
fun predictDueEntries(
    history: List<History>,
    dismissed: Set<String>,
    now: ZonedDateTime
): List<Prediction> {
    val today = now.toLocalDate()
    val currentMonth = YearMonth.from(today)
    return history.bySource()
        .mapNotNull { (group, entries) ->
            val (isDonation, _) = group
            val recurrence = detectRecurrence(entries, now) ?: return@mapNotNull null
            val dates = entries.map { it.localDate(now.zone) }
            val latest = entries.maxBy { it.timeStamp }
            val latestName = latest.name.trim()

            (-1L..1L)
                .map { offset -> currentMonth.plusMonths(offset).atDayClamped(recurrence.typicalDay) }
                .mapNotNull { expected ->
                    val daysUntil = ChronoUnit.DAYS.between(today, expected)
                    val status = statusFor(daysUntil) ?: return@mapNotNull null
                    if (dates.hasEntryNear(expected)) return@mapNotNull null
                    val suggestion = suggestAmount(entries, latestName, isDonation, now)
                        ?: return@mapNotNull null
                    Prediction(
                        name = latestName,
                        isDonation = isDonation,
                        expectedDate = expected,
                        amount = suggestion.amount,
                        currency = suggestion.currency,
                        status = status,
                        isTaxOnly = latest.isTaxOnlyDonation
                    )
                }
                .filterNot { it.key in dismissed }
                .minByOrNull { abs(it.daysUntil(today)) }
        }
        .sortedBy { it.expectedDate }
}

/** Names of sources that recur monthly, most recently used first. */
fun recurringSourceNames(history: List<History>, now: ZonedDateTime): List<String> =
    history.bySource()
        .values
        .filter { detectRecurrence(it, now) != null }
        .map { entries -> entries.maxBy { it.timeStamp } }
        .sortedByDescending { it.timeStamp }
        .map { it.name.trim() }

/** Dismissal keys still worth keeping: only those for last, this or next month. */
fun pruneDismissed(keys: Set<String>, today: LocalDate): Set<String> {
    val month = YearMonth.from(today)
    val keep = setOf(month.minusMonths(1), month, month.plusMonths(1)).map(YearMonth::toString)
    return keys.filterTo(mutableSetOf()) { key -> keep.any { key.endsWith("|$it") } }
}
