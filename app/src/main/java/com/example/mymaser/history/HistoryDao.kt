package com.example.mymaser.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(history: History)

    @Query("SELECT * FROM History")
    fun getAll(): List<History>

    @Delete
    fun delete(history: History)

    @Query("DELETE FROM History")
    fun deleteAll()

    @Update
    fun update(history: History)

    @Query("SELECT * FROM History ORDER BY timeStamp DESC LIMIT 1")
    fun getLastHistory(): History?

    @Query("SELECT * FROM History WHERE isDonation == :isDonation ORDER BY timeStamp DESC LIMIT 1")
    fun getLastHistoryBtType(isDonation: Boolean): History?

    @Query("SELECT SUM(amount) FROM History WHERE isDonation == :isDonation AND timeStamp >= :startOfMonth AND timeStamp < :endOfMonth")
    fun getHistoryForLastMonth(startOfMonth: Long, endOfMonth: Long, isDonation: Boolean): Double

    @Query("SELECT SUM(amount) FROM History WHERE isDonation == :isDonation AND timeStamp >= :startOfYear AND timeStamp < :endOfYear")
    fun getHistoryForLastYear(startOfYear: Long, endOfYear: Long, isDonation: Boolean): Double

    @Query("SELECT SUM(amount) FROM History WHERE isDonation")
    fun getTotalMaser(): Double

    @Query("SELECT name FROM History WHERE isDonation == :isDonation ORDER BY CASE WHEN strftime('%d', timeStamp / 1000, 'unixepoch') = strftime('%d', 'now') THEN 0 ELSE 1 END, name ASC")
    fun getAllNamesByType(isDonation: Boolean): List<String>

    @Query("SELECT amount FROM History WHERE name == :name AND isDonation == :isDonation")
    fun getAmountByName(name: String, isDonation: Boolean): List<Double>

}
