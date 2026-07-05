package com.parallellite.launcher.ui.launcher.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parallellite.launcher.ui.theme.BrandColors

/** A back button + title row used at the top of sub-pages. */
@Composable
fun PageTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "←",
            fontSize = 24.sp,
            color = BrandColors.TextPrimary,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BrandColors.SurfaceVariant)
                .clickable(onClick = onBack)
                .padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = BrandColors.MarioGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
