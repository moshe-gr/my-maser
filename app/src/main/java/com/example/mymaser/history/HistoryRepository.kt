package com.example.mymaser.history

import android.content.Context
import android.icu.util.Calendar
import com.example.mymaser.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryRepository {
    companion object {
        lateinit var historyDao: HistoryDao
        private val calendar = Calendar.getInstance()

        private fun getAllHistory(): List<History> = historyDao.getAll()

        fun getHistoryForLastMonth(isDonation: Boolean): Double {
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH))
            val startOfMonth = calendar.timeInMillis
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val endOfMonth = calendar.timeInMillis
            return historyDao.getHistoryForLastMonth(startOfMonth, endOfMonth, isDonation)
        }

        fun getHistoryForLastYear(isDonation: Boolean): Double {
            calendar.set(Calendar.DAY_OF_YEAR, 1)
            val startOfYear = calendar.timeInMillis
            calendar.set(Calendar.DAY_OF_YEAR, calendar.getActualMaximum(Calendar.DAY_OF_YEAR))
            val endOfYear = calendar.timeInMillis
            return historyDao.getHistoryForLastYear(startOfYear, endOfYear, isDonation)
        }

        fun getLastActionTime(): String = historyDao.getLastHistory()?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(
                Date(it.timeStamp)
            )
        } ?: "No actions yet"

        fun getLastHistoryByType(isDonation: Boolean) = historyDao.getLastHistoryBtType(isDonation)

        fun getTotalMaser(): Double = historyDao.getTotalMaser()

        fun saveHistory(name: String, amount: Float, isDonation: Boolean) = historyDao.insert(
            History().apply {
                this.name = name
                this.amount = amount
                this.isDonation = isDonation
                this.timeStamp = System.currentTimeMillis()
            }
        )

        fun groupHistoryByTimePeriod(context: Context): Map<String, List<History>> {
            val today = Calendar.getInstance()
            val thisWeekStart = (today.clone() as Calendar).apply {
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val thisMonthStart = (today.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return getAllHistory().reversed().groupBy {
                val historyDate = Calendar.getInstance().apply { timeInMillis = it.timeStamp }
                when {
                    isSameDay(today, historyDate) -> context.getString(R.string.today)
                    historyDate.after(thisWeekStart) -> context.getString(R.string.this_week)
                    historyDate.after(thisMonthStart) -> context.getString(R.string.this_monthh)
                    else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(historyDate.time)
                }
            }
        }

        private fun isSameDay(calendar1: Calendar, calendar2: Calendar): Boolean {
            return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                    calendar1.get(Calendar.DAY_OF_YEAR) == calendar2.get(Calendar.DAY_OF_YEAR)
        }

        fun getAllNamesByType(isDonation: Boolean) = historyDao.getAllNamesByType(isDonation).distinct()

        fun getAmountByName(name: String, isDonation: Boolean): Double = historyDao.getAmountByName(name, isDonation).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0.0

        fun updateHistory(history: History) = historyDao.update(history)
    }
}