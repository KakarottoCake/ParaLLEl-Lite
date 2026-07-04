package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parallellite.launcher.R
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.data.model.HackVersion
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun VersionDialog(
    hack: Hack,
    onDismiss: () -> Unit,
    onConfirm: (HackVersion) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val variants = hack.variants

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BrandColors.Surface,
        title = { Text(stringResource(R.string.dialog_version_title), color = BrandColors.MarioGold, fontWeight = FontWeight.Bold) },
        text = {
            if (variants.isEmpty()) {
                Text(stringResource(R.string.dialog_no_variants), color = BrandColors.TextSecondary)
            } else {
                Column {
                    Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        variants.forEachIndexed { index, variant ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = index }
                                    .padding(vertical = 4.dp),
                            ) {
                                RadioButton(selected = selected == index, onClick = { selected = index })
                                Spacer(Modifier.width(8.dp))
                                Text(variant.name, color = BrandColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onConfirm(variants[selected]) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColors.MarioGold),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dialog_download_patch), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = BrandColors.MarioGold)
            }
        },
    )
}
