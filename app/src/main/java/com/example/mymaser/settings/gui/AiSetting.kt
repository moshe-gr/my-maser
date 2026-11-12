package com.example.mymaser.settings.gui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.components.ChipRow
import com.example.mymaser.settings.MaserBasis

@Composable
fun AiSetting(
    enabled: Boolean,
    basis: MaserBasis,
    onEnabledChange: (Boolean) -> Unit,
    onBasisChange: (MaserBasis) -> Unit
) {
    Column {
        SwitchRow(
            title = stringResource(R.string.ai_enabled),
            subtitle = stringResource(R.string.ai_privacy_note),
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
        Text(
            text = stringResource(R.string.maser_basis_title),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        ChipRow {
            listOf(
                MaserBasis.Net to R.string.maser_basis_net,
                MaserBasis.Gross to R.string.maser_basis_gross
            ).forEach { (option, labelRes) ->
                FilterChip(
                    selected = basis == option,
                    onClick = { onBasisChange(option) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
        Text(
            text = stringResource(R.string.maser_basis_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
