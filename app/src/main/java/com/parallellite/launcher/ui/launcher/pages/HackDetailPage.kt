package com.parallellite.launcher.ui.launcher.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parallellite.launcher.R
import com.parallellite.launcher.data.CoreChoice
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.ui.launcher.components.PageTopBar
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun HackDetailPage(
    hack: Hack,
    isPatched: Boolean,
    canPatch: Boolean,
    coreChoice: CoreChoice,
    onCoreChange: (CoreChoice) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPatch: () -> Unit,
    onUnpatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageTopBar(title = hack.title, onBack = onBack, modifier = Modifier.padding(bottom = 14.dp))

        if (hack.authors.isNotEmpty()) {
            Text(
                text = stringResource(R.string.detail_by, hack.authors.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextMuted,
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(stringResource(R.string.stars_count, hack.starCount.toString()))
            MetaChip(stringResource(R.string.version_label, hack.version))
        }

        if (hack.recommendedSettings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.detail_recommended_settings), style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                hack.recommendedSettings.forEach { SettingChip(it) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = hack.description.ifBlank { stringResource(R.string.detail_no_description) },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = BrandColors.TextSecondary,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )

        Spacer(Modifier.height(16.dp))
        CoreSelector(coreChoice = coreChoice, recommended = hack.recommendedPlugin, onCoreChange = onCoreChange)
        Spacer(Modifier.height(12.dp))

        if (isPatched) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.PlayGreen),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.action_play), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                OutlinedButton(
                    onClick = onUnpatch,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, BrandColors.Outline),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(stringResource(R.string.action_unpatch), color = BrandColors.TextSecondary)
                }
            }
        } else {
            Button(
                onClick = onPatch,
                enabled = canPatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.MarioGold,
                    disabledContainerColor = BrandColors.SurfaceVariant,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.action_patch), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun CoreSelector(coreChoice: CoreChoice, recommended: String?, onCoreChange: (CoreChoice) -> Unit) {
    Column {
        val autoLabel = if (recommended != null) stringResource(R.string.core_auto_with, recommended) else stringResource(R.string.core_auto)
        Text(stringResource(R.string.core_label), style = MaterialTheme.typography.bodySmall, color = BrandColors.TextMuted)
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CorePill(autoLabel, coreChoice == CoreChoice.AUTO) { onCoreChange(CoreChoice.AUTO) }
            CorePill(stringResource(R.string.core_parallel), coreChoice == CoreChoice.PARALLEL) { onCoreChange(CoreChoice.PARALLEL) }
            CorePill(stringResource(R.string.core_mupen), coreChoice == CoreChoice.MUPEN) { onCoreChange(CoreChoice.MUPEN) }
            CorePill(stringResource(R.string.core_m64plusfz), coreChoice == CoreChoice.M64PLUS_FZ) { onCoreChange(CoreChoice.M64PLUS_FZ) }
        }
    }
}

@Composable
private fun CorePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) Color.Black else BrandColors.TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BrandColors.MarioGold else BrandColors.SurfaceVariant)
            .border(1.dp, if (selected) BrandColors.MarioGold else BrandColors.Outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SettingChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BrandColors.PlayGreen,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.PlayGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = BrandColors.MarioGold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BrandColors.SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
