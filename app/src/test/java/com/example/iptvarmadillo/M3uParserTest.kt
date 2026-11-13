package com.example.iptvarmadillo

import com.example.iptvarmadillo.data.M3uParser
import com.example.iptvarmadillo.data.UNGROUPED_GROUP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class M3uParserTest {
    @Test
    fun parsesExtendedPlaylist() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="1" tvg-name="Um" tvg-logo="http://img/1.png" group-title="Notícias",Ignorado
            http://example.com/1
            #EXTINF:0,Dois
            #EXTGRP:Filmes
            http://example.com/2
            não é um canal
            https://example.com/plain.ts
            #EXTINF:-1 tvg-name="Nome, com vírgula" group-title="Esportes",Título
            #EXTVLCOPT:http-user-agent=Provedor
            http://example.com/3
            #EXTINF:-1,Quatro
            http://example.com/4
        """.trimIndent()

        val result = M3uParser.parse(playlist, "Lista", "https://example.com/list.m3u", null)

        assertEquals(5, result.channels.size)
        assertEquals("Um", result.channels[0].name)
        assertEquals("http://img/1.png", result.channels[0].logoUrl)
        assertEquals("Notícias", result.channels[0].group)

        assertEquals("Dois", result.channels[1].name)
        assertEquals("Filmes", result.channels[1].group)
        assertEquals("Filmes", result.channels[2].group)
        assertEquals("plain.ts", result.channels[2].name)

        assertEquals("Nome, com vírgula", result.channels[3].name)
        assertEquals("Esportes", result.channels[3].group)
        assertEquals("Provedor", result.channels[3].userAgent)
        assertEquals("Provedor", result.channels[4].userAgent)
        assertEquals(UNGROUPED_GROUP, result.channels[4].group)
    }

    @Test
    fun resolvesRelativeUrlsAgainstThePlaylist() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Relativo
            canal/ao-vivo.m3u8
        """.trimIndent()

        val result = M3uParser.parse(
            playlist,
            "Lista",
            "https://cdn.example/listas/grade.m3u",
            null,
        )

        assertEquals("https://cdn.example/listas/canal/ao-vivo.m3u8", result.channels.single().streamUrl)
    }

    @Test
    fun treatsHlsMediaPlaylistAsOneChannel() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:8
            #EXTINF:8,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val result = M3uParser.parse(playlist, "Globo", null, "https://cdn.example/live.m3u8")

        assertEquals(1, result.channels.size)
        assertEquals("Globo", result.channels[0].name)
        assertEquals("https://cdn.example/live.m3u8", result.channels[0].streamUrl)
    }

    @Test
    fun keepsChannelListWhenHeaderHasSessionData() {
        val playlist = """
            #EXTM3U x-tvg-url="http://example.com/xmltv.php?username=1&amp;password=2"
            #EXT-X-SESSION-DATA:DATA-ID="com.example.panel"
            # Variedades
            #EXTINF:-1 tvg-name="Um 4K" tvg-id="Um.br" tvg-logo="http://example.com/a%26e.png" group-title="Canais | Variedades",Um 4K
            http://example.com/live/10.ts
            #EXTINF:-1 timeshift="7" tvg-name="Dois HD" group-title="Canais | Abertos",Dois HD
            http://example.com/live/11.ts
        """.trimIndent()

        val result = M3uParser.parse(playlist, "Lista", null, "content://lista")

        assertEquals(2, result.channels.size)
        assertEquals("Um 4K", result.channels[0].name)
        assertEquals("Canais | Variedades", result.channels[0].group)
        assertEquals("http://example.com/a%26e.png", result.channels[0].logoUrl)
        assertEquals("Dois HD", result.channels[1].name)
        assertEquals("Canais | Abertos", result.channels[1].group)
        assertTrue(result.channels.all { it.streamUrl.endsWith(".ts") })
    }

    @Test
    fun parsesPls() {
        val playlist = """
            [playlist]
            File1=http://example.com/a
            Title1=Rádio A
            File2=http://example.com/b
            Title2=Rádio B
            NumberOfEntries=2
        """.trimIndent()

        val result = M3uParser.parse(playlist, "Rádios", null, null)

        assertEquals(listOf("Rádio A", "Rádio B"), result.channels.map { it.name })
        assertEquals("http://example.com/b", result.channels[1].streamUrl)
    }

    @Test
    fun decodesUtf8AndWindows1252() {
        assertEquals("Ação", M3uParser.decode("Ação".toByteArray(Charsets.UTF_8)))
        assertEquals(
            "Ação",
            M3uParser.decode("Ação".toByteArray(Charset.forName("windows-1252"))),
        )
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()
        assertEquals("abc", M3uParser.decode(withBom))
        assertTrue(M3uParser.parse("", "Lista", null, null).channels.isEmpty())
        assertNull(M3uParser.parse("#EXTINF:-1,Sem logo\nhttp://example.com/x", "Lista", null, null).channels.single().logoUrl)
    }
}
