package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parallellite.launcher.R
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun SettingsDialog(
    patchedRomDir: String,
    liveTrackingEnabled: Boolean,
    onLiveTrackingChange: (Boolean) -> Unit,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canClose = patchedRomDir.isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (canClose) onDismiss() },
        containerColor = BrandColors.Surface,
        title = { Text(stringResource(R.string.settings_title), color = BrandColors.MarioGold, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_output_dir), color = BrandColors.TextPrimary)
                OutlinedTextField(
                    value = patchedRomDir,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_output_label), color = BrandColors.TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandColors.MarioGold,
                        unfocusedBorderColor = BrandColors.TextMuted,
                        focusedTextColor = BrandColors.TextPrimary,
                        unfocusedTextColor = BrandColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onPickFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.MarioGold),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_select_folder), color = Color.Black, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = BrandColors.Outline)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_live_tracking), color = BrandColors.TextPrimary)
                        Text(
                            stringResource(R.string.settings_live_tracking_desc),
                            color = BrandColors.TextMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = liveTrackingEnabled,
                        onCheckedChange = onLiveTrackingChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = BrandColors.MarioGold,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = canClose) {
                Text(stringResource(R.string.action_close), color = BrandColors.MarioGold)
            }
        },
    )
}
