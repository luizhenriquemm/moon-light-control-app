package br.com.moonaudio.moonlightcontrol.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadFileNameTest {

    private val url = "http://10.1.1.200:8080/api/v1/shows/3/export"

    @Test
    fun utf8ExtendedNameKeepsAccents() {
        assertEquals(
            "Culto de domingo – ação.mlcshow",
            DownloadFileName.from(
                """attachment; filename="Culto de domingo - acao.mlcshow"; filename*=UTF-8''Culto%20de%20domingo%20%E2%80%93%20a%C3%A7%C3%A3o.mlcshow""",
                url,
            ),
        )
    }

    @Test
    fun extendedNameWinsWhateverTheOrderAndCase() {
        assertEquals(
            "ação.mlcshow",
            DownloadFileName.from("""Attachment; FILENAME*=utf-8'pt-BR'a%C3%A7%C3%A3o.mlcshow; filename="acao.mlcshow"""", url),
        )
    }

    @Test
    fun plainQuotedAndUnquotedNames() {
        assertEquals("Show 1.mlcshow", DownloadFileName.from("""attachment; filename="Show 1.mlcshow"""", url))
        assertEquals("show.mlcshow", DownloadFileName.from("attachment; filename=show.mlcshow", url))
        assertEquals("a\"b.mlcshow".replace('"', '_'), DownloadFileName.from("""attachment; filename="a\"b.mlcshow"""", url))
    }

    @Test
    fun brokenExtendedNameFallsBackToPlain() {
        assertEquals(
            "plain.mlcshow",
            DownloadFileName.from("""attachment; filename*=UTF-8''bad%Zname; filename="plain.mlcshow"""", url),
        )
        assertEquals(
            "plain.mlcshow",
            DownloadFileName.from("""attachment; filename*=koi8-r''x; filename="plain.mlcshow"""", url),
        )
    }

    @Test
    fun withoutHeaderUsesTheUrl() {
        assertEquals("export", DownloadFileName.from(null, url))
        assertEquals("show ação.json", DownloadFileName.from("attachment", "http://h/files/show%20a%C3%A7%C3%A3o.json?x=1#y"))
    }

    @Test
    fun fallbackWhenNothingUsable() {
        assertEquals("download", DownloadFileName.from(null, "http://h/"))
        assertEquals("show.mlcshow", DownloadFileName.from("attachment; filename=\"\"", "http://h/", fallback = "show.mlcshow"))
        assertEquals("download", DownloadFileName.from(null, null))
    }

    @Test
    fun namesAreMadeSafe() {
        assertEquals("passwd", DownloadFileName.from("""attachment; filename="../../etc/passwd"""", url))
        assertEquals("x.mlcshow", DownloadFileName.from("""attachment; filename="C:\\temp\\x.mlcshow"""", url))
        assertEquals("a_b_c.mlcshow", DownloadFileName.from("attachment; filename*=UTF-8''a%0Ab%3Fc.mlcshow", url))
        assertEquals("hidden", DownloadFileName.from("""attachment; filename=".hidden"""", url))
        assertEquals(120, DownloadFileName.from("attachment; filename=${"a".repeat(300)}", url).length)
    }
}
