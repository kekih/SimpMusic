package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.simpmusic.subsonic.Child
import com.maxrave.simpmusic.subsonic.SubsonicServer
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Delete
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.BrowseLevel
import com.maxrave.simpmusic.viewModel.SubsonicViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stage 3: browse a Subsonic/Navidrome library and list saved servers.
 *
 * Playback: [onPlayStream] receives the authenticated stream URL when the user taps a song.
 * Wire this to the app player in a later step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubsonicLibraryScreen(
    serverId: String? = null,
    onBack: () -> Unit,
    onPlayStream: (title: String, artist: String?, streamUrl: String, coverUrl: String?) -> Unit = { _, _, _, _ -> },
    viewModel: SubsonicViewModel = koinViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val browse by viewModel.browseState.collectAsStateWithLifecycle()

    LaunchedEffect(serverId) {
        if (serverId != null) {
            viewModel.openServerById(serverId)
        }
    }

    // No server selected yet → server list
    if (browse.server == null) {
        ServerListContent(
            servers = servers,
            onOpen = { viewModel.openServer(it) },
            onRemove = { viewModel.removeServer(it) },
            onToggle = { id, enabled -> viewModel.setEnabled(id, enabled) },
            onBack = onBack,
        )
        return
    }

    val title =
        when (val last = browse.path.lastOrNull()) {
            is BrowseLevel.Directory -> last.title
            else -> browse.server?.name ?: "Subsonic"
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (browse.path.size > 1) {
                                viewModel.navigateBack()
                            } else {
                                viewModel.clearBrowse()
                            }
                        },
                    ) {
                        Icon(SimpIcons.ArrowBackIosNew, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            var query by remember { mutableStateOf(browse.searchQuery) }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.search(it)
                },
                label = { Text("Поиск") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (browse.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (browse.error != null) {
                Text(
                    text = browse.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (browse.searchQuery.isNotBlank()) {
                    items(browse.searchAlbums, key = { "album-${it.id}" }) { album ->
                        LibraryRow(
                            title = album.name ?: album.id,
                            subtitle = album.artist,
                            coverUrl = viewModel.coverArtUrl(album.coverArt),
                            onClick = {
                                viewModel.openDirectory(album.id, album.name ?: album.id)
                            },
                        )
                    }
                    items(browse.searchSongs, key = { "song-${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            coverUrl = viewModel.coverArtUrl(song.coverArt),
                            onClick = {
                                val url = viewModel.streamUrl(song.id) ?: return@SongRow
                                onPlayStream(
                                    song.title ?: song.id,
                                    song.artist,
                                    url,
                                    viewModel.coverArtUrl(song.coverArt),
                                )
                            },
                        )
                    }
                } else {
                    items(browse.artists, key = { "artist-${it.id}" }) { artist ->
                        LibraryRow(
                            title = artist.name ?: artist.id,
                            subtitle = artist.albumCount?.let { "$it albums" },
                            coverUrl = viewModel.coverArtUrl(artist.coverArt),
                            onClick = {
                                viewModel.openDirectory(artist.id, artist.name ?: artist.id)
                            },
                        )
                    }
                    items(browse.children, key = { it.id }) { child ->
                        if (child.isDir == true) {
                            LibraryRow(
                                title = child.title ?: child.id,
                                subtitle = child.artist,
                                coverUrl = viewModel.coverArtUrl(child.coverArt),
                                onClick = {
                                    viewModel.openDirectory(child.id, child.title ?: child.id)
                                },
                            )
                        } else {
                            SongRow(
                                song = child,
                                coverUrl = viewModel.coverArtUrl(child.coverArt),
                                onClick = {
                                    val url = viewModel.streamUrl(child.id) ?: return@SongRow
                                    onPlayStream(
                                        child.title ?: child.id,
                                        child.artist,
                                        url,
                                        viewModel.coverArtUrl(child.coverArt),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListContent(
    servers: List<SubsonicServer>,
    onOpen: (SubsonicServer) -> Unit,
    onRemove: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subsonic / Navidrome") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(SimpIcons.ArrowBackIosNew, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Нет серверов.\nДобавь через + → Добавить библиотеку…",
                    style = typo().bodyMedium,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(servers, key = { it.id }) { server ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = server.enabled) { onOpen(server) }
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = typo().titleSmall)
                        Text(
                            server.baseUrl,
                            style = typo().bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = server.enabled,
                        onCheckedChange = { onToggle(server.id, it) },
                    )
                    IconButton(onClick = { onRemove(server.id) }) {
                        Icon(SimpIcons.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = typo().titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Child,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    val duration =
        song.duration?.let { sec ->
            "%d:%02d".format(sec / 60, sec % 60)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title ?: song.id,
                style = typo().titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub =
                listOfNotNull(song.artist, song.album, duration)
                    .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
