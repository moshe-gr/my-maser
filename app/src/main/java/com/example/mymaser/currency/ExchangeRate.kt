package com.example.mymaser.currency

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Units of [currency] per one euro on [epochDay]. */
@Entity(primaryKeys = ["epochDay", "currency"])
data class ExchangeRate(
    val epochDay: Long,
    val currency: String,
    val ratePerEur: Double
)

@Dao
interface ExchangeRateDao {

    @Query("SELECT * FROM ExchangeRate")
    fun observeAll(): Flow<List<ExchangeRate>>

    @Query("SELECT * FROM ExchangeRate")
    suspend fun getAll(): List<ExchangeRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<ExchangeRate>)

    @Query("SELECT MIN(epochDay) FROM ExchangeRate WHERE currency = :currency")
    suspend fun firstDay(currency: String): Long?

    @Query("SELECT MAX(epochDay) FROM ExchangeRate WHERE currency = :currency")
    suspend fun lastDay(currency: String): Long?
}
