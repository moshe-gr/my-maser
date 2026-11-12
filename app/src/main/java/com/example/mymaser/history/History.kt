package com.example.mymaser.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mymaser.currency.DEFAULT_CURRENCY
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Entity
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeStamp: Long = 0,
    val name: String = "",
    val amount: Float = 0F,
    val isDonation: Boolean = false,
    /** The percentage in force when the entry was saved, so later edits reverse exactly what it added. */
    @ColumnInfo(defaultValue = "10")
    val maserPercentage: Float = DEFAULT_MASER_PERCENTAGE,
    /** ISO 4217 code of [amount]; entries keep their original currency and are converted for display. */
    @ColumnInfo(defaultValue = DEFAULT_CURRENCY)
    val currency: String = DEFAULT_CURRENCY
)

fun History.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(timeStamp).atZone(zone).toLocalDate()
