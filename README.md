# NickNameChanger

A Hytale server plugin that lets players customize their display nickname with colors, gradients, and text formatting.

## Features

- **Custom Nicknames**: Set display names with `/nick` command or graphical UI editor
- **Color Support**: Solid colors, gradients, bold, italic, underline
- **Message Color**: Customize your chat message text color
- **Graphical UI**: Full visual editor with color pickers, gradient presets, and preview
- **Nickname rules**: Length, allowed characters (regex), banned words, unique nicknames, no copying of other players' real names
- **Real names stay real**: commands, `/ban`, `/tp`, shops and vote plugins keep using the player's real username
- **LuckPerms Integration**: Displays LP prefixes/suffixes in chat
- **Chat plugins**: nicknames show up automatically in mini-chat-formatter, EssentialsPlus and EliteEssentials chat; HyperPerms via `%nnc_nickname%`
- **PlaceholderAPI** (optional): `%nnc_nickname%` & co. for any plugin, and any `%placeholder%` inside NNC's own chat format

## Commands

`/nnc` is the main command; `/nick` and `/nickname` are aliases (another plugin may own `/nick`, `/nnc` always works).

| Command | Description |
|---------|-------------|
| `/nick` | Open the nickname editor UI |
| `/nick <name>` | Set nickname via command |
| `/nick reset` | Reset nickname and message color |
| `/nick msgcolor <#hex>` | Set message text color |
| `/nick msgcolor gradient:#hex1:#hex2` | Set gradient message color |
| `/nick msgcolor reset` | Reset message color only |
| `/nick settings` | Open admin settings (requires `nickname.admin`) |

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `nickname.use` | true | Use /nick command |
| `nickname.format` | true | Colors and styles in the nickname |
| `nickname.msgcolor` | true | Set a chat message color (clearing it is always allowed) |
| `nickname.admin` | false | Access settings panel |

"Default true" means allowed unless the node is explicitly set to false (e.g. LuckPerms: `/lp group default permission set nickname.msgcolor false`). With LuckPerms, nodes that are not set at all use these defaults.

## Which chat plugin works with LuckPerms + NNC?

Every integration is optional: NNC works alone and with any combination of the plugins below. Only **one** plugin formats chat; NNC picks the route automatically and logs it at startup (`Integrations: ... Chat: ...`).

| Installed (besides NNC) | Who formats chat | Nickname in chat | What to do |
|-------------------------|------------------|------------------|------------|
| nothing / LuckPerms | NNC (`ChatFormat`, LP prefix/suffix) | ✅ colors + message color | Nothing. Old LuckPerms 5.5.28 betas had their own chat formatter: set `chat-formatter.enabled: false` in LP. |
| mini-chat-formatter (+ LP, + PAPI) | MCF | ✅ `<username>` shows the nickname, `<message>` gets the message color (MCF 0.1.x) | Nothing. Other MCF versions: use `%nnc_nickname_mini%` (needs PlaceholderAPI). |
| EssentialsPlus (+ LP, + MCF, + PAPI) | EP (when EP `chat.enabled`) | ✅ synced into EP's `{player}` + message color | Nothing. EP only accepts nicknames with A-Z, 0-9, `_`; others are refused (the player is told), use `%nnc_nickname_mini%` in EP's format for those (needs PlaceholderAPI). |
| EliteEssentials (+ LP, + PAPI) | EE (when its `chatFormat` is enabled) | ✅ synced into EE's `{player}` | Nothing for nicknames. **Message color is not automatic**: install PlaceholderAPI and put `%nnc_msgcolor_legacy%` right before `{message}` in EE's chat formats. |
| HyperPerms | HyperPerms | ✅ with `%nnc_nickname%` | Replace `%player%` with `%nnc_nickname%` in HyperPerms' chat format (registered by NNC, no PlaceholderAPI needed). **Note:** HyperPerms 2.10.0 is not yet updated for Hytale 0.6.8 (it fails with `NoSuchMethodError ServerPlayerListPlayer.<init>` on its own). |
| KyuubiSoft titles | NNC | ✅ | Install the Kyuubi Chat Title Bridge (CurseForge 1581560) + PlaceholderAPI, add e.g. `%ks_title_raw% ` to NNC's `ChatFormat`; set Kyuubi `displayMode` to `nametag` or `none`. |
| MysticNameTags | NNC | ✅ | Tags in chat: `%mystictags_tag%` in `ChatFormat`. Mystic-owned nameplates: NNC `ShowOnNameplate` = false, `%nnc_nameplate%` in Mystic's nameplate format. |

If both EssentialsPlus and EliteEssentials format chat, they conflict with each other (not related to NNC). If an unknown plugin formats chat, NNC leaves it alone and names it in the log at the first message. `/nnc` always works even if another plugin owns `/nick`.
### Placeholders (PlaceholderAPI, identifier `nnc`)

| Placeholder | Value |
|-------------|-------|
| `%nnc_nickname%` | Nickname as plain text (real name if none or if `ShowInChat` is off) |
| `%nnc_nameplate%` | Nickname as plain text, ignoring `ShowInChat` (for nameplate plugins) |
| `%nnc_nickname_mini%` | Nickname with colors as MiniMessage / EssentialsPlus tags |
| `%nnc_nickname_legacy%` | Nickname with colors as `&#RRGGBB` / `&l` codes |
| `%nnc_has_nickname%` | `true` / `false` |
| `%nnc_realname%` | Real username |
| `%nnc_msgcolor_open%` / `%nnc_msgcolor_close%` | MiniMessage tags to wrap the message in the player's message color |
| `%nnc_msgcolor_legacy%` | `&#RRGGBB` message color (first color of a gradient) |

## How nicknames are shown

- **Chat**: NNC's `ChatFormat` (`{prefix}`, `{username}`, `{suffix}`, `{message}` and any `%placeholder%`), with colors and gradients
- **Nameplate, tab list, map**: plain text (these can't render gradients). The real username is never changed; the tab list and map labels are replaced as they are sent to players, so they stay correct after joins, world changes, vanish/unhide and spectating.

## Installation

1. Download the latest JAR from [Releases](https://github.com/UberMorgott/NickNameChanger/releases)
2. Place in `Hytale/UserData/Mods/`
3. Restart the server
4. Use `/nick` to open the editor

## Admin Settings Panel

The settings panel (`/nick settings`) gives administrators a GUI to control where nicknames appear globally.

- Three checkboxes: **Show in Chat**, **Show on Nameplate**, **Show in Tab List**
- Changes apply **instantly** to all online players — no restart required
- Settings persist across server restarts

> **Note:** "Show on Map" is a **config-only** setting (`Display.ShowOnMap`) — it is not exposed in the admin GUI.

## Configuration

Config file is created automatically at `mods/NickNameChanger_NickNameChanger/config.json`

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

| Key | Meaning |
|-----|---------|
| `MinLength` / `MaxLength` | Visible characters (tags don't count) |
| `AllowedCharactersRegex` | Regex the whole visible nickname must match, e.g. `^[A-Za-z0-9_]+$` or `^[A-Za-zА-Яа-яЁё0-9_]+$`. Empty: letters, digits, space and `_ - . ! ?` (+ Cyrillic / Unicode letters per `AllowCyrillic` / `AllowUnicode`). `< > & § % { } \` are never allowed. |
| `BannedWords` | Case-insensitive; a nickname containing any of them is rejected |
| `UniqueNicknames` | No two players with the same nickname (case-insensitive, colors ignored) |
| `BlockRealUsernames` | A nickname can't be the real name of another player who has joined the server |

> **Important:** Config keys use **PascalCase** (e.g. `ShowInChat`). Configs from 0.0.16 and older (camelCase keys) are converted automatically on startup; the original is kept as `config.json.pre-0.0.18.bak`.
## Developer API

Other plugins can read nickname data and display settings via the `NicknameAPI` class.

### Setup

Add NickNameChanger as an optional dependency in your `manifest.json`:

```json
{
  "OptionalDependencies": {
    "NickNameChanger:NickNameChanger": "*"
  }
}
```

### Check if NickNameChanger is loaded

```java
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.common.plugin.PluginIdentifier;

boolean hasNNC = PluginManager.get().getPlugin(
    new PluginIdentifier("NickNameChanger", "NickNameChanger")) != null;
```

### Using NicknameAPI

```java
import com.nickname.plugin.api.NicknameAPI;
import java.util.UUID;

// Get player UUID from any PlayerRef
UUID uuid = playerRef.getUuid();
```

### Methods

#### `getNickname(UUID uuid) → String | null`

Returns the raw nickname with markup tags, or `null` if no nickname is set.

```java
String nick = NicknameAPI.getNickname(uuid);
// Returns: "<gradient:#FF5555:#55FF55>Morgott" (with color tags)
// Returns: "Morgott" (plain nickname)
// Returns: null (no nickname set)
```

#### `getDisplayName(UUID uuid, String defaultName) → String`

Returns the nickname if set, otherwise returns `defaultName`. Never returns null.

```java
String name = NicknameAPI.getDisplayName(uuid, playerRef.getUsername());
// Returns: "Morgott" (nickname or fallback, always non-null)
```

#### `hasNickname(UUID uuid) → boolean`

```java
if (NicknameAPI.hasNickname(uuid)) {
    // Player has a custom nickname
}
```

#### `getOriginalUsername(UUID uuid) → String | null`

Returns the player's real username as last seen by NickNameChanger (recorded on every join). Returns `null` if the player never joined since 0.0.18 and never had a nickname.

```java
String realName = NicknameAPI.getOriginalUsername(uuid);
// Returns: "UberMorgott" (real username)
// Returns: null (unknown player)
```

#### Display Settings (global, read-only)

```java
NicknameAPI.isShowInChat()      // Are nicknames shown in chat? (default: true)
NicknameAPI.isShowOnNameplate()  // Are nicknames shown above heads? (default: true)
NicknameAPI.isShowInTabList()    // Are nicknames shown in player list? (default: true)
```

### Full Example: Custom Join Message

```java
public void onPlayerReady(PlayerReadyEvent event) {
    PlayerRef playerRef = /* get from event */;
    UUID uuid = playerRef.getUuid();

    String displayName = NicknameAPI.getDisplayName(uuid, playerRef.getUsername());
    String original = NicknameAPI.getOriginalUsername(uuid);

    if (original != null && !displayName.equals(original)) {
        broadcast(displayName + " (aka " + original + ") joined!");
    } else {
        broadcast(displayName + " joined!");
    }
}
```

### Notes

- All methods are **static** and **thread-safe**
- All methods return safe defaults if NickNameChanger is not loaded (`null` or `false`)
- `getNickname()` may contain markup tags (`<color:...>`, `<gradient:...>`, `<bold>`, etc.) — use `getDisplayName()` if you need plain text with fallback
- Add `NickNameChanger:NickNameChanger` to your `OptionalDependencies` to ensure NNC loads before your plugin

## Links

- [CurseForge](https://www.curseforge.com/hytale/mods/nick-name-changer)
- [Issues](https://github.com/UberMorgott/NickNameChanger/issues)
