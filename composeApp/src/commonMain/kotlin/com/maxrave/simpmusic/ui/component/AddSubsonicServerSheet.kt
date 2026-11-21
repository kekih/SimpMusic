package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SubsonicViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Bottom sheet to add a Subsonic / Navidrome (or compatible) library.
 * Tests the connection with ping before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubsonicServerSheet(
    onDismiss: () -> Unit,
    viewModel: SubsonicViewModel = koinViewModel(),
) {
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useTokenAuth by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.resetAddState()
    }

    LaunchedEffect(addState.success) {
        if (addState.success) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            colors =
                CardDefaults.cardColors().copy(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPaddingIme()
                        .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Reuse the same handle style as CreateSheet
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Добавить библиотеку из Subsonic / Navidrome",
                    style = typo().titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    text = "Подключи свой сервер (Subsonic, Navidrome, Airsonic и совместимые)",
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = !addState.isLoading,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL сервера") },
                    placeholder = { Text("https://music.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = !addState.isLoading,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = !addState.isLoading,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    enabled = !addState.isLoading,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Token auth (рекомендуется)", style = typo().bodyMedium)
                        Text(
                            "Navidrome и современные серверы",
                            style = typo().bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useTokenAuth,
                        onCheckedChange = { useTokenAuth = it },
                        enabled = !addState.isLoading,
                    )
                }

                if (addState.error != null) {
                    Text(
                        text = addState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = typo().bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (addState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text("Проверка соединения…", style = typo().bodySmall)
                } else {
                    TextButton(
                        onClick = {
                            viewModel.addServer(
                                name = name,
                                baseUrl = baseUrl,
                                username = username,
                                password = password,
                                useTokenAuth = useTokenAuth,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Добавить и проверить")
                    }
                }

                EndOfModalBottomSheet()
            }
        }
    }
}

@Composable
private fun Modifier.windowInsetsPaddingIme(): Modifier =
    this.then(
        Modifier.padding(0.dp), // placeholder; real ime padding applied via windowInsets below
    ).let { base ->
        // Keep the form above the keyboard the same way CreateLocalPlaylistSheet does.
        androidx.compose.foundation.layout.windowInsetsPadding(
            WindowInsets.ime.exclude(WindowInsets.navigationBars),
        ).let { ime -> base.then(ime) }
    }
