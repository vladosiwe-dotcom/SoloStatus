# Интеграции, версии и риски

> **Важно о проверке версий.** Код писался без доступа к Maven NeoForge и Modrinth: сетевая политика облачной среды их блокировала. Поэтому точные версии библиотек ниже — **ориентиры, а не проверенные факты**. Перед сборкой сверьте их по ссылкам.

## Таблица совместимости

| Компонент | Ожидаемая версия | Тип зависимости | Где проверить |
|---|---|---|---|
| Minecraft | 1.21.1 | обязательно | — |
| NeoForge | 21.1.x (в проекте 21.1.172) | обязательно | https://projects.neoforged.net/neoforged/neoforge |
| ModDevGradle | 2.0.x | сборка | https://projects.neoforged.net/neoforged/moddevgradle |
| Java | 21 | сборка | https://adoptium.net/ |
| Epic Fight | ветка для 1.21.1 NeoForge | необязательно | https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod · https://github.com/Epic-Fight/epicfight |
| Iron's Spells 'n Spellbooks | ветка для 1.21.1 NeoForge | необязательно | https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks · https://github.com/iron431/irons-spells-n-spellbooks |
| Legendary Tooltips | ветка для 1.21.1 NeoForge | необязательно | https://www.curseforge.com/minecraft/mc-mods/legendary-tooltips |
| Simple Voice Chat | ветка для 1.21.1 NeoForge | необязательно (будущее) | https://modrepo.de/minecraft/voicechat |
| Better Combat | **не использовать вместе с Epic Fight** | — | — |

## Подтверждённые механизмы (стандартный API NeoForge/Minecraft)

- Data attachments, data components, networking payloads, ModConfigSpec, global loot modifiers, ItemAttributeModifierEvent, события урона, блока, смерти и добычи, GUI layers, key mappings.
- **Iron's Spells, атрибуты.** Модификаторы применяются через ванильный реестр атрибутов по id `irons_spellbooks:spell_power`, `max_mana`, `mana_regen`, `cooldown_reduction`, `<школа>_spell_power`. Если атрибут не найден, модификатор просто не ставится.
- **Iron's Spells, школа магии по урону.** Тип урона `irons_spellbooks:<школа>_magic` используется для магического мастерства.
- **Epic Fight, урон.** Атаки Epic Fight проходят через стандартные события урона, поэтому STR, PER, крит, мастерство, качество оружия и пассивки к ним применяются. AGI повышает атрибут скорости атаки, от которого Epic Fight масштабирует скорость анимаций.
- **Legendary Tooltips.** Качество выставляет ванильный компонент `minecraft:rarity`. По нему Legendary Tooltips выбирает рамку по умолчанию, а цвет названия меняется даже без этого мода.

## Предположения, требующие проверки

| Что | Как сделано | Риск | Что делать, если не работает |
|---|---|---|---|
| Iron's: событие `io.redspace.ironsspellbooks.api.events.SpellOnCastEvent` с методами `getEntity`, `getSchoolType`, `getSpellLevel` | Подключается через отражение | Названия могли измениться | В логе будет предупреждение. Мастерство магии продолжит расти от урона заклинаниями |
| Iron's: названия атрибутов | По id реестра | Переименование | Модификатор не применится, ошибки не будет |
| Iron's: имена типов урона `*_magic` | Разбор id | Другое именование | Мастерство будет записано под полным именем типа |
| Epic Fight: блок/парирование через ванильный `LivingShieldBlockEvent` | Стандартное событие | Epic Fight может обрабатывать guard своим механизмом | Подключить события Epic Fight (см. ниже) |

## Что требует API-jar Epic Fight (следующий этап)

Чтобы не создавать фиктивные методы, следующие части **не реализованы**, а изолированы в `compat/epicfight`:

1. **Анимации навыков.** Сейчас навыки используют ванильный взмах, частицы и звуки. Нужно получить `PlayerPatch` через capability Epic Fight и вызвать проигрывание анимации из его реестра анимаций.
2. **Уклонения и парирования Epic Fight в счётчики пробуждения.** Нужно подписаться на события Epic Fight (dodge / guard / parry success) и вызывать `PlayerData.addCounter("dodges" | "perfect_guards" | "counters")`.
3. **Регистрация навыков системы как Skill Epic Fight** (по желанию).

Как подключить: добавить в `build.gradle` репозиторий и `compileOnly` / `localRuntime` зависимость на Epic Fight нужной версии (или положить jar в `libs/` и подключить `compileOnly files('libs/epicfight-....jar')`). Затем реализовать вызовы в `EpicFightCompat` и обращаться к нему только при `CompatManager.epicFightLoaded()`.

## Голосовая команда «Система, покажи статус»

- Уже работает **текстовый вариант**: фраза в чате открывает окно и не отправляется в чат (настраивается в `solostatus-client.toml`). Клавиша **K** работает независимо.
- Simple Voice Chat **не распознаёт речь**, он только передаёт звук. Безопасная схема:
  1. Клиентский адаптер получает звук микрофона (через API Simple Voice Chat или напрямую).
  2. **Локальный** распознаватель (например, Vosk с небольшой русской моделью) превращает звук в текст на ПК игрока. Звук никуда не отправляется.
  3. Текст передаётся в `VoiceCommands.match(...)`, а команда `OPEN_STATUS` открывает окно.
  4. Внешние облачные распознаватели допустимы только при явном включении в конфиге с понятным предупреждением.
- Это отдельный модуль с нативной библиотекой и моделью (~50 МБ). Он вынесен в следующий этап, чтобы не утяжелять мод по умолчанию.

## Технические риски

1. **Компиляция не проверена в этой среде.** Возможны точечные расхождения сигнатур NeoForge 21.1.x (например, у `LivingShieldBlockEvent`, `CriticalHitEvent`, `MobEffectEvent.Applicable`). Их нужно исправить по логу сборки.
2. Баланс урона рассчитан на ванилу + Epic Fight. С крупными модами на мобов стоит настроить `xpMultiplier` и пороги в конфиге.
3. Модификатор качества `ADD_MULTIPLIED_TOTAL` к атаке складывается с другими модами, которые меняют атаку тем же способом.
