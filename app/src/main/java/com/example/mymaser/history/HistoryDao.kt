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

    @Query("SELECT name FROM History WHERE isDonation == :isDonation ORDER BY CASE WHEN strftime('%d', timeStamp / 1000, 'unixepoch') = strftime('%d', 'now') THEN 0 ELSE 1 END, name ASC")
    fun observeNamesByType(isDonation: Boolean): Flow<List<String>>

    @Query("SELECT * FROM History WHERE name == :name AND isDonation == :isDonation")
    suspend fun getByName(name: String, isDonation: Boolean): List<History>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM History WHERE name = :name AND isDonation = :isDonation AND timeStamp >= :start AND timeStamp < :endExclusive")
    suspend fun getTotalByNameInRange(
        name: String,
        isDonation: Boolean,
        start: Long,
        endExclusive: Long
    ): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM History WHERE name = :name AND isDonation = :isDonation")
    suspend fun getTotalByName(name: String, isDonation: Boolean): Double

    @Query("SELECT COUNT(*) FROM History WHERE name = :name AND isDonation = :isDonation")
    suspend fun getCountByName(name: String, isDonation: Boolean): Int
}
