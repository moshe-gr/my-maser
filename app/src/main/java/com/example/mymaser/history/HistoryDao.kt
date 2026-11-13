package com.example.mymaser.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History): Long

    @Delete
    suspend fun delete(history: History)

    @Update
    suspend fun update(history: History)

    @Query("SELECT * FROM History ORDER BY timeStamp DESC")
    fun observeAll(): Flow<List<History>>

    @Query("SELECT * FROM History ORDER BY timeStamp DESC")
    suspend fun getAll(): List<History>

    @Query("UPDATE History SET taxCredit = :taxCredit WHERE id IN (:ids)")
    suspend fun setTaxCredit(ids: List<Int>, taxCredit: Boolean)

    @Query("UPDATE History SET currency = :currency")
    suspend fun setCurrencyForAll(currency: String)
}
