package com.example.mymaser.gui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.formatCurrency
import com.example.mymaser.gui.theme.tabular
import com.example.mymaser.history.History
import com.example.mymaser.history.HistoryRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Keeps each row's height while totals load, so the sheet measures its full height up front. */
private const val LOADING_PLACEHOLDER = "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySummarySheet(
    history: History,
    loadSummary: suspend (History) -> HistoryRepository.HistorySummary,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<HistoryRepository.HistorySummary?>(null) }

    LaunchedEffect(history) {
        summary = loadSummary(history)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(history.name, style = MaterialTheme.typography.headlineSmall)

            SummaryRow(stringResource(R.string.amount), formatCurrency(history.amount.toDouble()))
            SummaryRow(
                stringResource(R.string.type),
                stringResource(if (history.isDonation) R.string.donation else R.string.income)
            )
            SummaryRow(
                stringResource(R.string.date),
                DateFormat.getDateInstance().format(Date(history.timeStamp))
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val loaded = summary
            val money: @Composable (Double?) -> String = { value ->
                value?.let { formatCurrency(it) } ?: LOADING_PLACEHOLDER
            }
            SummaryRow(stringResource(R.string.total), money(loaded?.totalByName))
            SummaryRow(
                stringResource(R.string.entries_count),
                loaded?.count?.toString() ?: LOADING_PLACEHOLDER
            )
            SummaryRow(stringResource(R.string.this_month), money(loaded?.monthTotal))
            SummaryRow(stringResource(R.string.this_year), money(loaded?.yearTotal))
            SummaryRow(stringResource(R.string.last_year), money(loaded?.lastYearTotal))
            SummaryRow(stringResource(R.string.average), money(loaded?.average))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.edit), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDelete() }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.delete), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge.tabular)
    }
}
