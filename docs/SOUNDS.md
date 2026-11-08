# Звуки и озвучка Системы

У каждого события Системы есть свой звук. По умолчанию звуки ссылаются на подходящие ванильные, поэтому мод звучит сразу после установки. Проверить любой звук можно в игре: окно **STATUS → «Звуки»**, кнопка ▶ рядом с каждым событием.

| Событие (id) | Когда звучит |
|---|---|
| `solostatus:system.open` / `system.close` | открытие / закрытие окна Системы |
| `system.click` | выбор, переключение окна |
| `system.stat_up` | вложено очко характеристики / изучен навык |
| `system.notice` | обычное уведомление |
| `system.level_up` | повышение уровня |
| `system.awakening` | пробуждение навыка |
| `system.achievement` / `system.hidden_achievement` | достижение / скрытое достижение |
| `system.rank_up` | новый Hunter Rank |
| `system.title` | получен или надет титул, выбран класс |
| `system.error` | ошибка (нет энергии, нет цели и т.п.) |
| `system.presence` | рядом другой охотник открыл Систему |

## Своя озвучка (например, голос «Системы»)

Мод пересобирать не нужно, достаточно ресурспака:

```
MySystemVoice/
├─ pack.mcmeta
└─ assets/solostatus/
   ├─ sounds.json
   └─ sounds/voice/
      ├─ level_up.ogg
      └─ achievement.ogg
```

`pack.mcmeta`:
```json
{ "pack": { "pack_format": 34, "description": "Голос Системы" } }
```

`assets/solostatus/sounds.json` (заменяет только перечисленные события, остальные остаются по умолчанию):
```json
{
  "system.level_up":   { "replace": true, "subtitle": "subtitles.solostatus.system.level_up",   "sounds": [ "solostatus:voice/level_up" ] },
  "system.achievement":{ "replace": true, "subtitle": "subtitles.solostatus.system.achievement", "sounds": [ "solostatus:voice/achievement" ] }
}
```

Требования к файлам: формат **Ogg Vorbis**, моно (для звуков интерфейса), 44.1 или 48 кГц. Громкость управляется ползунком «Общая» в настройках звука.
