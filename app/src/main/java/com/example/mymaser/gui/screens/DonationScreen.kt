package com.example.mymaser.gui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.history.HistoryRepository.Companion.getLastHistoryByType
import com.example.mymaser.history.HistoryRepository.Companion.saveHistory

@Composable
fun DonationScreen(onEdit: (Float) -> Unit) {
    var value by rememberSaveable {
        mutableFloatStateOf(0F)
    }

    var donationReceiver by rememberSaveable {
        mutableStateOf("")
    }

    var lastDonation by remember {
        mutableStateOf(getLastHistoryByType(true))
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = if (value != 0F) value.toString() else "",
            onValueChange = { v -> value = v.toFloatOrNull() ?: 0F },
            modifier = Modifier.focusRequester(focusRequester),
            label = { Text(text = stringResource(id = R.string.type_donation)) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            })
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = donationReceiver,
            onValueChange = { v -> donationReceiver = v },
            modifier = Modifier.focusRequester(focusRequester),
            label = { Text(text = stringResource(id = R.string.donate_to)) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (value != 0F && donationReceiver.isNotBlank()) {
                    saveHistory(donationReceiver, value, true)
                    onEdit(-value)
                    lastDonation = getLastHistoryByType(true)
                    value = 0F
                    donationReceiver = ""
                }
            })
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                saveHistory(donationReceiver, value, true)
                onEdit(-value)
                lastDonation = getLastHistoryByType(true)
            },
            enabled = value != 0F && donationReceiver.isNotBlank()
        ) {
            Text(text = stringResource(id = R.string.confirm))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                id = R.string.last_donation_info,
                lastDonation?.let { "${it.name} - ${it.amount}" } ?: stringResource(id = R.string.no_information_yet))
        )
    }
}