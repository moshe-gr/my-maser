package com.example.mymaser.history

/** Values to open the income or donation form with. */
data class Prefill(
    val name: String,
    val amount: Float?,
    val currency: String?,
    val isDonation: Boolean,
    val isTaxOnly: Boolean = false
)

fun Prediction.toPrefill() = Prefill(name, amount, currency, isDonation, isTaxOnly)

/** A confirmed entry ready to store, with the time it happened. */
data class NewEntry(
    val name: String,
    val amount: Float,
    val currency: String,
    val isDonation: Boolean,
    val timeStamp: Long,
    val isTaxOnly: Boolean = false,
    /** How the imported document named the source, so a rename can be remembered. */
    val documentName: String? = null
)
