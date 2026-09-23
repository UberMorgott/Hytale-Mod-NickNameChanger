# NickNameChanger 0.0.18

[English](README.md) | Русский

Серверный мод для Hytale 0.6.x. Версия 0.0.18 собрана под сервер 0.6.8; манифест требует сервер 0.6.8 или новее.

Меняет отображаемый никнейм в чате, над персонажем, в списке игроков и на карте. Настоящее имя аккаунта остаётся прежним. Цвета, градиенты, жирный, курсив и подчёркивание доступны в редакторе и через команду. Цвет сообщений настраивается отдельно.

Мод работает самостоятельно. LuckPerms, PlaceholderAPI и другие интеграции необязательны.

О багах пишите в [GitHub Issues](https://github.com/UberMorgott/Hytale-Mod-NickNameChanger/issues). Я не читаю комментарии CurseForge.

## Установка

### Свой мир в игре, Windows

1. Скачайте `NickNameChanger-0.0.18.jar` со [страницы мода](https://www.curseforge.com/hytale/mods/nick-name-changer).
2. Нажмите `Win+R`.
3. Вставьте `%AppData%\Hytale\UserData\Mods` и нажмите Enter. Если папки `Mods` нет, откройте `%AppData%\Hytale\UserData` и создайте её.
4. Переместите скачанный `.jar` в `Mods`. Распаковывать его не нужно.
5. Запустите игру. Откройте настройки своего мира, затем вкладку **Mods**, раздел **Global Mods**.
6. Включите **NickNameChanger** и загрузите мир.
7. Введите `/nnc`. Должен открыться редактор.

Это стандартные пути Windows. Если вы выбрали другую папку данных Hytale, используйте её папку `UserData\Mods`, а для журналов — `UserData\Logs`.

### Выделенный сервер

Положите `.jar` в папку `mods` сервера и перезапустите сервер.

### Проверка загрузки

Для своего мира откройте `%AppData%\Hytale\UserData\Logs`, выберите самый новый файл и найдите строку с префиксом `Integrations:`. В ней NNC пишет о найденных интеграциях и обработчике чата. На выделенном сервере ищите ту же строку в его логе.

### Обновление с 0.0.17 и более ранних версий

Остановите сервер или выйдите из мира. Замените старый `.jar` новым и удалите старую копию, чтобы не загружать две версии одновременно. Сохраните файлы данных мода.

Конфигурация обновляется автоматически. Старые ключи camelCase преобразуются в PascalCase; перед миграцией создаётся `config.json.pre-0.0.18.bak` рядом с `config.json`. Удалять конфиг или данные никнеймов не нужно. Если в конфиге есть оба варианта ключа, значение PascalCase имеет приоритет; отсутствующие вложенные значения переносятся из старой записи.

Если миграция не удалась, загрузка останавливается с ошибкой. Нечитаемые файлы никнеймов не перезаписываются, а ошибки сохранения сообщаются игроку. Уже удалённые или перезаписанные данные обновление не восстановит: для этого нужна прежняя резервная копия.

## Команды и редактор

Основная команда — `/nnc`. `/nick` и `/nickname` — её псевдонимы. Если другой мод занял `/nick`, используйте `/nnc`. В примерах ниже можно заменить `/nick` на `/nnc`.

| Команда | Действие |
|---|---|
| `/nick` | Открыть редактор |
| `/nick <имя>` | Установить никнейм |
| `/nick reset` | Сбросить никнейм и цвет сообщений |
| `/nick msgcolor #FF5555` | Задать цвет сообщений |
| `/nick msgcolor gradient:#FF5555:#55FF55` | Задать градиент сообщений |
| `/nick msgcolor reset` | Сбросить только цвет сообщений |
| `/nick settings` | Открыть общие настройки; нужно `nickname.admin` |

![Редактор никнейма](img/editor_gradient.png)

В редакторе есть 12 вариантов цвета, включая сброс, 8 градиентов, выбор своих цветов и стили текста. Цвет сообщений находится на отдельной вкладке. Предпросмотр обновляется при нажатии кнопок и изменении цвета; введённый текст передаётся при нажатии «Применить».

Примеры форматирования через команду:

```text
/nnc <color:#FF5555>Steve</color>
/nnc <gradient:#FF5555:#55FF55>Steve</gradient>
/nnc <b>Steve</b>
```

Разрешены теги `color`, `gradient`, `b`/`bold`, `i`/`italic`, `u`/`underline`. Для них требуется `nickname.format`. Ограничения символов и длины проверяются после разбора этих тегов, поэтому цвета и градиенты не запрещаются правилом, которое исключает буквальные `<` и `>` из имени.

## Права доступа

| Право | Что разрешает | По умолчанию |
|---|---|---|
| `nickname.use` | Команду и редактор | Да |
| `nickname.format` | Цвета, градиенты и стили никнейма | Да |
| `nickname.msgcolor` | Цвет сообщений | Да |
| `nickname.admin` | Общие настройки | Нет |

Неустановленные права используют значения из таблицы, в том числе с LuckPerms. Явный запрет отключает возможность. Сброс цвета сообщений доступен и без `nickname.msgcolor`, если доступна команда.

Встроенная группа `OP` имеет право `*`, включая доступ к настройкам. Поэтому владелец одиночного мира обычно может открыть `/nnc settings` без отдельной настройки прав.

### LuckPerms

```text
/lp user Steve permission set nickname.admin true
/lp group Admin permission set nickname.admin true
/lp group default permission set nickname.format false
/lp group default permission set nickname.msgcolor false
/lp user Steve permission set nickname.use false
```

### Встроенные права Hytale

Без LuckPerms права задаются в `permissions.json` сервера. Ниже пример секции групп. Не заменяйте им весь существующий файл: сохраните пользователей и остальные группы.

```json
{
  "groups": {
    "OP": { "permissions": ["*"] },
    "Admin": { "permissions": ["nickname.admin", "nickname.format"] },
    "VIP": { "permissions": ["nickname.format"] },
    "Default": { "permissions": [] }
  }
}
```

В этом примере все группы могут менять ник и использовать цвета по умолчанию. Настройки доступны `OP` и `Admin`. Чтобы запретить форматирование группе `Default`, добавьте `"-nickname.format"` в её массив `permissions`. Аналогично работает `"-nickname.msgcolor"`. В LuckPerms используйте `false`, как в примерах выше.

## Настройки отображения

![Настройки](img/settings_panel.png)

В `/nnc settings` три переключателя: чат, имя над персонажем и список игроков. Нажмите «Сохранить», чтобы применить изменения ко всем игрокам онлайн и записать их в конфиг. Сохранённые никнеймы при этом не удаляются.

Карта настраивается отдельно: `Display.ShowOnMap` в `config.json`. Она не связана с переключателем списка игроков.

## Совместимость с другими модами

NNC выбирает свой обработчик чата или уступает поддерживаемому чат-моду. Не включайте одновременно форматирование чата в EssentialsPlus и EliteEssentials: они конфликтуют между собой. Неизвестный сторонний форматтер NNC оставляет без изменений и сообщает о нём в логе при первом сообщении.

Здесь LP означает LuckPerms, PAPI — **HelpChat:PlaceholderAPI**. Установка PAPI сама по себе не добавляет все сторонние плейсхолдеры: нужен мод или расширение, которое их предоставляет.

| Дополнительно установлено | Кто оформляет чат | Настройка никнеймов и цвета сообщений |
|---|---|---|
| Ничего или LuckPerms | NNC, параметр `ChatFormat` | Никнеймы и цвет сообщений работают сразу. LP добавляет префикс и суффикс, включая hex-цвета. В старых бетах LuckPerms 5.5.28 отключите его собственный форматтер: `chat-formatter.enabled: false`. |
| mini-chat-formatter 0.1.x, в том числе с LP/PAPI | MCF | `<username>` автоматически получает никнейм, `<message>` — цвет сообщений. PAPI для этого не нужен. В других версиях MCF используйте `%nnc_nickname_mini%` в формате чата с PAPI. |
| EssentialsPlus, в том числе с LP/MCF/PAPI | EP при включённом `chat.enabled` | NNC синхронизирует никнейм для `{player}` и применяет цвет сообщений, когда EP один обрабатывает чат. Нативная синхронизация EP принимает только `A–Z`, `a–z`, `0–9`, `_`. Другие имена отклоняются с сообщением игроку. Для отображения таких имён используйте `%nnc_nickname_mini%` в формате EP через PAPI. |
| EliteEssentials, в том числе с LP/PAPI | EE при включённом `chatFormat.enabled` | Никнейм синхронизируется для `{player}`. Цвет сообщений автоматически не переносится. Для него нужен PAPI: поставьте `%nnc_msgcolor_legacy%` прямо перед `{message}` в форматах чата EE. У градиента используется первый цвет. |
| HyperPerms | HyperPerms | NNC регистрирует нативный `%nnc_nickname%`. Замените им `%player%` в формате чата HP; PAPI не нужен. Но HyperPerms 2.10.0 не обновлён для сервера 0.6.8: сам мод падает с `NoSuchMethodError ServerPlayerListPlayer.<init>`. Эта версия HP не является рабочим вариантом для 0.6.8. |
| Титулы KyuubiSoft | NNC | Нужны Kyuubi Chat Title Bridge (CurseForge 1581560) и PAPI. Добавьте `%ks_title_raw% ` в `ChatFormat`. В Kyuubi установите `displayMode` в `nametag` или `none`, чтобы он не оформлял тот же чат. `{title}` не является плейсхолдером NNC. |
| MysticNameTags | NNC для чата; Mystic для своих табличек | Для тега в чате добавьте `%mystictags_tag%` в `ChatFormat`, используя расширение PAPI от Mystic. Если табличками управляет Mystic, выключите `Display.ShowOnNameplate` в NNC и вставьте `%nnc_nameplate%` в формат табличек Mystic. |

### Плейсхолдеры NNC

Для PAPI используется идентификатор `nnc`. Проверьте совместимость версии HelpChat PlaceholderAPI с вашим сервером. Наличие адаптера NNC не гарантирует, что любая версия стороннего мода работает на 0.6.8. Плейсхолдеры цвета сообщений возвращают пустую строку, если цвет не сохранён или нет права `nickname.msgcolor`.

| Плейсхолдер | Значение |
|---|---|
| `%nnc_nickname%` | Никнейм без оформления. Настоящее имя, если никнейм не задан или `ShowInChat` выключен |
| `%nnc_nameplate%` | Никнейм без оформления независимо от `ShowInChat`; настоящее имя, если никнейма нет |
| `%nnc_nickname_mini%` | Никнейм в тегах MiniMessage / EssentialsPlus; учитывает `ShowInChat` |
| `%nnc_nickname_legacy%` | Никнейм в кодах `&#RRGGBB`, `&l` и других legacy-кодах; учитывает `ShowInChat` |
| `%nnc_has_nickname%` | `true` или `false` |
| `%nnc_realname%` | Настоящее имя |
| `%nnc_msgcolor_open%` | Открывающий тег цвета или градиента сообщений |
| `%nnc_msgcolor_close%` | Соответствующий закрывающий тег |
| `%nnc_msgcolor_legacy%` | Цвет сообщений в виде `&#RRGGBB`; для градиента — первый цвет |

В формате с поддержкой MiniMessage оберните текст сообщения парой `%nnc_msgcolor_open%` и `%nnc_msgcolor_close%`. Название поля сообщения зависит от чат-мода. Например, для MCF: `%nnc_msgcolor_open%<message>%nnc_msgcolor_close%`. В MCF 0.1.x ручная настройка этого цвета обычно не нужна.

## Конфигурация

Файл создаётся в папке данных сервера: `mods/NickNameChanger_NickNameChanger/config.json`. Это путь относительно сервера, а не путь к общим модам клиента.

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

| Параметр | Назначение |
|---|---|
| `ChatFormat` | Собственный формат NNC: `{prefix}`, `{username}`, `{suffix}`, `{message}`. С PAPI также доступны предоставленные расширениями `%плейсхолдеры%` |
| `Display.ShowInChat` | Никнейм в чате |
| `Display.ShowOnNameplate` | Никнейм над персонажем |
| `Display.ShowInTabList` | Никнейм в списке игроков |
| `Display.ShowOnMap` | Никнейм на карте; по умолчанию выключен |
| `Nicknames.MinLength`, `MaxLength` | Длина видимого текста без тегов |
| `Nicknames.AllowCyrillic` | Кириллица, если свой regex не задан |
| `Nicknames.AllowUnicode` | Другие Unicode-буквы и цифры, если свой regex не задан |
| `Nicknames.AllowedCharactersRegex` | Регулярное выражение для всего видимого имени. Пустая строка включает стандартные правила |
| `Nicknames.UniqueNicknames` | Запрет одинаковых никнеймов без учёта регистра и оформления |
| `Nicknames.BlockRealUsernames` | Запрет совпадения с настоящим именем другого известного серверу игрока |
| `Nicknames.BannedWords` | Запрещённые подстроки без учёта регистра: `admin` также запрещает `MyAdmin` |
| `Integrations.Luckperms.Enabled` | Использовать интеграцию с LP |
| `Integrations.Luckperms.ShowPrefix`, `ShowSuffix` | Показывать префикс и суффикс LP в чате NNC |

Примеры regex: `^[A-Za-z0-9_]+$` для латиницы, цифр и подчёркивания; `^[A-Za-zА-Яа-яЁё0-9_]+$` также разрешает кириллицу.

Без regex доступны латинские буквы, цифры, пробел и `_ - . ! ?`; дополнительные алфавиты задаются флагами выше. Буквальные `< > & § % { } \`, управляющие и невидимые управляющие символы запрещены независимо от regex. Разрешённые теги оформления разбираются до этой проверки. Некорректный regex или диапазон длины блокирует установку новых никнеймов до исправления конфига; причина записывается в лог.

Ключи конфигурации пишутся в PascalCase. Для трёх переключателей отображения удобнее использовать `/nnc settings`.

## Ограничения

- Имя в верхней части инвентаря и экрана персонажа берётся клиентом из вашего аккаунта. Ни один серверный мод не может его изменить.
- Над персонажем, в списке игроков и на карте никнейм выводится обычным текстом. Цвета и градиенты там не отображаются.
- NNC не переписывает настоящее имя в `PlayerRef`. Другие моды могут по-прежнему показывать имя аккаунта, если не используют никнейм или плейсхолдер NNC.
- У EssentialsPlus и HyperPerms есть ограничения, описанные в таблице совместимости.

## Если не работает

1. Проверьте версию сервера: сборка 0.0.18 рассчитана на API 0.6.8.
2. Проверьте, что мод включён для нужного мира и в папке остался только один `.jar` NNC.
3. Попробуйте `/nnc`, особенно если установлен мод с собственной командой `/nick`.
4. Найдите `Integrations:` в новом логе. Сверьте активный чат-мод с таблицей совместимости.
5. Проверьте права, `Display.ShowInChat` и правила никнеймов. Убедитесь, что два чат-мода не оформляют одни и те же сообщения.
6. Если проблема осталась, создайте [GitHub Issue](https://github.com/UberMorgott/Hytale-Mod-NickNameChanger/issues). Укажите версии игры и модов, шаги воспроизведения и приложите относящийся к проблеме лог. Комментарии CurseForge я не читаю.

## API для разработчиков

Класс: `com.nickname.plugin.api.NicknameAPI`. API статический и предназначен для чтения. Запись никнеймов через него не предусмотрена.

Добавьте `.jar` в `libs` и подключите его в `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly(fileTree("libs") { include("NickNameChanger-*.jar") })
}
```

В `manifest.json` своего плагина добавьте:

```json
{
  "OptionalDependencies": {
    "NickNameChanger:NickNameChanger": "*"
  }
}
```

Проверка наличия плагина:

```java
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.common.plugin.PluginIdentifier;

boolean hasNNC = PluginManager.get().getPlugin(
    new PluginIdentifier("NickNameChanger", "NickNameChanger")) != null;
```

Не загружайте класс интеграции, который напрямую обращается к `NicknameAPI`, пока не проверили наличие NNC. При отсутствии `.jar` сам класс API недоступен; его внутренние проверки не защищают от ошибки загрузки класса. Держите прямые обращения в отдельном адаптере.

| Метод | Результат |
|---|---|
| `getNickname(UUID uuid)` | Сырая строка никнейма с тегами или `null` |
| `getDisplayName(UUID uuid, String defaultName)` | Та же сырая строка или переданный ненулевой по контракту `defaultName`; метод не удаляет оформление |
| `hasNickname(UUID uuid)` | Есть ли сохранённый никнейм |
| `getOriginalUsername(UUID uuid)` | Последнее известное NNC настоящее имя, записываемое при входе, или `null` |
| `isShowInChat()` | Глобальная настройка отображения в чате |
| `isShowOnNameplate()` | Глобальная настройка отображения над персонажем |
| `isShowInTabList()` | Глобальная настройка списка игроков; не карты |

`getNickname()` и `getDisplayName()` не применяют `ShowInChat` автоматически. Для чата проверяйте его отдельно:

```java
import com.nickname.plugin.api.NicknameAPI;
import java.util.UUID;

UUID uuid = playerRef.getUuid();
String name = NicknameAPI.isShowInChat()
    ? NicknameAPI.getDisplayName(uuid, playerRef.getUsername())
    : playerRef.getUsername();
```

Пример метода для сообщения о входе. Вызывайте его из адаптера, когда у вас есть готовый `PlayerRef`. Здесь разметка удаляется явно; для цветного сообщения нужен подходящий парсер.

```java
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.nickname.plugin.api.NicknameAPI;

public String joinText(PlayerRef playerRef) {
    String raw = NicknameAPI.getDisplayName(
        playerRef.getUuid(), playerRef.getUsername());
    String plain = raw.replaceAll("<[^>]*>", "");
    String real = playerRef.getUsername();
    return plain.equals(real)
        ? plain + " joined!"
        : plain + " (aka " + real + ") joined!";
}
```

Чтение хранилища синхронизировано; настройки отображения хранятся в `volatile`-полях. Если класс API доступен, но ещё не инициализирован, методы никнейма возвращают `null`, `hasNickname()` — `false`, `getDisplayName()` — переданное значение по умолчанию, а три метода отображения — `true`. Это не проверка успешной загрузки плагина.

## Локализация

Английский (`en-US`), русский (`ru-RU`), бразильский португальский (`pt-BR`).

## Автор и ссылки

Автор: Morgott.

- [CurseForge](https://www.curseforge.com/hytale/mods/nick-name-changer)
- [GitHub Issues](https://github.com/UberMorgott/Hytale-Mod-NickNameChanger/issues) — сообщения об ошибках; комментарии CurseForge я не читаю.


