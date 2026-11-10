package com.example.mymaser.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timeStamp: Long = 0,
    val name: String = "",
    val amount: Float = 0F,
    val isDonation: Boolean = false,
    val attachmentUri: String? = null
)
