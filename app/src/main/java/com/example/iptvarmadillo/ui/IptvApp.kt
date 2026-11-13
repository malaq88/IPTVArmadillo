package com.example.iptvarmadillo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.iptvarmadillo.R

@Composable
fun IptvApp(viewModel: IptvViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    BackHandler(enabled = state.destination != Destination.Library) {
        viewModel.back()
    }

    LaunchedEffect(state.message?.id) {
        val text = state.message?.text ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    val pendingDelete = state.playlists.find { it.id == state.pendingDeleteId }

    Box(Modifier.fillMaxSize()) {
        when (state.destination) {
            Destination.Library -> LibraryScreen(
                playlists = state.playlists,
                loading = state.loading,
                onAdd = viewModel::openDraft,
                onOpen = viewModel::openPlaylist,
                onRefresh = viewModel::refresh,
                onDelete = viewModel::requestDelete,
            )
            Destination.Channels -> ChannelsScreen(
                title = state.playlistTitle,
                channels = state.visibleChannels,
                groups = state.groups,
                selectedGroup = state.selectedGroup,
                query = state.query,
                loading = state.loading,
                onBack = viewModel::back,
                onQuery = viewModel::onQuery,
                onGroup = viewModel::onGroup,
                onPlay = viewModel::play,
            )
            Destination.Player -> PlayerScreen(
                channelName = state.playerChannelName,
                group = state.playerGroup,
                index = state.playerIndex,
                count = state.playerCount,
                canPrevious = state.canPrevious,
                canNext = state.canNext,
                error = state.playerError,
                errorDetail = state.playerErrorDetail,
                onBack = viewModel::back,
                onPrevious = viewModel::previousChannel,
                onNext = viewModel::nextChannel,
                onRetry = viewModel::retryPlayback,
                player = viewModel::playerOrNull,
                onHostStop = viewModel::onHostStop,
                onHostStart = viewModel::onHostStart,
            )
        }

        if (state.destination != Destination.Player) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = if (state.destination == Destination.Library) 88.dp else 16.dp),
            )
        }

        if (state.draftOpen) {
            AddPlaylistSheet(
                draft = state.draft,
                loading = state.loading,
                error = state.draftError,
                onName = viewModel::onDraftName,
                onUrl = viewModel::onDraftUrl,
                onUserAgent = viewModel::onDraftUserAgent,
                onReferer = viewModel::onDraftReferer,
                onChooseFile = { openDocument.launch(arrayOf("*/*")) },
                onClearFile = viewModel::clearDraftFile,
                onSave = viewModel::submitDraft,
                onClose = viewModel::closeDraft,
            )
        }

        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDelete,
                title = { Text(stringResource(R.string.remove_title)) },
                text = { Text(stringResource(R.string.remove_body, pendingDelete.name)) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) {
                        Text(stringResource(R.string.remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDelete) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}
