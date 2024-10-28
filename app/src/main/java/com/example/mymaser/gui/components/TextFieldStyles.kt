package com.example.mymaser.gui.components

import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.example.mymaser.R

@Composable
fun myTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    focusedBorderColor = colorResource(id = R.color.colorPrimary),
    unfocusedBorderColor = colorResource(id = R.color.secondary_text),
    focusedLabelColor = colorResource(id = R.color.colorPrimary),
    unfocusedLabelColor = colorResource(id = R.color.secondary_text),
    cursorColor = colorResource(id = R.color.colorPrimary),
    textColor = colorResource(id = R.color.colorOnPrimary)
)