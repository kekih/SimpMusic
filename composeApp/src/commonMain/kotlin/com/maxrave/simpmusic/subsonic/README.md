# Subsonic / Navidrome support (WIP)

Этапная интеграция собственного медиа-сервера (Subsonic API) в SimpMusic.

## Статус

### Этап 1 (сделано)
- [x] Модели (`SubsonicServer`, ответы API)
- [x] Базовый клиент `SubsonicApi`
  - ping
  - getMusicFolders
  - getIndexes
  - getMusicDirectory
  - search3
  - streamUrl / coverArtUrl
- [x] Хранение серверов (`SubsonicServerRepository` через DataStore)

### Этап 2 (следующий)
- [ ] UI: диалог/экран «Добавить библиотеку из Subsonic или Navidrome»
- [ ] Кнопка в разделе создания/библиотеки (как на скриншоте)
- [ ] Проверка соединения (ping) при добавлении
- [ ] Список добавленных серверов в настройках

### Этап 3
- [ ] Отображение библиотеки сервера (артисты / альбомы / треки)
- [ ] Интеграция stream URL в плеер
- [ ] Cover art

### Этап 4
- [ ] Фоновая/ручная синхронизация при появлении новых треков
- [ ] Кэш / offline (по желанию)
- [ ] Несколько серверов одновременно

## Использование (пока только код)

```kotlin
val api = SubsonicApi(httpClient)
val result = api.ping(server)
val folders = api.getMusicFolders(server)
val stream = api.streamUrl(server, songId)
```

## Совместимость

Работает с любым Subsonic-compatible сервером:
- Subsonic
- Navidrome
- Airsonic / Airsonic-Advanced
- и другие, поддерживающие REST API 1.16+
