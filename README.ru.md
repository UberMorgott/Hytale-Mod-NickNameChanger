# NickNameChanger

[![en](https://img.shields.io/badge/lang-English-blue)](README.md) [![ru](https://img.shields.io/badge/lang-Русский-green)](README.ru.md)

> **Внимание:** Версия 0.0.18 требует сервер Hytale **0.6.8** или новее.

Серверный плагин для Hytale, позволяющий игрокам менять отображаемый никнейм с цветами, градиентами и стилями текста.

![Редактор никнейма](img/editor_gradient.png)

## Возможности

- **UI Редактор** — Графический интерфейс для настройки никнейма
  - 12 готовых цветов + выбор произвольного цвета
  - 8 готовых градиентов + произвольные цвета градиента
  - Стили текста: Жирный, Курсив, Подчёркнутый
  - Превью в реальном времени
- **Цвет сообщений** — Персональный цвет сообщений в чате через UI-вкладку или команду
- **Чат** — Никнейм отображается в сообщениях чата
- **Табличка над головой** — Имя над персонажем
- **Карта** — Имя на карте мира (настраивается)
- **Таб-лист** — Никнейм в списке игроков
- **Валидация никнеймов** — Настраиваемые правила: мин/макс длина, разрешённые символы (regex), запрещённые слова, кириллица, уникальность, запрет чужих настоящих имён
- **Настоящие имена не меняются** — команды, `/ban`, `/tp`, магазины и плагины голосования работают с настоящим именем игрока
- **Защита от инъекций тегов** — Разрешены только безопасные теги (color, gradient, bold, italic, underline)
- **Админ-панель настроек** — Глобальное управление отображением никнеймов с мгновенным применением ко всем игрокам
- **Конфигурация** — Управление отображением через `config.json` или админ-панель
- **LuckPerms** — Префикс/суффикс в чате с поддержкой hex-цветов (`<#RRGGBB>`)
- **PlaceholderAPI** — `%nnc_nickname%` и др. для других чат-плагинов, а также любые `%плейсхолдеры%` в формате чата NNC
- **API** — Публичный API для чтения никнеймов и настроек отображения другими плагинами

## Команды

Основная команда — /nnc, /nick и /nickname — её синонимы (если /nick занят другим плагином, /nnc работает всегда).

| Команда | Описание |
|---------|----------|
| `/nick` | Открыть редактор никнейма |
| `/nick <имя>` | Установить никнейм через команду |
| `/nick reset` | Сбросить на оригинальное имя |
| `/nick msgcolor <hex>` | Установить цвет сообщений в чате (например `/nick msgcolor #FF5555`) |
| `/nick msgcolor gradient:#HEX1:#HEX2` | Установить градиент цвета сообщений |
| `/nick msgcolor reset` | Сбросить цвет сообщений на стандартный |
| `/nick settings` | Открыть админ-панель настроек (требуется `nickname.admin`) |

## Установка

1. Поместите `NickNameChanger-0.0.18.jar` в папку `Hytale\UserData\Mods`
2. Запустите игру и откройте настройки мира
3. Включите мод в разделе Mods
4. Загрузите мир

## Права доступа

| Право | Описание | По умолчанию |
|---|---|---|
| `nickname.use` | Доступ к команде `/nick` | Разрешено |
| `nickname.format` | Цвета, градиенты, bold/italic/underline в никнейме | Разрешено |
| `nickname.msgcolor` | Цвет сообщений в чате (сбросить цвет можно всегда) | Разрешено |
| `nickname.admin` | Доступ к `/nick settings` (админ-панель) | **Запрещено** |

### Как работают права

`nickname.use`, `nickname.format` и `nickname.msgcolor` **разрешены по умолчанию** — мод работает сразу без настройки. Для ограничения используйте префикс `-`.

`nickname.admin` **запрещено по умолчанию** — доступ к панели настроек есть только у тех, кому право выдано явно. Это функция только для администраторов.

> **Примечание про OP и singleplayer:** Встроенная группа Hytale `OP` имеет wildcard-право (`*`), которое даёт **все** права, включая `nickname.admin`. В singleplayer владелец сервера всегда OP, поэтому `/nick settings` доступен автоматически. На выделенном сервере обычные игроки находятся в группе `Default` и **не имеют** доступа к настройкам, пока вы не выдадите `nickname.admin` явно.

### С LuckPerms

```
# Выдать доступ к настройкам конкретному игроку
/lp user Steve permission set nickname.admin true

# Выдать доступ к настройкам целой группе
/lp group Admin permission set nickname.admin true

# Запретить форматирование для группы по умолчанию (обычные ники без цветов — можно)
/lp group default permission set -nickname.format true

# Полностью запретить /nick конкретному игроку
/lp user Steve permission set -nickname.use true
```

### Без LuckPerms (`permissions.json`)

Отредактируйте `permissions.json` в папке данных мира. Группы проверяются по порядку — назначайте игроков в группы через конфиг сервера Hytale.

```json
{
  "groups": {
    "OP": ["*"],
    "Admin": ["nickname.admin", "nickname.format"],
    "VIP": ["nickname.format"],
    "Default": []
  }
}
```

| Группа | `/nick` | Цвета/Градиенты | `/nick settings` |
|--------|---------|------------------|-------------------|
| OP | Да (`*`) | Да (`*`) | Да (`*`) |
| Admin | Да (по умолч.) | Да (выдано) | Да (выдано) |
| VIP | Да (по умолч.) | Да (выдано) | Нет (запрещено по умолч.) |
| Default | Да (по умолч.) | Да (по умолч.) | Нет (запрещено по умолч.) |

Чтобы **ограничить** форматирование для группы Default, добавьте `"-nickname.format"`:

```json
"Default": ["-nickname.format"]
```

## Админ-панель настроек

Панель настроек (`/nick settings`) даёт администраторам графический интерфейс для глобального управления отображением никнеймов.

![Панель настроек](img/settings_panel.png)

### Как это работает

1. Админ открывает `/nick settings` (нужно право `nickname.admin`)
2. Три галочки управляют глобальным отображением:
   - **Показывать в чате** — отображать ли никнеймы в сообщениях чата
   - **Показывать над головой** — отображать ли никнеймы на табличке над персонажем
   - **Показывать в таб-листе / на карте** — отображать ли никнеймы в списке игроков и на карте мира
3. Нажмите **Сохранить** для применения

### Что происходит при сохранении

- Настройки сохраняются в `config.json` мгновенно
- **Все онлайн-игроки обновляются сразу** — перезапуск или перезаход не нужен
- Когда галочка **снята**: у всех игроков с никнеймами в этом контексте (табличка, таб-лист или чат) отображается оригинальное имя
- Когда галочка **включена обратно**: все никнеймы восстанавливаются мгновенно
- Никнеймы никогда не удаляются — они остаются сохранёнными и просто скрываются или показываются в зависимости от настроек

### Важно

- Настройки **глобальные** — влияют на ВСЕХ игроков на сервере
- Только админы с правом `nickname.admin` могут менять эти настройки
- Изменения сохраняются между перезапусками сервера (записываются в `config.json`)
- Сохранённые никнеймы игроков не затрагиваются — меняется только их отображение

## Конфигурация

Плагин создаёт `config.json` в папке данных (`mods/NickNameChanger_NickNameChanger/`) при первом запуске и дописывает в него новые параметры при обновлении:

```json
{
  "PluginName": "NicknameChanger",
  "Version": "0.0.18",
  "DebugMode": false,
  "ChatFormat": "{prefix}<{username}>{suffix} {message}",
  "Display": {
    "ShowInChat": true,
    "ShowOnNameplate": true,
    "ShowInTabList": true,
    "ShowOnMap": false
  },
  "Nicknames": {
    "MinLength": 2,
    "MaxLength": 32,
    "AllowCyrillic": true,
    "AllowUnicode": false,
    "UniqueNicknames": true,
    "BannedWords": ["admin", "moderator", "server", "owner"],
    "AllowedCharactersRegex": "",
    "BlockRealUsernames": true
  },
  "Integrations": {
    "Luckperms": {
      "Enabled": true,
      "ShowPrefix": true,
      "ShowSuffix": true
    }
  }
}
```

| Параметр | Описание |
|----------|----------|
| `ChatFormat` | Формат чата: `{prefix}`, `{username}`, `{suffix}`, `{message}` и любые `%плейсхолдеры%` PlaceholderAPI |
| `Display.ShowInChat` | Показывать никнеймы в чате |
| `Display.ShowOnNameplate` | Показывать никнеймы над головой |
| `Display.ShowInTabList` | Показывать никнеймы в таб-листе |
| `Display.ShowOnMap` | Показывать никнеймы на карте мира (отдельно от таб-листа) |
| `Nicknames.MinLength` / `MaxLength` | Мин/макс длина видимого текста (теги не считаются) |
| `Nicknames.AllowCyrillic` | Разрешить кириллицу (если `AllowedCharactersRegex` пуст) |
| `Nicknames.AllowUnicode` | Разрешить любые Unicode-буквы и цифры (если `AllowedCharactersRegex` пуст) |
| `Nicknames.AllowedCharactersRegex` | Regex для всего видимого никнейма, например `^[A-Za-z0-9_]+$` или `^[A-Za-zА-Яа-яЁё0-9_]+$`. Пусто — буквы, цифры, пробел и `_ - . ! ?`. Символы `< > & § % { } \` запрещены всегда |
| `Nicknames.UniqueNicknames` | Запретить одинаковые никнеймы (без учёта регистра и цветов) |
| `Nicknames.BlockRealUsernames` | Запретить никнейм, совпадающий с настоящим именем другого игрока, заходившего на сервер |
| `Nicknames.BannedWords` | Список запрещённых слов (без учёта регистра) |
| `Integrations.Luckperms.Enabled` | Включить интеграцию с LuckPerms |
| `Integrations.Luckperms.ShowPrefix` / `ShowSuffix` | Показывать prefix / suffix LuckPerms в чате |

> **Примечание:** Ключи пишутся в **PascalCase**. Конфиги версий 0.0.16 и старше (camelCase) конвертируются автоматически при запуске, оригинал сохраняется как `config.json.pre-0.0.18.bak`. Настройки `Display` удобнее менять через админ-панель (`/nick settings`).
## API для разработчиков

Другие плагины могут читать данные никнеймов и настройки отображения через класс `NicknameAPI` (`com.nickname.plugin.api.NicknameAPI`).

Добавьте NickNameChanger как `compileOnly` зависимость в `build.gradle.kts`:

```kotlin
compileOnly(fileTree("libs") { include("NickNameChanger-*.jar") })
```

### Методы никнеймов

#### `getNickname(UUID uuid): String?`

Возвращает сырую строку никнейма с разметкой (например `<color:#FF5555>Steve</color>`) или `null`, если никнейм не установлен.

```java
String nick = NicknameAPI.getNickname(playerUuid);
if (nick != null) {
    System.out.println("У игрока есть никнейм: " + nick);
}
```

#### `getDisplayName(UUID uuid, String defaultName): String`

Возвращает никнейм игрока или `defaultName`, если никнейм не установлен. Никогда не возвращает `null`.

```java
String name = NicknameAPI.getDisplayName(playerUuid, player.getUsername());
```

#### `hasNickname(UUID uuid): boolean`

Возвращает `true`, если у игрока установлен кастомный никнейм.

```java
if (NicknameAPI.hasNickname(playerUuid)) {
    // игрок использует кастомный никнейм
}
```

#### `getOriginalUsername(UUID uuid): String?`

Возвращает настоящее имя игрока (запоминается при каждом входе) или `null`, если игрок неизвестен.

```java
String original = NicknameAPI.getOriginalUsername(playerUuid);
```

### Методы настроек отображения

Эти методы позволяют другим плагинам проверять текущие глобальные настройки отображения:

#### `isShowInChat(): boolean`

Возвращает `true`, если никнеймы сейчас отображаются в чате.

#### `isShowOnNameplate(): boolean`

Возвращает `true`, если никнеймы сейчас отображаются над головой.

#### `isShowInTabList(): boolean`

Возвращает `true`, если никнеймы сейчас отображаются в таб-листе и на карте.

```java
// Пример: форматировать чат только если никнеймы включены
if (NicknameAPI.isShowInChat() && NicknameAPI.hasNickname(uuid)) {
    String nick = NicknameAPI.getNickname(uuid);
    // использовать никнейм в вашем чат-плагине
}
```

### Заметки

- Все методы **статические** — экземпляр не нужен
- Все методы **потокобезопасные**
- API только для чтения — никнеймы устанавливаются только через `/nick` или UI
- Если NickNameChanger не загружен, все методы безопасно возвращают `null` / `false` / `defaultName` / `true`
- Строки никнеймов могут содержать разметку: `<color:...>`, `<gradient:...>`, `<b>`, `<i>`, `<u>`

## Совместимость: какой чат-плагин работает с LuckPerms + NNC?

Форматировать чат может только **один** плагин. Выберите вариант:

| Вариант | Что сделать |
|---------|-------------|
| **NNC + LuckPerms** (проще всего) | Ничего. NNC сам форматирует чат с prefix/suffix LuckPerms (`ChatFormat`). В старых бетах LuckPerms 5.5.28 был свой форматтер чата — выключите `chat-formatter.enabled: false`. В LuckPerms 5.5.81+ его нет. |
| **mini-chat-formatter + LuckPerms** | Установите PlaceholderAPI от HelpChat (CurseForge 1445106). В `Format` MCF вместо `<username>` используйте `%nnc_nickname_mini%`, например `<prefix>%nnc_nickname_mini%<suffix>: <message>` |
| **EssentialsPlus** | Установите PlaceholderAPI. В форматах групп замените `{player}` на `%nnc_nickname_mini%`. Цвет сообщений: `%nnc_msgcolor_open%{message}%nnc_msgcolor_close%`. Отключите `/nick` EP (`disabledCommands`) или используйте `/nnc`. |
| **EliteEssentials** | Установите PlaceholderAPI, `chatFormat.placeholderapi = true`, замените `{player}` на `%nnc_nickname_legacy%` в `defaultFormat` / `groupFormats`. `nick.enabled = false` или используйте `/nnc`. |
| **HyperPerms** | Используйте `%nnc_nickname%` в формате чата HyperPerms (регистрируется NNC, PlaceholderAPI не нужен). |
| **Титулы KyuubiSoft** | Чат форматирует NNC: установите Kyuubi Chat Title Bridge (CurseForge 1581560) + PlaceholderAPI и добавьте в `ChatFormat`, например, `%ks_title_raw% `. В Kyuubi поставьте `displayMode` = `nametag` или `none`. |
| **MysticNameTags** | Теги в чате NNC: `%mystictags_tag%` в `ChatFormat`. Если табличками управляет Mystic: `ShowOnNameplate` = false в NNC и `%nnc_nickname%` в формате табличек Mystic. |

Если чат форматирует другой плагин, NNC его не трогает и пишет в лог, какой это плагин. Цвет сообщений применяется автоматически только в формате чата NNC.

### Плейсхолдеры (PlaceholderAPI, идентификатор `nnc`)

| Плейсхолдер | Значение |
|-------------|----------|
| `%nnc_nickname%` | Никнейм без цветов (или настоящее имя) |
| `%nnc_nickname_mini%` | Никнейм с цветами в тегах MiniMessage / EssentialsPlus |
| `%nnc_nickname_legacy%` | Никнейм с цветами в кодах `&#RRGGBB` / `&l` |
| `%nnc_has_nickname%` | `true` / `false` |
| `%nnc_realname%` | Настоящее имя |
| `%nnc_msgcolor_open%` / `%nnc_msgcolor_close%` | Теги MiniMessage вокруг сообщения — цвет сообщений игрока |
| `%nnc_msgcolor_legacy%` | Цвет сообщений как `&#RRGGBB` (у градиента — первый цвет) |

Табличка, таб-лист и карта показывают никнейм обычным текстом (градиенты там не отображаются). Настоящее имя игрока не меняется: таб-лист и подписи на карте подменяются при отправке игрокам, поэтому остаются правильными после входа, смены мира, невидимости и режима наблюдателя.
## Ограничения

- **Шапка инвентаря** — Невозможно изменить, так как отрисовывается на стороне клиента

## Локализация

- Английский (en-US)
- Русский (ru-RU)
- Бразильский португальский (pt-BR)

## Credits

- **Автор:** Morgott
