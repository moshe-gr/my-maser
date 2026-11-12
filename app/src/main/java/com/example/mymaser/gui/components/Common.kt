package com.example.mymaser.gui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mymaser.R
import com.example.mymaser.gui.theme.MaserMotion
import com.example.mymaser.gui.theme.maserColors
import java.text.DateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val ContentMaxWidth = 600.dp

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * Shows [content] while [value] is non-null, expanding in and collapsing out. The last value is
 * kept so the content can still draw while it animates away.
 */
@Composable
fun <T : Any> AnimatedPresence(value: T?, content: @Composable (T) -> Unit) {
    var held by remember { mutableStateOf(value) }
    if (value != null) held = value
    AnimatedVisibility(visible = value != null, enter = MaserMotion.expand, exit = MaserMotion.collapse) {
        held?.let { content(it) }
    }
}

@Composable
fun surfaceCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.invoke()
    }
}

/** The round income or donation icon that leads entry rows and cards. */
@Composable
fun EntryTypeBadge(isDonation: Boolean, size: Dp = 40.dp, iconSize: Dp = 20.dp) {
    val colors = MaterialTheme.maserColors
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = if (isDonation) colors.donationContainer else colors.incomeContainer,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDonation) Icons.Filled.VolunteerActivism else Icons.Filled.AccountBalanceWallet,
            contentDescription = stringResource(if (isDonation) R.string.donation else R.string.income),
            tint = if (isDonation) colors.donation else colors.income,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Chooses whether a donation comes out of maser or is kept only for tax receipts. */
@Composable
fun DonationTypeSelector(
    isTaxOnly: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(false to R.string.donation_type_maser, true to R.string.donation_type_tax_only)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (taxOnly, label) ->
            SegmentedButton(
                selected = isTaxOnly == taxOnly,
                onClick = { onChange(taxOnly) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            ) {
                Text(stringResource(label))
            }
        }
    }
}

/** Marks a donation that doesn't reduce the maser balance. */
@Composable
fun TaxOnlyTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = stringResource(R.string.tax_only_tag),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** A horizontally scrolling row of chips; [contentPadding] scrolls with the chips. */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun LabelValueRow(
    label: String,
    value: String,
    valueStyle: TextStyle,
    valueColor: Color = Color.Unspecified
) {
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
        Text(text = value, style = valueStyle, color = valueColor)
    }
}

@Composable
fun rememberDateTimeFormat(): DateFormat =
    remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

@Composable
fun rememberDateFormat(): DateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

/** "November 2026", in the stand-alone month form some languages need. */
fun YearMonth.monthYearLabel(): String =
    format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
