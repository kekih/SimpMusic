package com.maxrave.simpmusic.subsonic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

/**
 * Minimal Subsonic-compatible REST client.
 *
 * Supports both password and token (salt + md5) authentication.
 * Works with Subsonic, Navidrome, Airsonic and other compatible servers.
 *
 * API reference: https://www.subsonic.org/pages/api.jsp
 */
class SubsonicApi(
    private val client: HttpClient,
) {
    private val clientName = "SimpMusic"
    private val apiVersion = "1.16.1"
    private val format = "json"

    /**
     * Ping the server and verify credentials.
     * Returns the server type/version on success.
     */
    suspend fun ping(server: SubsonicServer): Result<SubsonicResponseBody> =
        request(server, "ping")

    /**
     * Returns the list of music folders configured on the server.
     */
    suspend fun getMusicFolders(server: SubsonicServer): Result<List<MusicFolder>> =
        request(server, "getMusicFolders").map { body ->
            body.musicFolders?.musicFolder.orEmpty()
        }

    /**
     * Returns artist indexes (and optionally children).
     */
    suspend fun getIndexes(
        server: SubsonicServer,
        musicFolderId: Int? = null,
    ): Result<Indexes> =
        request(server, "getIndexes") {
            if (musicFolderId != null) parameter("musicFolderId", musicFolderId)
        }.map { body ->
            body.indexes ?: Indexes()
        }

    /**
     * Returns a directory (album or folder) by id.
     */
    suspend fun getMusicDirectory(
        server: SubsonicServer,
        id: String,
    ): Result<Directory> =
        request(server, "getMusicDirectory") {
            parameter("id", id)
        }.map { body ->
            body.directory ?: error("No directory in response")
        }

    /**
     * Search artists, albums and songs.
     */
    suspend fun search3(
        server: SubsonicServer,
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 20,
    ): Result<SearchResult3> =
        request(server, "search3") {
            parameter("query", query)
            parameter("artistCount", artistCount)
            parameter("albumCount", albumCount)
            parameter("songCount", songCount)
        }.map { body ->
            body.searchResult3 ?: SearchResult3()
        }

    /**
     * Builds a stream URL for a given song id.
     * The player can use this URL directly (with auth already embedded).
     */
    fun streamUrl(
        server: SubsonicServer,
        id: String,
        maxBitRate: Int? = null,
        format: String? = null,
    ): String {
        val params = mutableListOf<
            Pair<String, String>,
        >()
        params += "id" to id
        if (maxBitRate != null) params += "maxBitRate" to maxBitRate.toString()
        if (format != null) params += "format" to format
        return buildUrl(server, "stream", params)
    }

    /**
     * Builds a cover art URL.
     */
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
        extra: (io.ktor.client.request.HttpRequestBuilder.() -> Unit)? = null,
    ): Result<SubsonicResponseBody> =
        runCatching {
            val url = buildUrl(server, action, emptyList())
            val response: HttpResponse =
                client.get(url) {
                    extra?.invoke(this)
                }
            if (!response.status.isSuccess()) {
                error("HTTP ${response.status.value}: ${response.status.description}")
            }
            val envelope: SubsonicResponse = response.body()
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
        val params = mutableListOf<
            Pair<String, String>,
        >()
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
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
