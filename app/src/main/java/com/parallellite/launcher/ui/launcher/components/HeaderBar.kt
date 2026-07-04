package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parallellite.launcher.R
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun HeaderBar(
    isSignedIn: Boolean,
    baseRomPath: String,
    onLogout: () -> Unit,
    onPickBaseRom: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = BrandColors.MarioGold,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSecondary,
            )
        }

        BaseRomChip(baseRomPath = baseRomPath, onClick = onPickBaseRom)

        if (isSignedIn) {
            TextPill(text = stringResource(R.string.action_logout), onClick = onLogout)
        }

        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings), tint = BrandColors.TextPrimary)
        }
    }
}

@Composable
private fun BaseRomChip(baseRomPath: String, onClick: () -> Unit) {
    val missing = baseRomPath.isEmpty()
    val accent = if (missing) BrandColors.Error else BrandColors.PlayGreen
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BrandColors.Surface)
            .border(1.dp, if (missing) BrandColors.Error else BrandColors.Outline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .widthIn(max = 280.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(
            text = if (missing) stringResource(R.string.base_rom_missing)
            else stringResource(R.string.base_rom_ready),
            color = BrandColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = BrandColors.TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BrandColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
