package com.example.mymaser.ai

import com.example.mymaser.history.History
import com.example.mymaser.history.YearEndForecast
import com.example.mymaser.history.bySource
import com.example.mymaser.history.detectRecurrence
import com.example.mymaser.history.localDate
import com.example.mymaser.history.maserDelta
import com.example.mymaser.history.nameKey
import java.time.LocalDate
import java.time.Month
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

/**
 * What the chat may look at: entries already converted to [currency], and the figures Home shows.
 * Only aggregates computed from this are ever sent to the model.
 */
data class ChatData(
    val entries: List<History>,
    val currency: String,
    val balance: Double,
    val forecast: YearEndForecast?,
    val now: ZonedDateTime
)

/** A tool the model can call; [parameters] maps each argument to its description. */
data class ChatTool(
    val name: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(),
    val optional: List<String> = parameters.keys.toList()
)

object ChatTools {
    const val TOTALS = "get_totals"
    const val BALANCE = "get_balance"
    const val MONTHLY = "get_monthly"
    const val RECURRING = "get_recurring"

    private const val TOP_NAMES = 5

    val all = listOf(
        ChatTool(
            TOTALS,
            "Income and donation totals for a date range, optionally for one source or recipient, " +
                "with the maser the income created and the biggest sources and recipients.",
            mapOf(
                "from" to "First day, YYYY-MM-DD. Omit for the start of all records.",
                "to" to "Last day, YYYY-MM-DD, inclusive. Omit for today.",
                "name" to "Only entries whose source or recipient name contains this text."
            )
        ),
        ChatTool(
            BALANCE,
            "Maser still left to give now, and the projected balance on December 31 from recurring entries."
        ),
        ChatTool(
            MONTHLY,
            "Income, donation and maser totals for each month of a year.",
            mapOf("year" to "The year, e.g. 2026. Omit for this year.")
        ),
        ChatTool(RECURRING, "Income and donations that repeat monthly, with their usual day and amount.")
    )

    const val SYSTEM_PROMPT = """
You answer questions about the user's own ma'aser (tithe) records in a personal tracker app.
Always get numbers from the tools; never guess or invent figures. Amounts are in the currency the
tools return. Today's date is given with each question. Reply briefly, in the language the user
wrote in. If the records can't answer something, say so. Don't give halachic rulings; suggest
asking a rabbi for those.
"""

    /** Runs [name] over [data]; unknown tools and bad arguments come back as an error field. */
    fun run(name: String, args: Map<String, String?>, data: ChatData): Map<String, Any?> = try {
        when (name) {
            TOTALS -> totals(args, data)
            BALANCE -> balance(data)
            MONTHLY -> monthly(args, data)
            RECURRING -> recurring(data)
            else -> mapOf("error" to "Unknown tool $name")
        }
    } catch (e: IllegalArgumentException) {
        mapOf("error" to e.message)
    }

    private fun totals(args: Map<String, String?>, data: ChatData): Map<String, Any?> {
        val zone = data.now.zone
        val from = args["from"]?.let(::date)
        val to = args["to"]?.let(::date) ?: data.now.toLocalDate()
        val filter = args["name"]?.takeIf { it.isNotBlank() }?.let(::nameKey)
        val chosen = data.entries.filter { entry ->
            val day = entry.localDate(zone)
            (from == null || !day.isBefore(from)) && !day.isAfter(to) &&
                (filter == null || nameKey(entry.name).contains(filter))
        }
        val (donations, incomes) = chosen.partition { it.isDonation }
        return mapOf(
            "currency" to data.currency,
            "from" to (from?.toString() ?: "all records"),
            "to" to to.toString(),
            "income_total" to incomes.total(),
            "income_count" to incomes.size,
            "maser_from_income" to round(incomes.sumOf { it.maserDelta.toDouble() }),
            "donation_total" to donations.total(),
            "donation_count" to donations.size,
            "top_income_sources" to incomes.topNames(),
            "top_donation_recipients" to donations.topNames()
        )
    }

    private fun balance(data: ChatData): Map<String, Any?> = mapOf(
        "currency" to data.currency,
        "maser_left_to_give" to round(data.balance),
        "projected_on_december_31" to data.forecast?.balance?.let(::round),
        "monthly_to_finish_at_zero" to data.forecast?.monthlyToClear?.let(::round)
    )

    private fun monthly(args: Map<String, String?>, data: ChatData): Map<String, Any?> {
        val year = args["year"]?.let {
            requireNotNull(it.trim().toIntOrNull()) { "year must be a number like 2026" }
        } ?: data.now.year
        val zone = data.now.zone
        val inYear = data.entries.groupBy { it.localDate(zone) }.filterKeys { it.year == year }
        val months = Month.entries.map { month ->
            val entries = inYear.filterKeys { it.month == month }.values.flatten()
            val (donations, incomes) = entries.partition { it.isDonation }
            mapOf(
                "month" to month.value,
                "income" to incomes.total(),
                "donations" to donations.total(),
                "maser_from_income" to round(incomes.sumOf { it.maserDelta.toDouble() })
            )
        }
        return mapOf("currency" to data.currency, "year" to year, "months" to months)
    }

    private fun recurring(data: ChatData): Map<String, Any?> {
        val items = data.entries.bySource().values.mapNotNull { entries ->
            val recurrence = detectRecurrence(entries, data.now) ?: return@mapNotNull null
            val latest = entries.maxBy { it.timeStamp }
            mapOf(
                "name" to latest.name.trim(),
                "kind" to if (latest.isDonation) "donation" else "income",
                "usual_day_of_month" to recurrence.typicalDay,
                "last_amount" to round(latest.amount.toDouble()),
                "next_expected" to recurrence.nextExpected.toString()
            )
        }
        return mapOf("currency" to data.currency, "recurring" to items)
    }

    private fun List<History>.total() = round(sumOf { it.amount.toDouble() })

    private fun List<History>.topNames(): List<Map<String, Any>> =
        groupBy { nameKey(it.name) }.values
            .map { group -> Triple(group.first().name.trim(), group.total(), group.size) }
            .sortedByDescending { it.second }
            .take(TOP_NAMES)
            .map { (name, total, count) -> mapOf("name" to name, "total" to total, "count" to count) }

    private fun date(text: String): LocalDate = try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        throw IllegalArgumentException("dates must look like 2026-01-31, got $text")
    }

    private fun round(value: Double): Double = (value * 100).roundToLong() / 100.0
}
