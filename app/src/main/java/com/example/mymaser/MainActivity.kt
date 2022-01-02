package com.example.mymaser

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener

private const val totalMaserTXT = "total maser"
private const val shardPref = "shardPref"

class MainActivity : AppCompatActivity() {
    private var donate = false
    private var save = false
    private lateinit var tvTotal: TextView
    private lateinit var etIncome: EditText
    private lateinit var btSave: Button
    private var totalMaser = 0F
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTotal = findViewById(R.id.TV_total_maser)
        etIncome = findViewById(R.id.ET_income)
        btSave = findViewById(R.id.BT_save)
        totalMaser = getSharedPreferences(shardPref, MODE_PRIVATE)
            .getFloat(totalMaserTXT, 0F)

        tvTotal.text = totalMaser.toString()
        etIncome.addTextChangedListener {
            save = !it.isNullOrEmpty()
            btSave.setBackgroundColor(
                if (save) Color.RED else resources.getColor(R.color.purple_500)
            )
            when {
                save -> btSave.setText(R.string.save)
                donate -> {
                    btSave.setText(R.string.income)
                    etIncome.setHint(R.string.type_donation)
                }
                else -> {
                    btSave.setText(R.string.donate)
                    etIncome.setHint(R.string.type_income)
                }
            }
        }
        btSave.setOnClickListener {
            if (save) {
                maserChange()
            } else {
                actionChange()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("donate", donate)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        donate = savedInstanceState.getBoolean("donate")
        if (!save && donate) {
            btSave.setText(R.string.income)
            etIncome.setHint(R.string.type_donation)
        }
    }

    private fun maserChange() {
        if (donate) {
            totalMaser -= etIncome.text.toString().toFloat()
        } else {
            totalMaser += etIncome.text.toString().toFloat() / 10
        }
        tvTotal.text = totalMaser.toString()
        etIncome.text.clear()
        getSharedPreferences(shardPref, MODE_PRIVATE)
            .edit()
            .putFloat(
                totalMaserTXT,
                totalMaser
            )
            .apply()
    }

    private fun actionChange() {
        if (donate) {
            btSave.setText(R.string.donate)
            etIncome.setHint(R.string.type_income)
        } else {
            btSave.setText(R.string.income)
            etIncome.setHint(R.string.type_donation)
        }
        donate = !donate
    }
}