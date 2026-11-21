package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun resetAddState() {
        _addState.value = AddServerUiState()
    }

    /**
     * Validates the server with a ping, then persists it.
     */
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
}
