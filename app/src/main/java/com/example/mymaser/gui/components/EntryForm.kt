package com.example.mymaser.gui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.history.History
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.maserDelta
import com.example.mymaser.history.sanitizeAmountInput
import kotlinx.coroutines.launch

private const val MAX_SUGGESTIONS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(
    isDonation: Boolean,
    names: List<String>,
    lastEntry: History?,
    onSuggestionPicked: suspend (String) -> Float,
    onSave: (String, Float) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val amount = amountText.toFloatOrNull() ?: 0F
    val canSave = amount > 0F && name.isNotBlank()
    val suggestions = remember(names, name) {
        val query = name.trim()
        names.filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
    }

    fun submit() {
        if (!canSave) return
        focusManager.clearFocus()
        onSave(name.trim(), amount)
        name = ""
        amountText = ""
    }

    ScreenColumn(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        }
    ) {
        ExposedDropdownMenuBox(
            expanded = menuOpen && suggestions.isNotEmpty(),
            onExpandedChange = { menuOpen = it && name.isNotBlank() }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    menuOpen = it.isNotBlank()
                },
                label = {
                    Text(
                        stringResource(if (isDonation) R.string.donate_to else R.string.source_of_income)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .onFocusChanged { if (!it.isFocused) menuOpen = false },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    menuOpen = false
                    focusManager.moveFocus(FocusDirection.Down)
                })
            )
            ExposedDropdownMenu(
                expanded = menuOpen && suggestions.isNotEmpty(),
                onDismissRequest = { menuOpen = false }
            ) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = {
                            Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        onClick = {
                            name = suggestion
                            menuOpen = false
                            focusManager.moveFocus(FocusDirection.Down)
                            scope.launch {
                                amountText = formatAmountInput(onSuggestionPicked(suggestion))
                            }
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { input -> sanitizeAmountInput(input)?.let { amountText = it } },
            label = {
                Text(stringResource(if (isDonation) R.string.type_donation else R.string.type_income))
            },
            supportingText = if (!isDonation && amount > 0F) {
                {
                    Text(
                        stringResource(
                            R.string.adds_to_maser,
                            formatCurrency(maserDelta(amount, false).toDouble())
                        )
                    )
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                submit()
            })
        )

        Button(
            onClick = ::submit,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(stringResource(if (isDonation) R.string.confirm else R.string.save))
        }

        LastEntryRow(isDonation, lastEntry)
    }
}

@Composable
private fun LastEntryRow(isDonation: Boolean, lastEntry: History?) {
    val details = lastEntry?.let {
        stringResource(
            R.string.last_income_donation_info,
            it.name,
            formatCurrency(it.amount.toDouble())
        )
    } ?: stringResource(R.string.no_information_yet)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    if (isDonation) R.string.last_donation_info else R.string.last_income_info,
                    details
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
