package com.example.mymaser.history

import java.time.Instant
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
    val status: DueStatus
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

/** An entry this close to the expected date counts as that occurrence being logged. */
private const val LOGGED_TOLERANCE_DAYS = 5

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
    return history
        .groupBy { it.isDonation to nameKey(it.name) }
        .filterKeys { (_, key) -> key.isNotEmpty() }
        .mapNotNull { (group, entries) ->
            val (isDonation, _) = group
            val recurrence = detectRecurrence(entries, now) ?: return@mapNotNull null
            val dates = entries.map { Instant.ofEpochMilli(it.timeStamp).atZone(now.zone).toLocalDate() }
            val latestName = entries.maxBy { it.timeStamp }.name.trim()

            (-1L..1L)
                .map { offset ->
                    val month = currentMonth.plusMonths(offset)
                    month.atDay(minOf(recurrence.typicalDay, month.lengthOfMonth()))
                }
                .mapNotNull { expected ->
                    val daysUntil = ChronoUnit.DAYS.between(today, expected)
                    val status = statusFor(daysUntil) ?: return@mapNotNull null
                    val logged = dates.any {
                        abs(ChronoUnit.DAYS.between(it, expected)) <= LOGGED_TOLERANCE_DAYS
                    }
                    if (logged) return@mapNotNull null
                    val suggestion = suggestAmount(entries, latestName, isDonation, now)
                        ?: return@mapNotNull null
                    Prediction(
                        name = latestName,
                        isDonation = isDonation,
                        expectedDate = expected,
                        amount = suggestion.amount,
                        currency = suggestion.currency,
                        status = status
                    )
                }
                .filterNot { it.key in dismissed }
                .minByOrNull { abs(it.daysUntil(today)) }
        }
        .sortedBy { it.expectedDate }
}

/** Dismissal keys still worth keeping: only those for last, this or next month. */
fun pruneDismissed(keys: Set<String>, today: LocalDate): Set<String> {
    val month = YearMonth.from(today)
    val keep = setOf(month.minusMonths(1), month, month.plusMonths(1)).map(YearMonth::toString)
    return keys.filterTo(mutableSetOf()) { key -> keep.any { key.endsWith("|$it") } }
}
