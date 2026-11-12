package com.example.mymaser.settings.gui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.mymaser.R
import com.example.mymaser.settings.MaserBasis

@Composable
fun AiSetting(
    enabled: Boolean,
    basis: MaserBasis,
    onEnabledChange: (Boolean) -> Unit,
    onBasisChange: (MaserBasis) -> Unit
) {
    SwitchRow(
        title = stringResource(R.string.ai_enabled),
        subtitle = stringResource(R.string.ai_privacy_note),
        checked = enabled,
        onCheckedChange = onEnabledChange
    )
    SettingsDivider()
    Text(
        text = stringResource(R.string.maser_basis_title),
        style = MaterialTheme.typography.titleSmall
    )
    SegmentedChoice(
        options = listOf(
            MaserBasis.Net to stringResource(R.string.maser_basis_net),
            MaserBasis.Gross to stringResource(R.string.maser_basis_gross)
        ),
        selected = basis,
        onSelect = onBasisChange
    )
    SettingHint(stringResource(R.string.maser_basis_hint))
}
