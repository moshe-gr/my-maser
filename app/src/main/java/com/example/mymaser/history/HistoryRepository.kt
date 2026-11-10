package com.example.mymaser.history

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    fun observeHistory(): Flow<List<History>> = dao.observeAll()

    suspend fun insert(history: History): Int = dao.insert(history).toInt()

    suspend fun update(history: History) = dao.update(history)

    suspend fun delete(history: History) = dao.delete(history)
}
