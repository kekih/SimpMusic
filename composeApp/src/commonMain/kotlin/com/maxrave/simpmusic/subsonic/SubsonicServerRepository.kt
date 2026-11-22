package com.maxrave.simpmusic.subsonic

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Persists Subsonic/Navidrome server configurations.
 *
 * Uses a single DataStore preference key that holds a JSON list of [SubsonicServer].
 * This is intentionally simple for stage 1; can later move into the main data layer.
 */
class SubsonicServerRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val serversKey = stringPreferencesKey("subsonic_servers")

    val servers: Flow<List<SubsonicServer>> =
        dataStore.data.map { prefs ->
            val raw = prefs[serversKey] ?: return@map emptyList()
            runCatching {
                json.decodeFromString<List<SubsonicServer>>(raw)
            }.getOrDefault(emptyList())
        }

    suspend fun addServer(
        name: String,
        baseUrl: String,
        username: String,
        password: String,
        useTokenAuth: Boolean = true,
    ): SubsonicServer {
        val server =
            SubsonicServer(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { baseUrl },
                baseUrl = baseUrl.trimEnd('/'),
                username = username,
                password = password,
                useTokenAuth = useTokenAuth,
                enabled = true,
            )
        updateServers { current -> current + server }
        return server
    }

    suspend fun updateServer(server: SubsonicServer) {
        updateServers { current ->
            current.map { if (it.id == server.id) server else it }
        }
    }

    suspend fun removeServer(id: String) {
        updateServers { current -> current.filterNot { it.id == id } }
    }

    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) {
        updateServers { current ->
            current.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
        }
    }

    private suspend fun updateServers(transform: (List<SubsonicServer>) -> List<SubsonicServer>) {
        dataStore.edit { prefs ->
            val current =
                prefs[serversKey]?.let { raw ->
                    runCatching { json.decodeFromString<List<SubsonicServer>>(raw) }
                        .getOrDefault(emptyList())
                } ?: emptyList()
            prefs[serversKey] = json.encodeToString(transform(current))
        }
    }
}
