package com.example.iptvarmadillo.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.example.iptvarmadillo.R
import com.example.iptvarmadillo.data.Channel
import com.example.iptvarmadillo.data.EmptyPlaylistException
import com.example.iptvarmadillo.data.HttpStatusException
import com.example.iptvarmadillo.data.M3uParser
import com.example.iptvarmadillo.data.PlaylistFetcher
import com.example.iptvarmadillo.data.PlaylistStore
import com.example.iptvarmadillo.data.PlaylistTooLargeException
import com.example.iptvarmadillo.data.SourceType
import com.example.iptvarmadillo.data.StoredPlaylist
import com.example.iptvarmadillo.data.effectiveUserAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID

enum class Destination {
    Library,
    Channels,
    Player,
}

data class LibraryItem(
    val id: String,
    val name: String,
    val detail: String,
    val fromUrl: Boolean,
)

data class PlaylistDraft(
    val name: String = "",
    val url: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val fileLabel: String? = null,
    val fileUri: String? = null,
)

data class UserMessage(
    val id: Long,
    val text: String,
)

data class IptvUiState(
    val destination: Destination = Destination.Library,
    val playlists: List<LibraryItem> = emptyList(),
    val openPlaylistId: String? = null,
    val playlistTitle: String = "",
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val query: String = "",
    val loading: Boolean = false,
    val message: UserMessage? = null,
    val draftOpen: Boolean = false,
    val draft: PlaylistDraft = PlaylistDraft(),
    val draftError: String? = null,
    val pendingDeleteId: String? = null,
    val playerChannelName: String = "",
    val playerGroup: String = "",
    val playerIndex: Int = 0,
    val playerCount: Int = 0,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val playerBusy: Boolean = false,
    val playerError: String? = null,
    val playerErrorDetail: String? = null,
)

class IptvViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PlaylistStore(app)
    private val fetcher = PlaylistFetcher()
    private val _state = MutableStateFlow(IptvUiState())
    val state: StateFlow<IptvUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var boundHeaders: String? = null
    private var zapList: List<Channel> = emptyList()
    private var zapIndex: Int = 0
    private var activeUserAgent: String = ""
    private var activeReferer: String = ""
    private var filterJob: Job? = null
    private var resumeOnReturn = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) publishTransport(clearError = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(playerBusy = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val rejected = (error.cause as? HttpDataSource.InvalidResponseCodeException)
                ?.responseCode
                ?.let { it == 401 || it == 403 } == true
            _state.update {
                it.copy(
                    playerBusy = false,
                    playerError = getString(
                        if (rejected) R.string.playback_rejected else R.string.playback_error,
                    ),
                    playerErrorDetail = error.errorCodeName,
                )
            }
        }
    }

    init {
        reloadLibrary()
    }

    fun openDraft() {
        if (_state.value.loading) return
        _state.update { it.copy(draftOpen = true, draft = PlaylistDraft(), draftError = null) }
    }

    fun closeDraft() {
        if (_state.value.loading) {
            _state.update { it.copy(draftOpen = false) }
        } else {
            _state.update { it.copy(draftOpen = false, draft = PlaylistDraft(), draftError = null) }
        }
    }

    fun onDraftName(value: String) = updateDraft { it.copy(name = value) }

    fun onDraftUrl(value: String) = updateDraft {
        it.copy(
            url = value,
            fileUri = if (value.isNotBlank()) null else it.fileUri,
            fileLabel = if (value.isNotBlank()) null else it.fileLabel,
        )
    }

    fun onDraftUserAgent(value: String) = updateDraft { it.copy(userAgent = value) }

    fun onDraftReferer(value: String) = updateDraft { it.copy(referer = value) }

    fun clearDraftFile() = updateDraft { it.copy(fileUri = null, fileLabel = null) }

    fun onFilePicked(uri: Uri) {
        val context = getApplication<Application>()
        fetcher.persistReadPermission(context, uri)
        val label = fetcher.displayName(context, uri)
        _state.update { current ->
            current.copy(
                draft = current.draft.copy(
                    fileUri = uri.toString(),
                    fileLabel = label,
                    url = "",
                    name = current.draft.name.ifBlank { label.substringBeforeLast('.').ifBlank { label } },
                ),
                draftError = null,
            )
        }
    }

    fun submitDraft() {
        val draft = _state.value.draft
        if (_state.value.loading) return
        val userAgent = draft.userAgent.trim().take(512)
        val referer = draft.referer.trim().take(1024)
        val fileUri = draft.fileUri
        val url = draft.url.trim()
        if (fileUri.isNullOrBlank() && url.isBlank()) {
            _state.update { it.copy(draftError = getString(R.string.need_source)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, draftError = null) }
            try {
                val outcome = if (!fileUri.isNullOrBlank()) {
                    importFile(draft.name, Uri.parse(fileUri), userAgent, referer)
                } else {
                    val normalized = normalizeUrl(url)
                        ?: throw IllegalArgumentException(getString(R.string.bad_url))
                    importUrl(draft.name, normalized, userAgent, referer)
                }
                val notice = if (outcome.truncated) {
                    getString(R.string.truncated)
                } else {
                    getString(R.string.added)
                }
                _state.update {
                    it.copy(
                        loading = false,
                        draftOpen = false,
                        draft = PlaylistDraft(),
                        draftError = null,
                        playlists = readLibrary(),
                        message = UserMessage(System.nanoTime(), notice),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        draftOpen = true,
                        draftError = errorText(error),
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun openPlaylist(id: String) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val stored = withContext(Dispatchers.IO) { store.get(id) }
                if (stored == null) {
                    _state.update { it.copy(loading = false) }
                    return@launch
                }
                val text = withContext(Dispatchers.IO) { store.content(id) }
                val parsed = withContext(Dispatchers.Default) {
                    M3uParser.parse(
                        content = text,
                        fallbackName = stored.name,
                        baseUrl = stored.baseUrl.ifBlank { null },
                        hlsPlaybackUrl = playbackUrl(stored),
                    )
                }
                if (parsed.channels.isEmpty()) throw EmptyPlaylistException()
                activeUserAgent = stored.userAgent
                activeReferer = stored.referer
                _state.update {
                    it.copy(
                        loading = false,
                        destination = Destination.Channels,
                        openPlaylistId = id,
                        playlistTitle = stored.name,
                        channels = parsed.channels,
                        visibleChannels = parsed.channels,
                        groups = parsed.channels.map { channel -> channel.group }.distinct(),
                        selectedGroup = null,
                        query = "",
                        message = if (parsed.truncated) {
                            UserMessage(System.nanoTime(), getString(R.string.truncated))
                        } else {
                            it.message
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = UserMessage(System.nanoTime(), errorText(error)),
                    )
                }
            }
        }
    }

    fun refresh(id: String) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val stored = withContext(Dispatchers.IO) { store.get(id) }
                    ?: error("Lista não encontrada")
                val imported = withContext(Dispatchers.IO) { reload(stored) }
                if (imported.channels.isEmpty()) throw EmptyPlaylistException()
                val updated = stored.copy(
                    baseUrl = imported.baseUrl,
                    channelCount = imported.channels.size,
                    updatedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) { store.put(updated, imported.text) }
                val notice = if (imported.truncated) {
                    getString(R.string.truncated)
                } else {
                    getString(R.string.updated)
                }
                _state.update { current ->
                    val sameList = current.openPlaylistId == id && current.destination != Destination.Library
                    val channels = if (sameList) imported.channels else current.channels
                    val group = current.selectedGroup?.takeIf { selected ->
                        channels.any { it.group == selected }
                    }
                    current.copy(
                        loading = false,
                        playlists = readLibrary(),
                        channels = channels,
                        groups = if (sameList) channels.map { it.group }.distinct() else current.groups,
                        selectedGroup = if (sameList) group else current.selectedGroup,
                        visibleChannels = if (sameList) {
                            filterChannels(channels, group, current.query)
                        } else {
                            current.visibleChannels
                        },
                        message = UserMessage(System.nanoTime(), notice),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        message = UserMessage(System.nanoTime(), errorText(error)),
                    )
                }
            }
        }
    }

    fun requestDelete(id: String) {
        _state.update { it.copy(pendingDeleteId = id) }
    }

    fun dismissDelete() {
        _state.update { it.copy(pendingDeleteId = null) }
    }

    fun confirmDelete() {
        val id = _state.value.pendingDeleteId ?: return
        store.delete(id)
        val closing = _state.value.openPlaylistId == id
        if (closing) releasePlayer()
        _state.update {
            it.copy(
                pendingDeleteId = null,
                playlists = readLibrary(),
                destination = if (closing) Destination.Library else it.destination,
                openPlaylistId = if (closing) null else it.openPlaylistId,
                channels = if (closing) emptyList() else it.channels,
                visibleChannels = if (closing) emptyList() else it.visibleChannels,
            )
        }
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        refilter()
    }

    fun onGroup(group: String?) {
        _state.update { it.copy(selectedGroup = group) }
        refilter()
    }

    fun play(index: Int) {
        val list = _state.value.visibleChannels
        if (index !in list.indices) return
        val channelAgent = list.mapNotNull { it.userAgent?.trim()?.takeIf(String::isNotEmpty) }.toSet()
        val userAgent = effectiveUserAgent(activeUserAgent.ifBlank { channelAgent.singleOrNull() })
        val exoPlayer = ensurePlayer(userAgent, activeReferer)
        zapList = list
        zapIndex = index
        _state.update {
            it.copy(
                destination = Destination.Player,
                playerError = null,
                playerErrorDetail = null,
                playerBusy = true,
                playerCount = list.size,
            )
        }
        startCurrentChannel(exoPlayer)
    }

    fun previousChannel() {
        if (zapIndex <= 0) return
        val exoPlayer = player ?: return
        zapIndex--
        startCurrentChannel(exoPlayer)
    }

    fun nextChannel() {
        if (zapIndex >= zapList.lastIndex) return
        val exoPlayer = player ?: return
        zapIndex++
        startCurrentChannel(exoPlayer)
    }

    fun retryPlayback() {
        val exoPlayer = player ?: return
        startCurrentChannel(exoPlayer)
    }

    fun onHostStop(changingConfigurations: Boolean) {
        if (changingConfigurations || _state.value.destination != Destination.Player) return
        resumeOnReturn = player?.isPlaying == true
        player?.pause()
    }

    fun onHostStart() {
        if (resumeOnReturn && _state.value.destination == Destination.Player) {
            player?.play()
        }
        resumeOnReturn = false
    }

    fun playerOrNull(): ExoPlayer? = player

    fun back() {
        when (_state.value.destination) {
            Destination.Player -> {
                player?.stop()
                _state.update {
                    it.copy(
                        destination = Destination.Channels,
                        playerBusy = false,
                        playerError = null,
                        playerErrorDetail = null,
                    )
                }
            }
            Destination.Channels -> {
                releasePlayer()
                filterJob?.cancel()
                _state.update {
                    it.copy(
                        destination = Destination.Library,
                        openPlaylistId = null,
                        channels = emptyList(),
                        visibleChannels = emptyList(),
                        groups = emptyList(),
                        selectedGroup = null,
                        query = "",
                    )
                }
            }
            Destination.Library -> Unit
        }
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }

    private fun updateDraft(transform: (PlaylistDraft) -> PlaylistDraft) {
        _state.update { it.copy(draft = transform(it.draft), draftError = null) }
    }

    private suspend fun importUrl(
        name: String,
        url: String,
        userAgent: String,
        referer: String,
    ): ImportOutcome {
        val fetched = withContext(Dispatchers.IO) { fetcher.download(url, userAgent, referer) }
        val fallback = name.trim().ifBlank { hostOr(fetched.finalUrl) }
        val imported = withContext(Dispatchers.Default) {
            materialize(fetched.bytes, fallback, fetched.finalUrl, fetched.finalUrl)
        }
        if (imported.channels.isEmpty()) throw EmptyPlaylistException()
        withContext(Dispatchers.IO) {
            save(fallback, SourceType.Url, url, fetched.finalUrl, userAgent, referer, imported)
        }
        return ImportOutcome(imported.truncated)
    }

    private suspend fun importFile(
        name: String,
        uri: Uri,
        userAgent: String,
        referer: String,
    ): ImportOutcome {
        val context = getApplication<Application>()
        val bytes = withContext(Dispatchers.IO) { fetcher.read(context, uri) }
        val label = fetcher.displayName(context, uri)
        val fallback = name.trim().ifBlank { label.substringBeforeLast('.').ifBlank { label } }
        val playback = uri.toString()
        val imported = withContext(Dispatchers.Default) {
            materialize(bytes, fallback, baseUrl = null, hlsPlaybackUrl = playback)
        }
        if (imported.channels.isEmpty()) throw EmptyPlaylistException()
        withContext(Dispatchers.IO) {
            save(fallback, SourceType.File, playback, baseUrl = "", userAgent, referer, imported)
        }
        return ImportOutcome(imported.truncated)
    }

    private fun save(
        name: String,
        sourceType: SourceType,
        source: String,
        baseUrl: String,
        userAgent: String,
        referer: String,
        imported: Imported,
    ) {
        val stored = StoredPlaylist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Lista" },
            sourceType = sourceType,
            source = source,
            baseUrl = baseUrl,
            userAgent = userAgent,
            referer = referer,
            channelCount = imported.channels.size,
            updatedAt = System.currentTimeMillis(),
        )
        store.put(stored, imported.text)
    }

    private fun reload(stored: StoredPlaylist): Imported {
        val userAgent = stored.userAgent
        val referer = stored.referer
        return when (stored.sourceType) {
            SourceType.Url -> {
                val fetched = fetcher.download(stored.source, userAgent, referer)
                materialize(fetched.bytes, stored.name, fetched.finalUrl, fetched.finalUrl)
                    .copy(baseUrl = fetched.finalUrl)
            }
            SourceType.File -> {
                val bytes = fetcher.read(getApplication(), Uri.parse(stored.source))
                materialize(bytes, stored.name, baseUrl = null, hlsPlaybackUrl = stored.source)
                    .copy(baseUrl = "")
            }
        }
    }

    private fun materialize(
        bytes: ByteArray,
        fallbackName: String,
        baseUrl: String?,
        hlsPlaybackUrl: String?,
    ): Imported {
        val text = M3uParser.decode(bytes)
        val parsed = M3uParser.parse(text, fallbackName, baseUrl, hlsPlaybackUrl)
        return Imported(
            text = text,
            channels = parsed.channels,
            truncated = parsed.truncated,
            baseUrl = baseUrl.orEmpty(),
        )
    }

    private fun refilter() {
        filterJob?.cancel()
        val query = _state.value.query
        val group = _state.value.selectedGroup
        filterJob = viewModelScope.launch {
            val channels = _state.value.channels
            val visible = withContext(Dispatchers.Default) {
                filterChannels(channels, group, query)
            }
            _state.update { current ->
                if (current.query != query || current.selectedGroup != group) {
                    current
                } else {
                    current.copy(visibleChannels = visible)
                }
            }
        }
    }

    private fun ensurePlayer(userAgent: String, referer: String): ExoPlayer {
        val headers = "$userAgent\n$referer"
        val existing = player
        if (existing != null && boundHeaders == headers) return existing
        existing?.release()
        val requestHeaders = buildMap {
            put("Accept", "*/*")
            if (referer.isNotBlank()) put("Referer", referer)
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setDefaultRequestProperties(requestHeaders)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            )
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 30_000, 800, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(getApplication())
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .build()
            .also { created ->
                created.addListener(playerListener)
                player = created
                boundHeaders = headers
            }
    }

    private fun startCurrentChannel(exoPlayer: ExoPlayer) {
        val channel = zapList.getOrNull(zapIndex) ?: return
        exoPlayer.stop()
        exoPlayer.setMediaItem(channel.toMediaItem(zapIndex), /* resetPosition = */ true)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        publishTransport(clearError = true)
        _state.update { it.copy(playerBusy = true) }
    }

    private fun publishTransport(clearError: Boolean) {
        val channel = zapList.getOrNull(zapIndex)
        _state.update {
            it.copy(
                playerChannelName = channel?.name ?: it.playerChannelName,
                playerGroup = channel?.group ?: it.playerGroup,
                playerIndex = zapIndex,
                playerCount = zapList.size,
                canPrevious = zapIndex > 0,
                canNext = zapIndex < zapList.lastIndex,
                playerError = if (clearError) null else it.playerError,
                playerErrorDetail = if (clearError) null else it.playerErrorDetail,
            )
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        boundHeaders = null
        zapList = emptyList()
        zapIndex = 0
        resumeOnReturn = false
    }

    private fun reloadLibrary() {
        _state.update { it.copy(playlists = readLibrary()) }
    }

    private fun readLibrary(): List<LibraryItem> = store.list().map(::toItem)

    private fun toItem(playlist: StoredPlaylist): LibraryItem {
        val resources = getApplication<Application>().resources
        val count = resources.getQuantityString(
            R.plurals.channel_count,
            playlist.channelCount,
            playlist.channelCount,
        )
        return LibraryItem(
            id = playlist.id,
            name = playlist.name,
            detail = "$count · ${sourceLabel(playlist)}",
            fromUrl = playlist.sourceType == SourceType.Url,
        )
    }

    private fun sourceLabel(playlist: StoredPlaylist): String = when (playlist.sourceType) {
        SourceType.File -> getString(R.string.local_file)
        SourceType.Url -> runCatching { URI(playlist.source).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.remote_list)
    }

    private fun playbackUrl(stored: StoredPlaylist): String = when (stored.sourceType) {
        SourceType.Url -> stored.baseUrl.ifBlank { stored.source }
        SourceType.File -> stored.source
    }

    private fun errorText(error: Exception): String = when (error) {
        is PlaylistTooLargeException -> getString(R.string.too_large)
        is EmptyPlaylistException -> getString(R.string.nothing_found)
        is HttpStatusException -> getString(R.string.http_status, error.code)
        is SecurityException -> getString(R.string.read_failed)
        is IllegalArgumentException -> error.message ?: getString(R.string.generic_error)
        else -> getString(R.string.load_failed)
    }

    private fun getString(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private data class Imported(
        val text: String,
        val channels: List<Channel>,
        val truncated: Boolean,
        val baseUrl: String,
    )

    private data class ImportOutcome(val truncated: Boolean)

    private companion object {
        fun filterChannels(channels: List<Channel>, group: String?, query: String): List<Channel> {
            if (group == null && query.isBlank()) return channels
            val needle = query.trim()
            return channels.filter { channel ->
                (group == null || channel.group == group) &&
                    (needle.isEmpty() ||
                        channel.name.contains(needle, ignoreCase = true) ||
                        channel.group.contains(needle, ignoreCase = true))
            }
        }

        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val lower = withScheme.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
            return withScheme
        }

        fun hostOr(url: String): String =
            runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Lista"

        fun Channel.toMediaItem(index: Int): MediaItem {
            val builder = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(index.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setGenre(group)
                        .build(),
                )
            mimeTypeFor(streamUrl)?.let(builder::setMimeType)
            return builder.build()
        }

        fun mimeTypeFor(url: String): String? {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".ts") || path.endsWith(".mts") -> MimeTypes.VIDEO_MP2T
                path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }
    }
}
