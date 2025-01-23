package com.example.mymaser.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [History::class], version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class HistoryDb : RoomDatabase() {
    abstract fun getHistoryDao(): HistoryDao

    companion object {
        private const val DB_NAME = "history.db"
        private var instance: HistoryDb? = null

        fun getInstance(context: Context): HistoryDb {
            return instance ?: run {
                instance = Room.databaseBuilder(context, HistoryDb::class.java, DB_NAME)
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                instance!!
            }
        }
    }

}