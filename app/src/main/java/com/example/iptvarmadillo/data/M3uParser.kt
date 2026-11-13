package com.example.iptvarmadillo.data

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object M3uParser {
    private val attributePattern = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""")
    private val fileLine = Regex("""(?i)^File(\d+)\s*=\s*(.+)$""")
    private val titleLine = Regex("""(?i)^Title(\d+)\s*=\s*(.+)$""")
    private val plsHint = Regex("""(?im)^File\d+\s*=""")
    private val userAgentJson = Regex(""""User-Agent"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val windows1252 = Charset.forName("windows-1252")
    private val schemes = listOf(
        "http://",
        "https://",
        "rtsp://",
        "rtsps://",
        "rtmp://",
        "rtmps://",
        "mms://",
        "mmsh://",
        "udp://",
        "rtp://",
    )

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, windows1252)
        }
        return text.removePrefix("\uFEFF")
    }

    fun parse(
        content: String,
        fallbackName: String,
        baseUrl: String?,
        hlsPlaybackUrl: String?,
    ): ParseResult {
        val text = content.removePrefix("\uFEFF").trimStart()
        if (text.isEmpty()) return ParseResult(emptyList(), truncated = false)
        // #EXT-X marca um fluxo HLS único. Lista de canais usa #EXTINF e a URL de cada canal.
        if (isHls(text)) {
            val playback = hlsPlaybackUrl?.trim().orEmpty()
            if (playback.isEmpty()) return ParseResult(emptyList(), truncated = false)
            return ParseResult(
                channels = listOf(
                    Channel(
                        name = fallbackName.ifBlank { "Ao vivo" },
                        streamUrl = playback,
                        logoUrl = null,
                        group = UNGROUPED_GROUP,
                        userAgent = null,
                    ),
                ),
                truncated = false,
            )
        }
        val (channels, truncated) = if (isPls(text)) {
            val parsed = parsePls(text, baseUrl)
            if (parsed.size > MAX_CHANNELS) parsed.take(MAX_CHANNELS) to true else parsed to false
        } else {
            parseM3u(text, baseUrl)
        }
        return ParseResult(channels, truncated)
    }

    private fun isHls(text: String): Boolean =
        text.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
            text.contains("#EXT-X-STREAM-INF", ignoreCase = true) ||
            text.contains("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true)

    private fun isPls(text: String): Boolean {
        if (text.contains("#EXTINF", ignoreCase = true) || text.startsWith("#EXTM3U", ignoreCase = true)) {
            return false
        }
        val sample = text.take(4096)
        return sample.contains("[playlist]", ignoreCase = true) || plsHint.containsMatchIn(sample)
    }

    private fun parsePls(text: String, baseUrl: String?): List<Channel> {
        val files = sortedMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            fileLine.matchEntire(line)?.let { match ->
                files[match.groupValues[1].toInt()] = match.groupValues[2].trim()
                return@forEach
            }
            titleLine.matchEntire(line)?.let { match ->
                titles[match.groupValues[1].toInt()] = unescape(match.groupValues[2].trim())
            }
        }
        return files.mapNotNull { (index, rawUrl) ->
            val url = resolveUrl(baseUrl, rawUrl)
            if (url.isBlank()) return@mapNotNull null
            val name = titles[index]?.takeIf { it.isNotBlank() } ?: nameFromUrl(url)
            Channel(
                name = name,
                streamUrl = url,
                logoUrl = null,
                group = UNGROUPED_GROUP,
                userAgent = null,
            )
        }
    }

    private fun parseM3u(text: String, baseUrl: String?): Pair<List<Channel>, Boolean> {
        val channels = mutableListOf<Channel>()
        var pending: PendingInfo? = null
        var stickyGroup: String? = null
        var stickyUserAgent: String? = null
        var truncated = false
        text.lineSequence().forEach { raw ->
            if (channels.size >= MAX_CHANNELS) {
                if (raw.isNotBlank()) truncated = true
                return@forEach
            }
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#")) {
                when {
                    line.startsWith("#EXTINF:", ignoreCase = true) -> pending = parseExtInf(line)
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        stickyGroup = unescape(line.substringAfter(':')).trim().ifBlank { null }
                    }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val value = line.substringAfter(':')
                        if (value.startsWith("http-user-agent=", ignoreCase = true)) {
                            stickyUserAgent = cleanToken(value.substringAfter('='))
                        }
                    }
                    line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                        userAgentJson.find(line)?.let { match ->
                            stickyUserAgent = cleanToken(match.groupValues[1])
                        }
                    }
                }
                return@forEach
            }
            if (pending == null && !looksLikeUrl(line)) return@forEach
            val url = resolveUrl(baseUrl, line)
            if (url.isBlank()) {
                pending = null
                return@forEach
            }
            val name = pending?.name?.takeIf { it.isNotBlank() } ?: nameFromUrl(url)
            val group = pending?.group ?: stickyGroup ?: UNGROUPED_GROUP
            if (pending?.group != null) stickyGroup = null
            channels += Channel(
                name = name,
                streamUrl = url,
                logoUrl = pending?.logo,
                group = group,
                userAgent = stickyUserAgent,
            )
            pending = null
        }
        return channels to truncated
    }

    private fun parseExtInf(line: String): PendingInfo {
        val payload = line.substring(line.indexOf(':') + 1)
        val comma = indexOfTitleComma(payload)
        val meta = if (comma >= 0) payload.substring(0, comma) else payload
        val title = if (comma >= 0) unescape(payload.substring(comma + 1).trim()) else ""
        val attributes = parseAttributes(meta)
        val tvgName = attributes["tvg-name"]?.let(::unescape)?.trim().orEmpty()
        val logo = attributes["tvg-logo"]?.let(::unescape)?.trim()?.ifBlank { null }
        val group = attributes["group-title"]?.let(::unescape)?.trim()?.ifBlank { null }
        return PendingInfo(name = tvgName.ifBlank { title }, logo = logo, group = group)
    }

    private fun parseAttributes(meta: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        attributePattern.findAll(meta).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = when {
                match.groups[2] != null -> match.groupValues[2]
                match.groups[3] != null -> match.groupValues[3]
                else -> match.groupValues[4]
            }
            values[key] = value
        }
        return values
    }

    private fun indexOfTitleComma(payload: String): Int {
        var quote: Char? = null
        for (index in payload.indices) {
            val character = payload[index]
            if (quote != null) {
                if (character == quote) quote = null
            } else {
                when (character) {
                    '"', '\'' -> quote = character
                    ',' -> return index
                }
            }
        }
        return -1
    }

    private fun looksLikeUrl(value: String): Boolean {
        val lower = value.trim().lowercase()
        return lower.startsWith("//") || schemes.any { lower.startsWith(it) }
    }

    private fun resolveUrl(base: String?, raw: String): String {
        val value = raw.trim().trim('"').trim('\'')
        if (value.isEmpty()) return ""
        val lower = value.lowercase()
        if (schemes.any { lower.startsWith(it) }) return value
        if (value.startsWith("//")) {
            val scheme = base
                ?.substringBefore("://", "")
                ?.lowercase()
                ?.takeIf { it == "http" || it == "https" }
                ?: "https"
            return "$scheme:$value"
        }
        if (base.isNullOrBlank()) return value
        return runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
    }

    private fun nameFromUrl(url: String): String {
        val leaf = runCatching { URI(url).path }
            .getOrNull()
            .orEmpty()
            .substringAfterLast('/')
            .substringBefore('?')
            .trim()
        return leaf.take(80).ifBlank { "Canal" }
    }

    private fun unescape(value: String): String =
        value.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\u0000", "")
            .trim()

    private fun cleanToken(value: String): String? =
        value.trim().trim('"').trim('\'').takeIf { it.isNotEmpty() }

    private data class PendingInfo(
        val name: String,
        val logo: String?,
        val group: String?,
    )
}
