package com.example.iptvarmadillo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PlaylistStore(context: Context) {
    private val root = File(context.filesDir, "playlists").apply { mkdirs() }
    private val index = File(root, "index.json")

    @Synchronized
    fun list(): List<StoredPlaylist> {
        if (!index.exists()) return emptyList()
        return try {
            val array = JSONArray(index.readText(Charsets.UTF_8))
            buildList {
                for (position in 0 until array.length()) {
                    val item = array.optJSONObject(position) ?: continue
                    readPlaylist(item)?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun get(id: String): StoredPlaylist? = list().find { it.id == id }

    @Synchronized
    fun content(id: String): String = contentFile(id).readText(Charsets.UTF_8)

    @Synchronized
    fun put(playlist: StoredPlaylist, content: String) {
        writeAtomic(contentFile(playlist.id), content)
        val updated = list().filterNot { it.id == playlist.id } + playlist
        writeAtomic(index, toJson(updated.sortedByDescending { it.updatedAt }))
    }

    @Synchronized
    fun delete(id: String) {
        contentFile(id).delete()
        writeAtomic(index, toJson(list().filterNot { it.id == id }))
    }

    private fun readPlaylist(item: JSONObject): StoredPlaylist? {
        val id = item.optString("id")
        if (!id.matches(idPattern)) return null
        val sourceType = runCatching { SourceType.valueOf(item.optString("sourceType")) }.getOrNull()
            ?: return null
        val name = item.optString("name").ifBlank { "Lista" }
        return StoredPlaylist(
            id = id,
            name = name,
            sourceType = sourceType,
            source = item.optString("source"),
            baseUrl = item.optString("baseUrl"),
            userAgent = item.optString("userAgent"),
            referer = item.optString("referer"),
            channelCount = item.optInt("channelCount"),
            updatedAt = item.optLong("updatedAt"),
        )
    }

    private fun toJson(items: List<StoredPlaylist>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("sourceType", item.sourceType.name)
                    .put("source", item.source)
                    .put("baseUrl", item.baseUrl)
                    .put("userAgent", item.userAgent)
                    .put("referer", item.referer)
                    .put("channelCount", item.channelCount)
                    .put("updatedAt", item.updatedAt),
            )
        }
        return array.toString()
    }

    private fun contentFile(id: String): File {
        require(id.matches(idPattern)) { "Identificador inválido" }
        return File(root, "$id.m3u")
    }

    private fun writeAtomic(file: File, text: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text, Charsets.UTF_8)
        if (file.exists() && !file.delete()) {
            file.writeText(text, Charsets.UTF_8)
            temporary.delete()
            return
        }
        if (!temporary.renameTo(file)) {
            file.writeText(text, Charsets.UTF_8)
            temporary.delete()
        }
    }

    private companion object {
        val idPattern =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
