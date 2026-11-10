package com.example.mymaser.settings

import android.content.Context
import com.example.mymaser.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val DEFAULT_CURRENCY_SYMBOL = "₪"
const val DEFAULT_MASER_PERCENTAGE = 10f
const val MIN_MASER_PERCENTAGE = 1f
const val MAX_MASER_PERCENTAGE = 100f
val PRESET_MASER_PERCENTAGES = listOf(10f, 15f, 20f)

enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    val currencySymbol: String = DEFAULT_CURRENCY_SYMBOL
)

fun Context.appearanceFlow(): Flow<Appearance> = dataStore.data
    .map {
        Appearance(
            themeMode = ThemeMode.fromKey(it[SettingsKeys.THEME_MODE]),
            currencySymbol = it[SettingsKeys.CURRENCY_SYMBOL] ?: DEFAULT_CURRENCY_SYMBOL
        )
    }
    .distinctUntilChanged()

fun Context.maserPercentageFlow(): Flow<Float> = dataStore.data
    .map { it[SettingsKeys.MASER_PERCENTAGE] ?: DEFAULT_MASER_PERCENTAGE }
    .distinctUntilChanged()

fun isValidMaserPercentage(value: Float) = value in MIN_MASER_PERCENTAGE..MAX_MASER_PERCENTAGE

/** "10%" for whole values, "12.5%" otherwise. */
fun formatPercentage(value: Float): String =
    if (value == value.toLong().toFloat()) "${value.toLong()}%" else "$value%"
