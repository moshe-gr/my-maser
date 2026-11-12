package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.currency.SupportedCurrency

@Composable
fun SupportedCurrency.label(): String = "$symbol  ${stringResource(nameRes)}"

/** A compact symbol button inside an amount field that opens the list of currencies. */
@Composable
fun CurrencyPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = SupportedCurrency.fromCode(selected)
    val description = stringResource(
        R.string.currency_picker_description,
        current?.let { stringResource(it.nameRes) } ?: selected
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Text(current?.symbol ?: selected)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SupportedCurrency.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    trailingIcon = if (option.code == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
