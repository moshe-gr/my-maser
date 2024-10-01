package com.example.mymaser.history

import android.icu.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryRepository {
    companion object {
        lateinit var historyDao: HistoryDao
        private val calendar = Calendar.getInstance()

        fun getAllHistory(): List<History> = historyDao.getAll()

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

        fun getAllHistoryGroupedByMonth(): Map<String, List<History>> {
            val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

            return getAllHistory().reversed().groupBy {
                val date = Date(it.timeStamp)
                formatter.format(date)
            }
        }
    }
}