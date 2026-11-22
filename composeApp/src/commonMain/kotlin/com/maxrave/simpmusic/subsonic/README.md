# Subsonic / Navidrome support (WIP)

## Статус

### Этап 1–3 ✅
API, хранение, UI добавления, браузер библиотеки, cover art, stream URL

### Этап 4 ✅
- `playStream` / `playChild` → `MediaPlayerHandler.clearMediaItems()` + `addMediaItem(GenericMediaItem)`
- `mediaId = "subsonic:{id}"`, `uri = authenticated stream URL`
- Кнопка **Синхр.**: повторный `getIndexes`, сравнение fingerprint артистов/элементов
- UI тапает трек → воспроизведение через handler

### Известные ограничения
Плеер SimpMusic заточен под YouTube: stream resolver может игнорировать `uri` и пытаться резолвить `mediaId` как videoId. Если так — трек не заиграет, пока в `MediaServiceHandler` / ExoPlayer adapter не добавят ветку «если uri начинается с http → играть напрямую».

### Дальше (по желанию)
- Патч core: direct-URI playback для `subsonic:` mediaId
- Nav destination вместо Dialog
- Фоновый периодический sync
- Offline кэш Subsonic треков

## Использование

1. **+** → Добавить библиотеку… → ping → сохранить
2. **+** → Открыть библиотеку Subsonic → сервер → трек
3. **Синхр.** в шапке библиотеки — обновить indexes и увидеть дельту
