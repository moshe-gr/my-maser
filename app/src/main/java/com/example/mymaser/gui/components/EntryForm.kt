package com.example.mymaser.gui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.mymaser.gui.LocalDisplayCurrency
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.gui.theme.MaserPreview
import com.example.mymaser.gui.theme.ScreenPreviews
import com.example.mymaser.history.AmountSuggestion
import com.example.mymaser.history.EntryWarning
import com.example.mymaser.history.History
import com.example.mymaser.history.Prefill
import com.example.mymaser.history.formatAmountInput
import com.example.mymaser.history.maserDelta
import com.example.mymaser.history.matchSuggestions
import com.example.mymaser.history.sanitizeAmountInput
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE

private const val MAX_SUGGESTIONS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(
    isDonation: Boolean,
    names: List<String>,
    lastEntry: History?,
    onSuggestionPicked: (String) -> AmountSuggestion?,
    onSave: (name: String, amount: Float, currency: String, isTaxOnly: Boolean, taxCredit: Boolean) -> Unit,
    maserPercentage: Float = DEFAULT_MASER_PERCENTAGE,
    taxCreditFor: (String) -> Boolean = { false },
    prefill: Prefill? = null,
    onPrefillConsumed: () -> Unit = {},
    onCheck: (name: String, amount: Float, currency: String) -> EntryWarning? = { _, _, _ -> null },
    header: @Composable () -> Unit = {}
) {
    val displayCurrency = LocalDisplayCurrency.current
    var warning by remember { mutableStateOf<EntryWarning?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var chosenCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var autoFilledAmount by rememberSaveable { mutableStateOf<String?>(null) }
    var isTaxOnly by rememberSaveable { mutableStateOf(false) }
    var taxCredit by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val currency = chosenCurrency ?: displayCurrency
    val amount = amountText.toFloatOrNull() ?: 0F
    val canSave = amount > 0F && name.isNotBlank()
    val suggestions = remember(names, name) { matchSuggestions(names, name, MAX_SUGGESTIONS) }

    fun pick(suggestion: String) {
        name = suggestion
        if (isDonation) taxCredit = isTaxOnly || taxCreditFor(suggestion)
        menuOpen = false
        focusManager.moveFocus(FocusDirection.Down)
        val amountIsOurs = amountText.isEmpty() || amountText == autoFilledAmount
        if (amountIsOurs) {
            val suggested = onSuggestionPicked(suggestion)?.takeIf { it.amount > 0F }
            amountText = suggested?.amount?.let(::formatAmountInput).orEmpty()
            if (suggested != null) chosenCurrency = suggested.currency
            autoFilledAmount = amountText
        }
    }

    LaunchedEffect(prefill) {
        if (prefill == null || prefill.isDonation != isDonation) return@LaunchedEffect
        name = prefill.name
        amountText = prefill.amount?.takeIf { it > 0F }?.let(::formatAmountInput).orEmpty()
        chosenCurrency = prefill.currency
        autoFilledAmount = amountText
        isTaxOnly = prefill.isTaxOnly
        taxCredit = prefill.taxCredit || (isDonation && taxCreditFor(prefill.name))
        menuOpen = false
        onPrefillConsumed()
    }

    fun save() {
        focusManager.clearFocus()
        onSave(name.trim(), amount, currency, isDonation && isTaxOnly, isDonation && taxCredit)
        name = ""
        amountText = ""
        chosenCurrency = null
        autoFilledAmount = null
        isTaxOnly = false
        taxCredit = false
    }

    fun submit() {
        if (!canSave) return
        warning = onCheck(name.trim(), amount, currency)
        if (warning == null) save()
    }

    warning?.let { current ->
        EntryWarningDialog(
            warning = current,
            name = name.trim(),
            amount = formatCurrency(amount.toDouble(), currencyCode = currency),
            onSaveAnyway = {
                warning = null
                save()
            },
            onFix = { warning = null }
        )
    }

    ScreenColumn(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        }
    ) {
        header()
        ExposedDropdownMenuBox(
            expanded = menuOpen && suggestions.isNotEmpty(),
            onExpandedChange = { menuOpen = it }
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    menuOpen = true
                },
                label = {
                    Text(
                        stringResource(if (isDonation) R.string.donate_to else R.string.source_of_income)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .onFocusChanged { menuOpen = it.isFocused },
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
                        onClick = { pick(suggestion) }
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
            supportingText = when {
                !isDonation && amount > 0F -> {
                    {
                        Text(
                            stringResource(
                                R.string.adds_to_maser,
                                formatCurrency(
                                    maserDelta(amount, false, maserPercentage).toDouble(),
                                    currencyCode = currency
                                )
                            )
                        )
                    }
                }
                isDonation && isTaxOnly -> {
                    { Text(stringResource(R.string.tax_only_hint)) }
                }
                else -> null
            },
            trailingIcon = {
                CurrencyPicker(selected = currency, onSelect = { chosenCurrency = it })
            },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(MaserMotion.contentSize),
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

        if (isDonation) {
            DonationTypeSelector(
                isTaxOnly = isTaxOnly,
                onChange = {
                    isTaxOnly = it
                    if (it) taxCredit = true
                }
            )
            TaxCreditSwitch(checked = taxCredit, onChange = { taxCredit = it })
        }

        Button(
            onClick = ::submit,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(stringResource(R.string.save))
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
            formatCurrency(it.amount.toDouble(), currencyCode = it.currency)
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

@ScreenPreviews
@Composable
private fun IncomeFormPreview() {
    MaserPreview {
        EntryForm(
            isDonation = false,
            names = emptyList(),
            lastEntry = History(name = "Salary", amount = 12000F),
            onSuggestionPicked = { null },
            onSave = { _, _, _, _, _ -> }
        )
    }
}

@ScreenPreviews
@Composable
private fun DonationFormPreview() {
    MaserPreview {
        EntryForm(
            isDonation = true,
            names = emptyList(),
            lastEntry = null,
            onSuggestionPicked = { null },
            onSave = { _, _, _, _, _ -> }
        )
    }
}
