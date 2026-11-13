package com.example.iptvarmadillo.data

data class Channel(
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val group: String,
    val userAgent: String?,
)

data class StoredPlaylist(
    val id: String,
    val name: String,
    val sourceType: SourceType,
    val source: String,
    val baseUrl: String,
    val userAgent: String,
    val referer: String,
    val channelCount: Int,
    val updatedAt: Long,
)

enum class SourceType {
    Url,
    File,
}

data class ParseResult(
    val channels: List<Channel>,
    val truncated: Boolean,
)

const val UNGROUPED_GROUP = "Sem categoria"
const val DEFAULT_USER_AGENT = "IPTVArmadillo/1.0"
const val MAX_CHANNELS = 100_000
const val MAX_PLAYLIST_BYTES = 32 * 1024 * 1024

fun effectiveUserAgent(value: String?): String =
    value?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_USER_AGENT
