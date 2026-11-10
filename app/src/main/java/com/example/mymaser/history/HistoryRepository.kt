package com.example.mymaser.history

import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class HistoryRepository(private val dao: HistoryDao) {

    fun observeHistory(): Flow<List<History>> = dao.observeAll()

    fun observeNames(isDonation: Boolean): Flow<List<String>> =
        dao.observeNamesByType(isDonation).map { it.distinct() }.distinctUntilChanged()

    suspend fun insert(history: History): Int = dao.insert(history).toInt()

    suspend fun update(history: History) = dao.update(history)

    suspend fun delete(history: History) = dao.delete(history)

    suspend fun suggestedAmount(name: String, isDonation: Boolean): Float =
        mostLikelyAmount(dao.getByName(name, isDonation), ZonedDateTime.now())

    suspend fun loadSummary(history: History): HistorySummary {
        val now = ZonedDateTime.now()
        val totalByName = dao.getTotalByName(history.name, history.isDonation)
        val count = dao.getCountByName(history.name, history.isDonation)

        suspend fun totalIn(range: TimeRange) = dao.getTotalByNameInRange(
            history.name,
            history.isDonation,
            range.start,
            range.endExclusive
        )

        return HistorySummary(
            totalByName = totalByName,
            count = count,
            monthTotal = totalIn(monthRange(now)),
            yearTotal = totalIn(yearRange(now)),
            lastYearTotal = totalIn(previousYearRange(now)),
            average = if (count > 0) totalByName / count else 0.0
        )
    }

    data class HistorySummary(
        val totalByName: Double,
        val count: Int,
        val monthTotal: Double,
        val yearTotal: Double,
        val lastYearTotal: Double,
        val average: Double
    )
}
