package com.maxrave.simpmusic.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for a Subsonic-compatible server (Subsonic, Navidrome, Airsonic, etc.).
 */
@Serializable
data class SubsonicServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    /** Stored as plain text for now. Consider encrypting later. */
    val password: String,
    val useTokenAuth: Boolean = true,
    val enabled: Boolean = true,
)

/**
 * Minimal Subsonic REST response envelope.
 * See: https://www.subsonic.org/pages/api.jsp
 */
@Serializable
data class SubsonicResponse(
    @SerialName("subsonic-response")
    val response: SubsonicResponseBody,
)

@Serializable
data class SubsonicResponseBody(
    val status: String,
    val version: String? = null,
    val type: String? = null,
    val serverVersion: String? = null,
    val error: SubsonicError? = null,
    val musicFolders: MusicFolders? = null,
    val indexes: Indexes? = null,
    val directory: Directory? = null,
    val albumList2: AlbumList2? = null,
    val searchResult3: SearchResult3? = null,
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String? = null,
)

@Serializable
data class MusicFolders(
    val musicFolder: List<MusicFolder>? = null,
)

@Serializable
data class MusicFolder(
    val id: Int,
    val name: String? = null,
)

@Serializable
data class Indexes(
    val index: List<Index>? = null,
    val child: List<Child>? = null,
)

@Serializable
data class Index(
    val name: String? = null,
    val artist: List<Artist>? = null,
)

@Serializable
data class Artist(
    val id: String,
    val name: String? = null,
    val coverArt: String? = null,
    val albumCount: Int? = null,
)

@Serializable
data class Directory(
    val id: String,
    val name: String? = null,
    val child: List<Child>? = null,
)

@Serializable
data class Child(
    val id: String,
    val parent: String? = null,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val isDir: Boolean? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val size: Long? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val isVideo: Boolean? = null,
    val year: Int? = null,
    val genre: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val type: String? = null,
)

@Serializable
data class AlbumList2(
    val album: List<Album>? = null,
)

@Serializable
data class Album(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
)

@Serializable
data class SearchResult3(
    val artist: List<Artist>? = null,
    val album: List<Album>? = null,
    val song: List<Child>? = null,
)
