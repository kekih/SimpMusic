package com.maxrave.simpmusic.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Minimal Subsonic-compatible REST client.
 *
 * Supports password and token (salt + md5) auth.
 * Works with Subsonic, Navidrome, Airsonic and other compatible servers.
 *
 * API: https://www.subsonic.org/pages/api.jsp
 */
class SubsonicApi(
    private val client: HttpClient = defaultClient(),
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
) {
    private val clientName = "SimpMusic"
    private val apiVersion = "1.16.1"
    private val format = "json"

    suspend fun ping(server: SubsonicServer): Result<SubsonicResponseBody> = request(server, "ping")

    suspend fun getMusicFolders(server: SubsonicServer): Result<List<MusicFolder>> =
        request(server, "getMusicFolders").map { body ->
            body.musicFolders?.musicFolder.orEmpty()
        }

    suspend fun getIndexes(
        server: SubsonicServer,
        musicFolderId: Int? = null,
    ): Result<Indexes> {
        val extra =
            if (musicFolderId != null) {
                listOf("musicFolderId" to musicFolderId.toString())
            } else {
                emptyList()
            }
        return request(server, "getIndexes", extra).map { body ->
            body.indexes ?: Indexes()
        }
    }

    suspend fun getMusicDirectory(
        server: SubsonicServer,
        id: String,
    ): Result<Directory> =
        request(server, "getMusicDirectory", listOf("id" to id)).map { body ->
            body.directory ?: error("No directory in response")
        }

    suspend fun search3(
        server: SubsonicServer,
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 20,
    ): Result<SearchResult3> =
        request(
            server,
            "search3",
            listOf(
                "query" to query,
                "artistCount" to artistCount.toString(),
                "albumCount" to albumCount.toString(),
                "songCount" to songCount.toString(),
            ),
        ).map { body ->
            body.searchResult3 ?: SearchResult3()
        }

    fun streamUrl(
        server: SubsonicServer,
        id: String,
        maxBitRate: Int? = null,
        format: String? = null,
    ): String {
        val params = mutableListOf("id" to id)
        if (maxBitRate != null) params += "maxBitRate" to maxBitRate.toString()
        if (format != null) params += "format" to format
        return buildUrl(server, "stream", params)
    }

    fun coverArtUrl(
        server: SubsonicServer,
        id: String,
        size: Int? = 300,
    ): String {
        val params = mutableListOf("id" to id)
        if (size != null) params += "size" to size.toString()
        return buildUrl(server, "getCoverArt", params)
    }

    private suspend fun request(
        server: SubsonicServer,
        action: String,
        extraParams: List<Pair<String, String>> = emptyList(),
    ): Result<SubsonicResponseBody> =
        runCatching {
            val url = buildUrl(server, action, extraParams)
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                error("HTTP ${response.status.value}: ${response.status.description}")
            }
            val text = response.bodyAsText()
            val envelope = json.decodeFromString(SubsonicResponse.serializer(), text)
            val body = envelope.response
            if (body.status != "ok") {
                val err = body.error
                error("Subsonic error ${err?.code}: ${err?.message ?: "unknown"}")
            }
            body
        }

    private fun buildUrl(
        server: SubsonicServer,
        action: String,
        extraParams: List<Pair<String, String>>,
    ): String {
        val base = server.baseUrl.trimEnd('/')
        val params = mutableListOf<Pair<String, String>>()
        params += "u" to server.username
        if (server.useTokenAuth) {
            val salt = randomSalt()
            val token = md5(server.password + salt)
            params += "t" to token
            params += "s" to salt
        } else {
            params += "p" to server.password
        }
        params += "v" to apiVersion
        params += "c" to clientName
        params += "f" to format
        params += extraParams

        val query =
            params.joinToString("&") { (k, v) ->
                "${encode(k)}=${encode(v)}"
            }
        return "$base/rest/$action.view?$query"
    }

    private fun randomSalt(length: Int = 12): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { alphabet[Random.nextInt(alphabet.length)] }
            .joinToString("")
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { b ->
            val i = b.toInt() and 0xff
            i.toString(16).padStart(2, '0')
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private fun defaultClient(): HttpClient =
            HttpClient(CIO) {
                expectSuccess = false
            }
    }
}
