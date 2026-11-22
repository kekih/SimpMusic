package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericMediaMetadata
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.subsonic.Child
import com.maxrave.simpmusic.subsonic.SubsonicApi
import com.maxrave.simpmusic.subsonic.SubsonicServer
import com.maxrave.simpmusic.subsonic.SubsonicServerRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddServerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val serverName: String? = null,
    val serverId: String? = null,
)

sealed interface BrowseLevel {
    data object Root : BrowseLevel

    data class Directory(
        val id: String,
        val title: String,
    ) : BrowseLevel
}

data class BrowseUiState(
    val server: SubsonicServer? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val path: List<BrowseLevel> = listOf(BrowseLevel.Root),
    val artists: List<com.maxrave.simpmusic.subsonic.Artist> = emptyList(),
    val children: List<Child> = emptyList(),
    val searchQuery: String = "",
    val searchSongs: List<Child> = emptyList(),
    val searchAlbums: List<com.maxrave.simpmusic.subsonic.Album> = emptyList(),
    val syncMessage: String? = null,
)

class SubsonicViewModel(
    private val httpClient: HttpClient,
    private val repository: SubsonicServerRepository,
    private val mediaPlayerHandler: MediaPlayerHandler,
) : ViewModel() {
    private val api = SubsonicApi(httpClient)

    /** Last known library fingerprint per server id (artistCount|childCount). */
    private val librarySnapshots = mutableMapOf<String, String>()

    val servers: StateFlow<List<SubsonicServer>> =
        repository.servers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _addState = MutableStateFlow(AddServerUiState())
    val addState: StateFlow<AddServerUiState> = _addState.asStateFlow()

    private val _browseState = MutableStateFlow(BrowseUiState())
    val browseState: StateFlow<BrowseUiState> = _browseState.asStateFlow()

    fun resetAddState() {
        _addState.value = AddServerUiState()
    }

    fun addServer(
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        useTokenAuth: Boolean = true,
    ) {
        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _addState.value =
                AddServerUiState(error = "URL, username and password are required")
            return
        }

        val normalizedUrl =
            baseUrl.trim().let { url ->
                when {
                    url.startsWith("http://") || url.startsWith("https://") -> url.trimEnd('/')
                    else -> "https://${url.trimEnd('/')}"
                }
            }

        viewModelScope.launch {
            _addState.value = AddServerUiState(isLoading = true)

            val candidate =
                SubsonicServer(
                    id = "temp",
                    name = name.ifBlank { normalizedUrl },
                    baseUrl = normalizedUrl,
                    username = username.trim(),
                    password = password,
                    useTokenAuth = useTokenAuth,
                )

            val ping = api.ping(candidate)
            if (ping.isFailure) {
                _addState.value =
                    AddServerUiState(
                        error = ping.exceptionOrNull()?.message
                            ?: "Could not connect to server",
                    )
                return@launch
            }

            val body = ping.getOrNull()
            val displayName =
                name.ifBlank {
                    listOfNotNull(body?.type, body?.serverVersion)
                        .joinToString(" ")
                        .ifBlank { normalizedUrl }
                }

            runCatching {
                repository.addServer(
                    name = displayName,
                    baseUrl = normalizedUrl,
                    username = username.trim(),
                    password = password,
                    useTokenAuth = useTokenAuth,
                )
            }.onSuccess { server ->
                _addState.value =
                    AddServerUiState(
                        success = true,
                        serverName = server.name,
                        serverId = server.id,
                    )
            }.onFailure { e ->
                _addState.value =
                    AddServerUiState(error = e.message ?: "Failed to save server")
            }
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            repository.removeServer(id)
            librarySnapshots.remove(id)
            if (_browseState.value.server?.id == id) {
                _browseState.value = BrowseUiState()
            }
        }
    }

    fun setEnabled(
        id: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
        }
    }

    fun openServer(server: SubsonicServer) {
        _browseState.value =
            BrowseUiState(
                server = server,
                isLoading = true,
                path = listOf(BrowseLevel.Root),
            )
        loadRoot(server, recordSnapshot = true)
    }

    fun openServerById(id: String) {
        viewModelScope.launch {
            val server = servers.value.find { it.id == id } ?: return@launch
            openServer(server)
        }
    }

    private fun loadRoot(
        server: SubsonicServer,
        recordSnapshot: Boolean,
    ) {
        viewModelScope.launch {
            _browseState.value = _browseState.value.copy(isLoading = true, error = null, syncMessage = null)
            val result = api.getIndexes(server)
            result
                .onSuccess { indexes ->
                    val artists = indexes.index.orEmpty().flatMap { it.artist.orEmpty() }
                    val children = indexes.child.orEmpty()
                    val fingerprint = "${artists.size}|${children.size}"
                    val previous = librarySnapshots[server.id]
                    val syncMessage =
                        when {
                            !recordSnapshot || previous == null -> null
                            previous == fingerprint -> "Библиотека без изменений"
                            else -> {
                                val (prevArtists, prevChildren) =
                                    previous.split("|", limit = 2).let {
                                        (it.getOrNull(0)?.toIntOrNull() ?: 0) to
                                            (it.getOrNull(1)?.toIntOrNull() ?: 0)
                                    }
                                val dA = artists.size - prevArtists
                                val dC = children.size - prevChildren
                                "Синхронизация: артисты ${if (dA >= 0) "+$dA" else "$dA"}, " +
                                    "элементы ${if (dC >= 0) "+$dC" else "$dC"}"
                            }
                        }
                    if (recordSnapshot) {
                        librarySnapshots[server.id] = fingerprint
                    }
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            artists = artists,
                            children = children,
                            path = listOf(BrowseLevel.Root),
                            syncMessage = syncMessage,
                        )
                }.onFailure { e ->
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load library",
                        )
                }
        }
    }

    /** Re-fetch root indexes and compare with last snapshot. */
    fun syncLibrary() {
        val server = _browseState.value.server ?: return
        loadRoot(server, recordSnapshot = true)
    }

    fun openDirectory(
        id: String,
        title: String,
    ) {
        val server = _browseState.value.server ?: return
        viewModelScope.launch {
            _browseState.value = _browseState.value.copy(isLoading = true, error = null)
            val result = api.getMusicDirectory(server, id)
            result
                .onSuccess { dir ->
                    val path = _browseState.value.path + BrowseLevel.Directory(id, title)
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            artists = emptyList(),
                            children = dir.child.orEmpty(),
                            path = path,
                            searchQuery = "",
                            searchSongs = emptyList(),
                            searchAlbums = emptyList(),
                        )
                }.onFailure { e ->
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to open folder",
                        )
                }
        }
    }

    fun navigateBack() {
        val state = _browseState.value
        val server = state.server ?: return
        if (state.path.size <= 1) return
        val newPath = state.path.dropLast(1)
        val last = newPath.last()
        when (last) {
            is BrowseLevel.Root -> loadRoot(server, recordSnapshot = false)
            is BrowseLevel.Directory -> {
                _browseState.value = state.copy(path = newPath.dropLast(1))
                openDirectory(last.id, last.title)
            }
        }
    }

    fun search(query: String) {
        val server = _browseState.value.server ?: return
        _browseState.value = _browseState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _browseState.value =
                _browseState.value.copy(searchSongs = emptyList(), searchAlbums = emptyList())
            return
        }
        viewModelScope.launch {
            _browseState.value = _browseState.value.copy(isLoading = true, error = null)
            val result = api.search3(server, query)
            result
                .onSuccess { res ->
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            searchSongs = res.song.orEmpty(),
                            searchAlbums = res.album.orEmpty(),
                            artists = res.artist.orEmpty(),
                        )
                }.onFailure { e ->
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Search failed",
                        )
                }
        }
    }

    fun streamUrl(songId: String): String? {
        val server = _browseState.value.server ?: return null
        return api.streamUrl(server, songId)
    }

    fun coverArtUrl(id: String?): String? {
        val server = _browseState.value.server ?: return null
        if (id.isNullOrBlank()) return null
        return api.coverArtUrl(server, id)
    }

    /**
     * Queue a Subsonic track for immediate playback.
     *
     * mediaId is prefixed with `subsonic:` so it never collides with a YouTube videoId.
     * uri is the fully authenticated stream endpoint — players that honour GenericMediaItem.uri
     * play it directly; pipelines that only resolve YouTube ids may still fail (see README).
     */
    fun playStream(
        songId: String,
        title: String,
        artist: String?,
        coverUrl: String?,
        album: String? = null,
    ) {
        val url = streamUrl(songId) ?: return
        val mediaItem =
            GenericMediaItem(
                mediaId = "subsonic:$songId",
                uri = url,
                metadata =
                    GenericMediaMetadata(
                        title = title,
                        artist = artist,
                        albumTitle = album,
                        artworkUri = coverUrl,
                        description = MERGING_DATA_TYPE.SONG,
                    ),
                customCacheKey = "subsonic:$songId",
            )
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.clearMediaItems()
                mediaPlayerHandler.addMediaItem(mediaItem, playWhenReady = true)
            }
        }
    }

    fun playChild(song: Child) {
        playStream(
            songId = song.id,
            title = song.title ?: song.id,
            artist = song.artist,
            coverUrl = coverArtUrl(song.coverArt),
            album = song.album,
        )
    }

    fun clearBrowse() {
        _browseState.value = BrowseUiState()
    }
}
