package com.example.mymaser.history

/** Values to open the income or donation form with. */
data class Prefill(
    val name: String,
    val amount: Float?,
    val currency: String?,
    val isDonation: Boolean
)

fun Prediction.toPrefill() = Prefill(name, amount, currency, isDonation)

/** A confirmed entry ready to store, with the time it happened. */
data class NewEntry(
    val name: String,
    val amount: Float,
    val currency: String,
    val isDonation: Boolean,
    val timeStamp: Long
)
