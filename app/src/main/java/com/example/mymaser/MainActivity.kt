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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvTotal = findViewById<TextView>(R.id.TV_total_maser)
        val etIncome = findViewById<EditText>(R.id.ET_income)
        val btSave = findViewById<Button>(R.id.BT_save)
        var totalMaser = getSharedPreferences(shardPref, MODE_PRIVATE)
            .getFloat(totalMaserTXT, 0F)

        tvTotal.text = totalMaser.toString()
        btSave.setBackgroundColor(Color.GRAY)
        etIncome.addTextChangedListener {
            btSave.isClickable = !it.isNullOrEmpty()
            btSave.setBackgroundColor(
                if (it.isNullOrEmpty()) Color.GRAY else resources.getColor(R.color.purple_500)
            )
        }
        btSave.setOnClickListener {
            totalMaser += etIncome.text.toString().toFloat() / 10
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
        btSave.isClickable = false
    }
}