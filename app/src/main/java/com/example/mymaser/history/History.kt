package com.example.mymaser.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class History {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
    var timeStamp: Long = 0
    var name: String = ""
    var amount: Float = 0F
    var isDonation: Boolean = false
}
