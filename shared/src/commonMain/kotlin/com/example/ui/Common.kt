package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stats.BattingTotals
import com.example.stats.formatAverage

val STAT_COLUMNS = listOf(
    "GP", "AB", "R", "H", "2B", "3B", "HR", "RBI", "BB", "SO", "SB",
    "AVG", "OBP", "SLG", "OPS"
)

fun statValues(t: BattingTotals): List<String> = listOf(
    t.games.toString(), t.atBats.toString(), t.runs.toString(), t.hits.toString(),
    t.doubles.toString(), t.triples.toString(), t.homeRuns.toString(), t.rbis.toString(),
    t.walks.toString(), t.strikeouts.toString(), t.stolenBases.toString(),
    formatAverage(t.battingAverage), formatAverage(t.onBasePercentage),
    formatAverage(t.sluggingPercentage), formatAverage(t.ops)
)

/**
 * A horizontally scrollable stats table. Each row is a label (e.g. season name)
 * plus one [BattingTotals]. The label column stays compact; stat cells are fixed width.
 */
@Composable
fun StatsTable(
    rows: List<Pair<String, BattingTotals>>,
    modifier: Modifier = Modifier,
    labelWidth: Int = 84
) {
    val scrollState = rememberScrollState()
    val cellWidth = 48.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TableCell("", width = labelWidth.dp, header = true)
            STAT_COLUMNS.forEach { TableCell(it, width = cellWidth, header = true) }
        }
        HorizontalDivider()
        rows.forEach { (label, totals) ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(label, width = labelWidth.dp, header = true, align = TextAlign.Start)
                statValues(totals).forEach { TableCell(it, width = cellWidth) }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    header: Boolean = false,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        color = if (header) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Compact numeric entry field used in the stat line editor. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(3)) },
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

val ListContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)

// Material 3 1.4, which Compose Multiplatform 1.12 brings, recoloured a few
// controls in quieter greys. The app keeps the look it was designed with, so
// an Android phone sees no change: these stand in for the Material widgets
// wherever the app used one whose colours moved.

/** A FilterChip whose unselected border is the mid-grey outline it has always had. */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * A tab's name, kept on one line. Four tabs share the bar, and where it is
 * narrow - the iPhone Duo's front screen, a small phone - "Standings" broke
 * mid-word onto two lines. Now the name shrinks a little instead, down to 9sp,
 * and stays whole.
 */
@Composable
fun TabLabel(text: String) {
    // The bar's own label size (labelMedium, 12sp) is the most it grows to.
    val full = LocalTextStyle.current.fontSize.takeIf { it.isSp && it.value > 9f } ?: 12.sp
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = full)
    )
}
