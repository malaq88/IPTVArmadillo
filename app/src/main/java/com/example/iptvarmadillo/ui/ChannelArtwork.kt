package com.example.iptvarmadillo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.iptvarmadillo.R
import com.example.iptvarmadillo.data.Channel

@Composable
fun ChannelArtwork(
    channel: Channel,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size / 4)
    val logo = channel.logoUrl
    if (logo.isNullOrBlank()) {
        LetterBadge(channel.name, size, shape, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(logo)
            .size(128, 128)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.channel_logo),
        modifier = modifier.size(size).clip(shape),
        contentScale = ContentScale.Fit,
        loading = { LetterBadge(channel.name, size, shape) },
        error = { LetterBadge(channel.name, size, shape) },
    )
}

@Composable
private fun LetterBadge(
    name: String,
    size: Dp,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().take(1).uppercase().ifBlank { "#" },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}
