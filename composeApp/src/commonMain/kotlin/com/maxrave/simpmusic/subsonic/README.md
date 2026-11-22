# Subsonic / Navidrome support (WIP)

## Статус

### Этап 1 ✅
- Модели, `SubsonicApi`, `SubsonicServerRepository`

### Этап 2 ✅
- Create sheet → «Добавить библиотеку из Subsonic или Navidrome»
- `AddSubsonicServerSheet` + ping при добавлении
- `SubsonicViewModel` + Koin

### Этап 3 ✅
- `SubsonicLibraryScreen` — список серверов, артисты/папки/треки, поиск
- Cover art через `getCoverArt`
- Stream URL через `stream` (auth в query)
- Управление: enable / delete серверов
- После успешного add можно открыть библиотеку по `serverId`

### Этап 4 (следующий)
- Вшить stream URL в Media3 / SharedViewModel (реальное воспроизведение)
- Destination + nav graph
- Синхронизация новых треков на сервере
- Offline / кэш (по желанию)

## Воспроизведение

`SubsonicLibraryScreen(onPlayStream = { title, artist, streamUrl, coverUrl -> ... })`

Сейчас callback нужно связать с плеером приложения (MediaItem с `streamUrl`).
URL уже содержит token/salt или password — можно отдавать ExoPlayer / mpv как есть.

## Как пользоваться

1. **+** → Добавить библиотеку из Subsonic или Navidrome
2. URL + логин + пароль → Добавить и проверить
3. Открыть `SubsonicLibraryScreen` (после add или из списка серверов)
4. Листать / искать / тап по треку → `onPlayStream`
