package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.history.History
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.sanitizeAmountInput

@Composable
fun EditEntryDialog(
    entry: History,
    onConfirm: (name: String, amount: Float, isTaxOnly: Boolean, taxCredit: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }
    var amountText by rememberSaveable(entry.id) {
        mutableStateOf(formatAmountInput(entry.amount))
    }
    var isTaxOnly by rememberSaveable(entry.id) { mutableStateOf(entry.isTaxOnly) }
    var taxCredit by rememberSaveable(entry.id) { mutableStateOf(entry.taxCredit) }
    val amount = amountText.toFloatOrNull() ?: 0F
    val isValid = amount > 0F && name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> sanitizeAmountInput(input)?.let { amountText = it } },
                    label = { Text(stringResource(R.string.amount)) },
                    suffix = { Text(SupportedCurrency.symbolFor(entry.currency)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (entry.isDonation) {
                    DonationTypeSelector(
                        isTaxOnly = isTaxOnly,
                        onChange = {
                            isTaxOnly = it
                            if (it) taxCredit = true
                        }
                    )
                    TaxCreditSwitch(checked = taxCredit, onChange = { taxCredit = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), amount, isTaxOnly, taxCredit) }, enabled = isValid) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
