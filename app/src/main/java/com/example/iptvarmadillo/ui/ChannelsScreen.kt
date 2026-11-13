package com.example.iptvarmadillo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.iptvarmadillo.R
import com.example.iptvarmadillo.data.Channel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    title: String,
    channels: List<Channel>,
    groups: List<String>,
    selectedGroup: String?,
    query: String,
    loading: Boolean,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onGroup: (String?) -> Unit,
    onPlay: (Int) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_channels)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.channel_count, channels.size, channels.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            if (groups.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = selectedGroup == null,
                            onClick = { onGroup(null) },
                            label = { Text(stringResource(R.string.all_groups)) },
                        )
                    }
                    items(groups, key = { it }) { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { onGroup(group) },
                            label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = channels,
                        key = { index, _ -> index },
                        contentType = { _, _ -> "channel" },
                    ) { index, channel ->
                        ChannelRow(channel = channel, onClick = { onPlay(index) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        headlineContent = {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                channel.group,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { ChannelLogo(channel) },
        trailingContent = {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun ChannelLogo(channel: Channel) {
    val shape = RoundedCornerShape(10.dp)
    val logo = channel.logoUrl
    if (logo.isNullOrBlank()) {
        LetterBadge(channel.name, shape)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(logo)
            .size(96, 96)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.channel_logo),
        modifier = Modifier.size(44.dp).clip(shape),
        contentScale = ContentScale.Fit,
        loading = { LetterBadge(channel.name, shape) },
        error = { LetterBadge(channel.name, shape) },
    )
}

@Composable
private fun LetterBadge(name: String, shape: RoundedCornerShape) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().take(1).uppercase().ifBlank { "#" },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
