package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.simpmusic.subsonic.Child
import com.maxrave.simpmusic.subsonic.SubsonicApi
import com.maxrave.simpmusic.subsonic.SubsonicServer
import com.maxrave.simpmusic.subsonic.SubsonicServerRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
)

class SubsonicViewModel(
    private val httpClient: HttpClient,
    private val repository: SubsonicServerRepository,
) : ViewModel() {
    private val api = SubsonicApi(httpClient)

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
        loadRoot(server)
    }

    fun openServerById(id: String) {
        viewModelScope.launch {
            val server = servers.value.find { it.id == id } ?: return@launch
            openServer(server)
        }
    }

    private fun loadRoot(server: SubsonicServer) {
        viewModelScope.launch {
            _browseState.value = _browseState.value.copy(isLoading = true, error = null)
            val result = api.getIndexes(server)
            result
                .onSuccess { indexes ->
                    val artists = indexes.index.orEmpty().flatMap { it.artist.orEmpty() }
                    val children = indexes.child.orEmpty()
                    _browseState.value =
                        _browseState.value.copy(
                            isLoading = false,
                            artists = artists,
                            children = children,
                            path = listOf(BrowseLevel.Root),
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
            is BrowseLevel.Root -> loadRoot(server)
            is BrowseLevel.Directory -> {
                // reload parent by setting path then opening
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

    fun clearBrowse() {
        _browseState.value = BrowseUiState()
    }
}
