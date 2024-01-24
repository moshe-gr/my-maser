package com.example.mymaser.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [History::class], version = 1)
abstract class HistoryDb : RoomDatabase() {
    abstract fun getHistoryDao(): HistoryDao
}