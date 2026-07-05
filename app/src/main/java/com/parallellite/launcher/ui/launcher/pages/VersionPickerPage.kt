package com.parallellite.launcher.ui.launcher.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parallellite.launcher.R
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.data.model.HackVersion
import com.parallellite.launcher.ui.launcher.components.PageTopBar
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun VersionPickerPage(
    hack: Hack,
    onBack: () -> Unit,
    onPick: (HackVersion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageTopBar(title = stringResource(R.string.dialog_version_title), onBack = onBack, modifier = Modifier.padding(bottom = 6.dp))
        Text(
            text = hack.title,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
        )

        if (hack.variants.isEmpty()) {
            Text(stringResource(R.string.dialog_no_variants), color = BrandColors.TextSecondary, modifier = Modifier.padding(4.dp))
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(hack.variants) { variant ->
                VersionCard(variant = variant, onClick = { onPick(variant) })
            }
        }
    }
}

@Composable
private fun VersionCard(variant: HackVersion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = variant.name,
            style = MaterialTheme.typography.titleMedium,
            color = BrandColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        variant.plugin?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MarioGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrandColors.SurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
