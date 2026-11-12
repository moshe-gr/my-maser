package com.example.mymaser.history

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

enum class SummaryPeriod { ThisMonth, ThisYear, LastYear, Last12Months, AllTime }

data class MonthTotal(val month: YearMonth, val total: Double)

data class Recurrence(val typicalDay: Int, val nextExpected: LocalDate)

data class NameAnalytics(
    /** Entries for this name and type inside the period, newest first. */
    val entries: List<History>,
    val total: Double,
    val average: Double,
    val highest: Double?,
    val lowest: Double?,
    val firstTime: Long?,
    val lastTime: Long?,
    /** Total for the comparable earlier period, or null when the period has none (all time). */
    val previousTotal: Double?,
    /** This name's fraction of everything of the same type in the period, or null if that is zero. */
    val shareOfType: Double?,
    /** Net change this name made to the maser balance in the period. */
    val maserEffect: Double,
    /** The last 12 months, oldest first, ending with the current month. */
    val monthly: List<MonthTotal>,
    val recurrence: Recurrence?
) {
    val count: Int get() = entries.size

    /** Relative change versus [previousTotal], or null when there is nothing to compare with. */
    val change: Double?
        get() = previousTotal?.takeIf { it > 0 }?.let { (total - it) / it }
}

private const val MONTHS_IN_CHART = 12
private const val RECURRENCE_LOOKBACK_DAYS = 365L
private const val RECURRENCE_MIN_MONTHS = 3
private const val RECURRENCE_DAY_TOLERANCE = 3
private const val RECURRENCE_MIN_SHARE = 0.75

/** An entry this close to the expected date counts as that occurrence being logged. */
private const val LOGGED_TOLERANCE_DAYS = 5

/** [day] of this month, or its last day when the month is shorter. */
internal fun YearMonth.atDayClamped(day: Int): LocalDate = atDay(minOf(day, lengthOfMonth()))

internal fun List<LocalDate>.hasEntryNear(expected: LocalDate): Boolean =
    any { abs(ChronoUnit.DAYS.between(it, expected)) <= LOGGED_TOLERANCE_DAYS }

/** Entries grouped by type and name, the unit recurrence is detected on; unnamed entries are left out. */
internal fun List<History>.bySource(): Map<Pair<Boolean, String>, List<History>> =
    groupBy { it.isDonation to nameKey(it.name) }.filterKeys { (_, key) -> key.isNotEmpty() }

fun periodRange(period: SummaryPeriod, now: ZonedDateTime): TimeRange? = when (period) {
    SummaryPeriod.ThisMonth -> monthRange(now)
    SummaryPeriod.ThisYear -> yearRange(now)
    SummaryPeriod.LastYear -> previousYearRange(now)
    SummaryPeriod.Last12Months -> {
        val start = YearMonth.from(now).minusMonths(MONTHS_IN_CHART - 1L).atDay(1)
        TimeRange(start.startMillis(now.zone), now.toLocalDate().plusDays(1).startMillis(now.zone))
    }
    SummaryPeriod.AllTime -> null
}

/**
 * The earlier stretch [period] is fairly compared with. This year compares with last year up to
 * the same date, so a year in progress isn't measured against a whole one.
 */
fun previousPeriodRange(period: SummaryPeriod, now: ZonedDateTime): TimeRange? {
    val today = now.toLocalDate()
    val zone = now.zone
    return when (period) {
        SummaryPeriod.ThisMonth -> {
            val start = YearMonth.from(today).minusMonths(1).atDay(1)
            TimeRange(start.startMillis(zone), start.plusMonths(1).startMillis(zone))
        }
        SummaryPeriod.ThisYear -> {
            val lastYearToday = today.minusYears(1)
            TimeRange(
                lastYearToday.withDayOfYear(1).startMillis(zone),
                lastYearToday.plusDays(1).startMillis(zone)
            )
        }
        SummaryPeriod.LastYear -> {
            val start = today.withDayOfYear(1).minusYears(2)
            TimeRange(start.startMillis(zone), start.plusYears(1).startMillis(zone))
        }
        SummaryPeriod.Last12Months -> {
            val current = periodRange(SummaryPeriod.Last12Months, now)!!
            val start = YearMonth.from(today).minusMonths(2L * MONTHS_IN_CHART - 1).atDay(1)
            TimeRange(start.startMillis(zone), current.start)
        }
        SummaryPeriod.AllTime -> null
    }
}

private fun List<History>.within(range: TimeRange?) =
    if (range == null) this else filter { it.timeStamp in range }

private fun List<History>.sumAmounts() = sumOf { it.amount.toDouble() }

fun computeNameAnalytics(
    history: List<History>,
    entry: History,
    period: SummaryPeriod,
    now: ZonedDateTime
): NameAnalytics {
    val key = nameKey(entry.name)
    val sameType = history.filter { it.isDonation == entry.isDonation }
    val forName = sameType.filter { nameKey(it.name) == key }
    val range = periodRange(period, now)
    val inPeriod = forName.within(range).sortedByDescending { it.timeStamp }
    val total = inPeriod.sumAmounts()
    val typeTotal = sameType.within(range).sumAmounts()
    val currentMonth = YearMonth.from(now)

    return NameAnalytics(
        entries = inPeriod,
        total = total,
        average = if (inPeriod.isEmpty()) 0.0 else total / inPeriod.size,
        highest = inPeriod.maxOfOrNull { it.amount.toDouble() },
        lowest = inPeriod.minOfOrNull { it.amount.toDouble() },
        firstTime = inPeriod.lastOrNull()?.timeStamp,
        lastTime = inPeriod.firstOrNull()?.timeStamp,
        previousTotal = previousPeriodRange(period, now)?.let { forName.within(it).sumAmounts() },
        shareOfType = if (typeTotal > 0) total / typeTotal else null,
        maserEffect = inPeriod.sumOf { it.maserDelta.toDouble() },
        monthly = (MONTHS_IN_CHART - 1 downTo 0).map { back ->
            val month = currentMonth.minusMonths(back.toLong())
            MonthTotal(month, forName.filter { YearMonth.from(it.localDate(now.zone)) == month }.sumAmounts())
        },
        recurrence = detectRecurrence(forName, now)
    )
}

private fun circularDayDistance(a: Int, b: Int): Int {
    val diff = abs(a - b)
    return minOf(diff, 31 - diff)
}

/**
 * A name recurs when, over the past year, it appeared in at least three different months and
 * most of its entries landed within a few days of the same day of the month.
 */
fun detectRecurrence(entries: List<History>, now: ZonedDateTime): Recurrence? {
    val since = now.minusDays(RECURRENCE_LOOKBACK_DAYS).toInstant().toEpochMilli()
    val dates = entries.filter { it.timeStamp >= since }.map { it.localDate(now.zone) }
    if (dates.map(YearMonth::from).distinct().size < RECURRENCE_MIN_MONTHS) return null

    val days = dates.map { it.dayOfMonth }
    val typicalDay = (1..31).minBy { candidate -> days.sumOf { circularDayDistance(it, candidate) } }
    val consistent = days.count { circularDayDistance(it, typicalDay) <= RECURRENCE_DAY_TOLERANCE }
    if (consistent < days.size * RECURRENCE_MIN_SHARE) return null

    val currentMonth = YearMonth.from(now)
    val paidThisMonth = dates.any { YearMonth.from(it) == currentMonth }
    val nextMonth = if (paidThisMonth) currentMonth.plusMonths(1) else currentMonth
    return Recurrence(
        typicalDay = typicalDay,
        nextExpected = nextMonth.atDayClamped(typicalDay)
    )
}

private val CSV_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

/** Quotes a CSV field, and defuses text a spreadsheet would otherwise run as a formula. */
private fun csvField(value: String): String {
    val safe = if (value.firstOrNull() in FORMULA_START) "'$value" else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

private fun Float.csvNumber() = String.format(Locale.ROOT, "%.2f", this)

/**
 * CSV of [entries] as shown (converted), oldest first, with each entry's stored amount and
 * currency from [originalOf] alongside.
 */
fun entriesToCsv(
    entries: List<History>,
    zone: ZoneId,
    originalOf: (History) -> History = { it }
): String = buildString {
    appendLine(
        "date,name,type,amount,currency,original_amount,original_currency," +
            "maser_percentage,maser_effect,has_attachment"
    )
    entries.sortedBy { it.timeStamp }.forEach { entry ->
        val original = originalOf(entry)
        appendLine(
            listOf(
                Instant.ofEpochMilli(entry.timeStamp).atZone(zone).format(CSV_DATE_TIME),
                csvField(entry.name),
                if (entry.isDonation) "donation" else "income",
                entry.amount.csvNumber(),
                entry.currency,
                original.amount.csvNumber(),
                original.currency,
                entry.maserPercentage.csvNumber(),
                entry.maserDelta.csvNumber(),
                entry.hasAttachment.toString()
            ).joinToString(",")
        )
    }
}
