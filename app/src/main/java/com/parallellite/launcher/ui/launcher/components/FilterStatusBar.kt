package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.parallellite.launcher.ui.launcher.HackFilter
import com.parallellite.launcher.ui.launcher.StatusMessage
import com.parallellite.launcher.ui.theme.BrandColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilterStatusBar(
    filter: HackFilter,
    status: StatusMessage,
    isLoading: Boolean,
    onFilterSelected: (HackFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HackFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterSelected(option) },
                    label = { Text(stringResource(option.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = BrandColors.Surface,
                        labelColor = BrandColors.TextSecondary,
                        selectedContainerColor = BrandColors.MarioGold,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.Black,
                    ),
                )
            }
        }

        StatusLine(status = status, isLoading = isLoading, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun StatusLine(status: StatusMessage, isLoading: Boolean, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        is StatusMessage.Error -> status.text to BrandColors.Error
        is StatusMessage.Success -> status.text to BrandColors.PlayGreen
        is StatusMessage.Info -> status.text to BrandColors.TextSecondary
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = BrandColors.MarioGold,
                strokeWidth = 2.dp,
            )
        }
        if (text.isNotEmpty()) {
            Text(text = text, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}
