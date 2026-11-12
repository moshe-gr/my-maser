package com.example.mymaser.reminders

import com.example.mymaser.history.DueStatus
import com.example.mymaser.history.History
import com.example.mymaser.history.Prediction
import com.example.mymaser.history.hasAttachment
import com.example.mymaser.history.localDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

data class ReminderSettings(
    val enabled: Boolean = false,
    val expectedEntries: Boolean = true,
    val balance: Boolean = true,
    val receipts: Boolean = true,
    val yearInReview: Boolean = true
)

/** Something worth a notification. [key] makes sure each one is sent once. */
sealed interface Reminder {
    val key: String

    data class ExpectedEntry(val prediction: Prediction) : Reminder {
        override val key = "expected|${prediction.key}"
    }

    data class Balance(val amount: Double, val currency: String, val yearEnd: Boolean, val period: String) : Reminder {
        override val key = (if (yearEnd) "balance-year|" else "balance|") + period
    }

    data class MissingReceipts(val count: Int, val year: Int) : Reminder {
        override val key = "receipts|$year"
    }

    data class YearInReview(
        val year: Int,
        val total: Double,
        val previousTotal: Double,
        val currency: String
    ) : Reminder {
        override val key = "review|$year"
    }
}

/** Everything the rules look at; [history] is already in [displayCurrency] and convertible. */
data class ReminderInput(
    val history: List<History>,
    val balance: Double,
    val displayCurrency: String,
    val predictions: List<Prediction>,
    val settings: ReminderSettings,
    val alreadySent: Set<String>,
    val now: ZonedDateTime
)

private const val MIN_BALANCE = 0.5
private const val QUIET_DONATION_DAYS = 30L
private val MONTHLY_WINDOW = 1..7
private val YEAR_END_WINDOW = 15..31
private val REVIEW_WINDOW = 2..14

private fun History.date(input: ReminderInput): LocalDate = localDate(input.now.zone)

/**
 * Decides which reminders to post today. Each rule fires inside a window of days rather than on
 * one exact day, so a skipped daily run doesn't lose it; [ReminderInput.alreadySent] keeps it to
 * once.
 */
fun computeReminders(input: ReminderInput): List<Reminder> {
    val settings = input.settings
    if (!settings.enabled) return emptyList()
    val today = input.now.toLocalDate()
    val reminders = mutableListOf<Reminder>()

    if (settings.expectedEntries) {
        input.predictions
            .filter { it.status == DueStatus.Overdue }
            .mapTo(reminders) { Reminder.ExpectedEntry(it) }
    }

    if (settings.balance && input.balance > MIN_BALANCE) {
        val isDecember = today.monthValue == 12
        if (isDecember && today.dayOfMonth in YEAR_END_WINDOW) {
            reminders += Reminder.Balance(input.balance, input.displayCurrency, true, "${today.year}")
        } else if (today.dayOfMonth in MONTHLY_WINDOW) {
            val lastDonation = input.history.filter { it.isDonation }.maxOfOrNull { it.date(input) }
            val quiet = lastDonation == null || lastDonation.isBefore(today.minusDays(QUIET_DONATION_DAYS))
            if (quiet) {
                val month = YearMonth.from(today).toString()
                reminders += Reminder.Balance(input.balance, input.displayCurrency, false, month)
            }
        }
    }

    if (settings.receipts && today.monthValue == 12) {
        val missing = input.history.count {
            it.isDonation && !it.hasAttachment && it.date(input).year == today.year
        }
        if (missing > 0) reminders += Reminder.MissingReceipts(missing, today.year)
    }

    if (settings.yearInReview && today.monthValue == 1 && today.dayOfMonth in REVIEW_WINDOW) {
        fun donatedIn(year: Int) = input.history
            .filter { it.isDonation && it.date(input).year == year }
            .sumOf { it.amount.toDouble() }
        val total = donatedIn(today.year - 1)
        if (total > 0) {
            reminders += Reminder.YearInReview(
                year = today.year - 1,
                total = total,
                previousTotal = donatedIn(today.year - 2),
                currency = input.displayCurrency
            )
        }
    }

    return reminders.filterNot { it.key in input.alreadySent }
}

private val YEAR_IN_KEY = Regex("(\\d{4})")

/** Sent-reminder keys from this year or last; older ones can never fire again. */
fun pruneSentKeys(keys: Set<String>, today: LocalDate): Set<String> =
    keys.filterTo(mutableSetOf()) { key ->
        val year = YEAR_IN_KEY.findAll(key).lastOrNull()?.value?.toIntOrNull()
        year == null || year >= today.year - 1
    }
