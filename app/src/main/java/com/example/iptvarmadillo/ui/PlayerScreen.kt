package com.example.iptvarmadillo.ui

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.iptvarmadillo.R
import com.example.iptvarmadillo.data.Channel

@Composable
fun PlayerScreen(
    channelName: String,
    streamUrl: String,
    group: String,
    index: Int,
    count: Int,
    channels: List<Channel>,
    canPrevious: Boolean,
    canNext: Boolean,
    error: String?,
    errorDetail: String?,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTune: (Channel) -> Unit,
    onRetry: () -> Unit,
    player: () -> androidx.media3.exoplayer.ExoPlayer?,
    onHostStop: (Boolean) -> Unit,
    onHostStart: () -> Unit,
) {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    var guideOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = guideOpen) {
        guideOpen = false
    }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, activity.window.decorView)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onHostStop(activity?.isChangingConfigurations == true)
                Lifecycle.Event.ON_START -> onHostStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        controllerShowTimeoutMs = 3500
                        keepScreenOn = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player()
                    }
                },
                update = { playerView ->
                    playerView.keepScreenOn = true
                    val next = player()
                    if (playerView.player !== next) playerView.player = next
                },
                onRelease = { view -> view.player = null },
            )
            PlayerTopBar(
                channelName = channelName,
                group = group,
                index = index,
                count = count,
                guideOpen = guideOpen,
                canPrevious = canPrevious,
                canNext = canNext,
                onBack = onBack,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleGuide = { guideOpen = !guideOpen },
            )
            if (error != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    if (!errorDetail.isNullOrBlank()) {
                        Text(
                            errorDetail,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = guideOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            ChannelGuide(
                channels = channels,
                playingName = channelName,
                playingUrl = streamUrl,
                onClose = { guideOpen = false },
                onTune = onTune,
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    channelName: String,
    group: String,
    index: Int,
    count: Int,
    guideOpen: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleGuide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                ),
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = channelName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val position = if (count > 0) "${index + 1} / $count" else ""
                val subtitle = listOf(group, position).filter { it.isNotBlank() }.joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleGuide) {
                Icon(
                    Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(
                        if (guideOpen) R.string.hide_guide else R.string.show_guide,
                    ),
                    tint = if (guideOpen) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            IconButton(onClick = onPrevious, enabled = canPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_channel),
                    tint = if (canPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                )
            }
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.next_channel),
                    tint = if (canNext) Color.White else Color.White.copy(alpha = 0.35f),
                )
            }
        }
    }
}

@Composable
private fun ChannelGuide(
    channels: List<Channel>,
    playingName: String,
    playingUrl: String,
    onClose: () -> Unit,
    onTune: (Channel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = if (query.isBlank()) {
        channels
    } else {
        channels.filter { channel ->
            channel.name.contains(query, ignoreCase = true) ||
                channel.group.contains(query, ignoreCase = true)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val current = visible.indexOfFirst { it.streamUrl == playingUrl && it.name == playingName }
        if (current in 1 until visible.size) listState.scrollToItem(current)
    }

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(Color(0xF2111310))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.channel_guide),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.now_playing) + " · " + playingName,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.hide_guide),
                    tint = Color.White,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_channels)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_results),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = visible,
                    key = { itemIndex, channel -> "$itemIndex|${channel.name}|${channel.streamUrl.hashCode()}" },
                    contentType = { _, _ -> "guide" },
                ) { _, channel ->
                    val selected = channel.streamUrl == playingUrl && channel.name == playingName
                    GuideRow(channel = channel, selected = selected, onClick = { onTune(channel) })
                }
            }
        }
    }
}

@Composable
private fun GuideRow(channel: Channel, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelArtwork(channel = channel, size = 36.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = channel.name,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (selected) stringResource(R.string.now_playing) else channel.group,
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
