package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.parallellite.launcher.data.model.Hack
import com.parallellite.launcher.ui.theme.BrandColors

private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun HackGrid(
    hacks: List<Hack>,
    selectedHackId: String?,
    enabled: Boolean,
    thumbnailProvider: suspend (String) -> String?,
    onHackFocused: (Hack) -> Unit,
    onHackClicked: (Hack) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(hacks, key = { it.id }) { hack ->
            HackCard(
                hack = hack,
                selected = hack.id == selectedHackId,
                enabled = enabled,
                thumbnailProvider = thumbnailProvider,
                onFocused = { onHackFocused(hack) },
                onClicked = { onHackClicked(hack) },
            )
        }
    }
}

@Composable
private fun HackCard(
    hack: Hack,
    selected: Boolean,
    enabled: Boolean,
    thumbnailProvider: suspend (String) -> String?,
    onFocused: () -> Unit,
    onClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) BrandColors.SurfaceVariant else BrandColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) BrandColors.MarioGold else BrandColors.Outline,
                shape = CardShape,
            )
            .clickable(enabled = enabled) {
                onFocused()
                onClicked()
            }
            .padding(8.dp),
    ) {
        // Cover art with a star badge overlay.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            HackArtwork(hack, thumbnailProvider)
            StarBadge(
                count = hack.starCount,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }
        Text(
            text = hack.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) BrandColors.MarioGold else BrandColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun StarBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "★ $count",
        style = MaterialTheme.typography.bodySmall,
        color = BrandColors.MarioGold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun HackArtwork(hack: Hack, thumbnailProvider: suspend (String) -> String?) {
    // Resolve the thumbnail lazily (one detail request per hack, cached upstream).
    val url by produceState(initialValue = hack.thumbnailUrl, hack.id) {
        if (value == null) value = thumbnailProvider(hack.id)
    }

    val resolved = url
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandColors.MarioGold, BrandColors.MarioOrange))),
        contentAlignment = Alignment.Center,
    ) {
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
