package com.example.mymaser.reminders

import android.content.Context
import android.content.Intent
import com.example.mymaser.MainActivity
import com.example.mymaser.history.Prefill

enum class AppDestination { Home, Donation, History }

/** Where a notification takes the user inside the app. */
sealed interface DeepLink {
    data class OpenForm(val prefill: Prefill) : DeepLink
    data class Open(val destination: AppDestination) : DeepLink
}

private const val EXTRA_LINK = "com.example.mymaser.LINK"
private const val EXTRA_NAME = "com.example.mymaser.NAME"
private const val EXTRA_AMOUNT = "com.example.mymaser.AMOUNT"
private const val EXTRA_CURRENCY = "com.example.mymaser.CURRENCY"
private const val EXTRA_IS_DONATION = "com.example.mymaser.IS_DONATION"
private const val EXTRA_IS_TAX_ONLY = "com.example.mymaser.IS_TAX_ONLY"
private const val EXTRA_TAX_CREDIT = "com.example.mymaser.TAX_CREDIT"
private const val EXTRA_DESTINATION = "com.example.mymaser.DESTINATION"

private const val LINK_FORM = "form"
private const val LINK_OPEN = "open"

fun DeepLink.toIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        when (val link = this@toIntent) {
            is DeepLink.OpenForm -> {
                putExtra(EXTRA_LINK, LINK_FORM)
                putExtra(EXTRA_NAME, link.prefill.name)
                link.prefill.amount?.let { putExtra(EXTRA_AMOUNT, it) }
                link.prefill.currency?.let { putExtra(EXTRA_CURRENCY, it) }
                putExtra(EXTRA_IS_DONATION, link.prefill.isDonation)
                putExtra(EXTRA_IS_TAX_ONLY, link.prefill.isTaxOnly)
                putExtra(EXTRA_TAX_CREDIT, link.prefill.taxCredit)
            }
            is DeepLink.Open -> {
                putExtra(EXTRA_LINK, LINK_OPEN)
                putExtra(EXTRA_DESTINATION, link.destination.name)
            }
        }
    }

/** Reads a link from an intent built by [toIntent]; anything else gives null. */
fun Intent.toDeepLink(): DeepLink? = when (getStringExtra(EXTRA_LINK)) {
    LINK_FORM -> getStringExtra(EXTRA_NAME)?.let { name ->
        DeepLink.OpenForm(
            Prefill(
                name = name,
                amount = if (hasExtra(EXTRA_AMOUNT)) getFloatExtra(EXTRA_AMOUNT, 0F) else null,
                currency = getStringExtra(EXTRA_CURRENCY),
                isDonation = getBooleanExtra(EXTRA_IS_DONATION, false),
                isTaxOnly = getBooleanExtra(EXTRA_IS_TAX_ONLY, false),
                taxCredit = getBooleanExtra(EXTRA_TAX_CREDIT, false)
            )
        )
    }
    LINK_OPEN -> getStringExtra(EXTRA_DESTINATION)
        ?.let { name -> AppDestination.entries.firstOrNull { it.name == name } }
        ?.let { DeepLink.Open(it) }
    else -> null
}
