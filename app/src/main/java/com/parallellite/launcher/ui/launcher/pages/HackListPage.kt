package com.parallellite.launcher.ui.launcher.pages

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
import com.parallellite.launcher.ui.launcher.HackFilter
import com.parallellite.launcher.ui.launcher.StatusMessage
import com.parallellite.launcher.ui.launcher.components.FilterStatusBar
import com.parallellite.launcher.ui.launcher.components.HeaderBar
import com.parallellite.launcher.ui.theme.BrandColors

@Composable
fun HackListPage(
    hacks: List<Hack>,
    isSignedIn: Boolean,
    baseRomPath: String,
    filter: HackFilter,
    status: StatusMessage,
    isLoading: Boolean,
    thumbnailProvider: suspend (String) -> String?,
    onFilterSelected: (HackFilter) -> Unit,
    onLogout: () -> Unit,
    onPickBaseRom: () -> Unit,
    onOpenSettings: () -> Unit,
    onHackClick: (Hack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeaderBar(
            isSignedIn = isSignedIn,
            baseRomPath = baseRomPath,
            onLogout = onLogout,
            onPickBaseRom = onPickBaseRom,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FilterStatusBar(
            filter = filter,
            status = status,
            isLoading = isLoading,
            onFilterSelected = onFilterSelected,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(hacks, key = { it.id }) { hack ->
                HackCard(hack = hack, thumbnailProvider = thumbnailProvider, onClick = { onHackClick(hack) })
            }
        }
    }
}

@Composable
private fun HackCard(hack: Hack, thumbnailProvider: suspend (String) -> String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.Outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            Artwork(hack, thumbnailProvider)
            Text(
                text = "★ ${hack.starCount}",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.MarioGold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Text(
            text = hack.title,
            style = MaterialTheme.typography.titleMedium,
            color = BrandColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun Artwork(hack: Hack, thumbnailProvider: suspend (String) -> String?) {
    val url by produceState(initialValue = hack.thumbnailUrl, hack.id) {
        if (value == null) value = thumbnailProvider(hack.id)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandColors.MarioGold, BrandColors.MarioOrange))),
        contentAlignment = Alignment.Center,
    ) {
        val resolved = url
        if (resolved.isNullOrEmpty()) {
            Text(hack.title.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineMedium, color = Color.Black)
        } else {
            AsyncImage(model = resolved, contentDescription = hack.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
