package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.parallellite.launcher.R
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun HackDetailPanel(
    hack: Hack?,
    isPatched: Boolean,
    canPatch: Boolean,
    thumbnailProvider: suspend (String) -> String?,
    onPlay: () -> Unit,
    onPatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BrandColors.BackgroundTop),
        shape = MaterialTheme.shapes.large,
    ) {
        if (hack == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.detail_empty), color = BrandColors.TextMuted)
            }
            return@Card
        }

        Column(modifier = Modifier.fillMaxHeight().padding(16.dp)) {
            HeroImage(hack, thumbnailProvider)
            Spacer(Modifier.height(14.dp))
            Text(hack.title, style = MaterialTheme.typography.titleLarge, color = BrandColors.MarioGold)
            if (hack.authors.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.detail_by, hack.authors.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextMuted,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip(stringResource(R.string.stars_count, hack.starCount.toString()))
                MetaChip(stringResource(R.string.version_label, hack.version))
            }
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            ) {
                Text(hack.description, style = MaterialTheme.typography.bodyMedium, color = BrandColors.TextMuted)
            }

            if (isPatched) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColors.PlayGreen),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.action_play), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.action_patch), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
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

@Composable
private fun HeroImage(hack: Hack, thumbnailProvider: suspend (String) -> String?) {
    val url by produceState(initialValue = hack.thumbnailUrl, hack.id) {
        if (value == null) value = thumbnailProvider(hack.id)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(BrandColors.MarioGold, BrandColors.MarioOrange))),
        contentAlignment = Alignment.Center,
    ) {
        val resolved = url
        if (resolved.isNullOrEmpty()) {
            Text(
                text = hack.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
            )
        } else {
            AsyncImage(
                model = resolved,
                contentDescription = hack.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
