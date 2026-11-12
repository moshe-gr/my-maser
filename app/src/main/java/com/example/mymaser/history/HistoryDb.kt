package com.example.mymaser.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.example.mymaser.currency.ExchangeRate
import com.example.mymaser.currency.ExchangeRateDao
import com.example.mymaser.gmail.PendingImport
import com.example.mymaser.gmail.PendingImportDao

@Database(
    entities = [History::class, ExchangeRate::class, PendingImport::class],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = HistoryDb.DropAttachments::class),
        AutoMigration(from = 5, to = 6)
    ]
)
abstract class HistoryDb : RoomDatabase() {
    abstract fun getHistoryDao(): HistoryDao
    abstract fun getExchangeRateDao(): ExchangeRateDao
    abstract fun getPendingImportDao(): PendingImportDao

    @DeleteColumn(tableName = "History", columnName = "attachmentUri")
    class DropAttachments : AutoMigrationSpec

    companion object {
        private const val DB_NAME = "history.db"

        @Volatile
        private var instance: HistoryDb? = null

        fun getInstance(context: Context): HistoryDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDb::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
    }
}
