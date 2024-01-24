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
//    fun getById(id: Int): History?
//    fun getByDate(date: String): History?
//    fun getByDateAndTime(date: String, time: String): History?
//    fun getByDateAndTimeAndPlace(date: String, time: String, place: String): History?
//    fun getByDateAndTimeAndPlaceAndType(date: String, time: String, place: String, type: String): History?
//    fun getByDateAndTimeAndPlaceAndTypeAndDescription(date: String, time: String, place: String, type: String, description: String): History?
//    fun getByDateAndTimeAndPlaceAndTypeAndDescriptionAndImage(date: String, time: String, place: String, type: String, description: String, image: String): History?
}
