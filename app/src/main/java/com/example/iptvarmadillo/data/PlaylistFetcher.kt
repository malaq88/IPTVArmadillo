package com.example.iptvarmadillo.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class HttpStatusException(val code: Int) : Exception()

class PlaylistTooLargeException : Exception()

class EmptyPlaylistException : Exception()

data class FetchedPlaylist(
    val bytes: ByteArray,
    val finalUrl: String,
)

class PlaylistFetcher {
    fun download(url: String, userAgent: String?, referer: String?): FetchedPlaylist {
        var current = url.trim()
        var redirects = 0
        while (redirects < 5) {
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", effectiveUserAgent(userAgent))
                connection.setRequestProperty("Accept", "*/*")
                if (!referer.isNullOrBlank()) {
                    connection.setRequestProperty("Referer", referer)
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")?.trim().orEmpty()
                    if (location.isEmpty()) error("Redirecionamento sem destino")
                    current = resolveRedirect(current, location)
                    redirects++
                    continue
                }
                if (code !in 200..299) throw HttpStatusException(code)
                val bytes = connection.inputStream.use { it.readCapped() }
                return FetchedPlaylist(bytes, current)
            } finally {
                connection.disconnect()
            }
        }
        error("Muitos redirecionamentos")
    }

    fun read(context: Context, uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Não foi possível abrir o arquivo")
        return stream.use { it.readCapped() }
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "lista.m3u"
    }

    fun persistReadPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // A leitura imediata ainda funciona. Atualizar depois pode pedir o arquivo de novo.
        }
    }

    private fun resolveRedirect(current: String, location: String): String {
        val lower = location.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return location
        return runCatching { URI(current).resolve(location).toString() }.getOrDefault(location)
    }

    private fun InputStream.readCapped(): ByteArray {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_PLAYLIST_BYTES) throw PlaylistTooLargeException()
            output.write(chunk, 0, read)
        }
        return output.toByteArray()
    }
}
