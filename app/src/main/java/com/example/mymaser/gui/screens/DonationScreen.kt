package com.example.mymaser.gui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.components.SuccessPopUp
import com.example.mymaser.gui.components.SuggestionsList
import com.example.mymaser.gui.components.myTextFieldColors
import com.example.mymaser.history.HistoryRepository.Companion.getAllNamesByType
import com.example.mymaser.history.HistoryRepository.Companion.getAmountByName
import com.example.mymaser.history.HistoryRepository.Companion.getLastHistoryByType
import com.example.mymaser.history.HistoryRepository.Companion.saveHistory
import kotlinx.coroutines.delay

@Composable
fun DonationScreen(onEdit: (Float) -> Unit) {
    var value by rememberSaveable { mutableFloatStateOf(0F) }
    var valueString by rememberSaveable { mutableStateOf("") }
    var donationReceiver by rememberSaveable { mutableStateOf("") }
    var lastDonation by remember { mutableStateOf(getLastHistoryByType(true)) }
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val pastSources = remember { getAllNamesByType(true) }
    var showSuccess by remember { mutableStateOf(false) }
    val suggestions = remember(donationReceiver) {
        pastSources.filter { it.contains(donationReceiver, ignoreCase = true) }
    }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(2000)
            showSuccess = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .clickable(interactionSource = null, indication = null) { focusManager.clearFocus() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Column {
            OutlinedTextField(
                value = donationReceiver,
                onValueChange = {
                    donationReceiver = it
                    expanded = it.isNotBlank()
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { expanded = it.isFocused && donationReceiver.isNotBlank() },
                label = { Text(text = stringResource(id = R.string.donate_to)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                }),
                colors = myTextFieldColors()
            )
            if (expanded && suggestions.isNotEmpty()) {
                SuggestionsList(suggestions = suggestions) {
                    donationReceiver = it
                    focusManager.moveFocus(FocusDirection.Down)
                    value = getAmountByName(it, true).toFloat()
                    valueString = value.toString()
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        OutlinedTextField(
            value = valueString,
            onValueChange = { v ->
                if (v.matches(Regex("^\\d*\\.?\\d{0,5}$"))) {
                    valueString = v
                    value = v.toFloatOrNull() ?: 0F
                }
            },
            modifier = Modifier.focusRequester(focusRequester),
            label = { Text(text = stringResource(id = R.string.type_donation)) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (value != 0F && donationReceiver.isNotBlank()) {
                    saveHistory(donationReceiver, value, true)
                    onEdit(-value)
                    lastDonation = getLastHistoryByType(true)
                    value = 0F
                    valueString = ""
                    donationReceiver = ""
                }
            }),
            colors = myTextFieldColors()
        )
        Spacer(modifier = Modifier.weight(1f))
        SuccessPopUp(showSuccess)
        Button(
            onClick = {
                saveHistory(donationReceiver, value, true)
                onEdit(-value)
                showSuccess = true
                lastDonation = getLastHistoryByType(true)
                value = 0F
                valueString = ""
                donationReceiver = ""
            },
            enabled = value != 0F && donationReceiver.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = colorResource(id = R.color.colorPrimary),
                contentColor = colorResource(id = R.color.colorOnPrimary)
            )
        ) {
            Text(text = stringResource(id = R.string.confirm))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = colorResource(id = R.color.colorPrimary),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(
                    id = R.string.last_donation_info,
                    lastDonation?.let { "${it.name} - ${it.amount}" }
                        ?: stringResource(id = R.string.no_information_yet)
                ),
                color = colorResource(id = R.color.text),
                style = MaterialTheme.typography.caption
            )
        }
    }
}