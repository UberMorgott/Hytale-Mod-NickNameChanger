# NickNameChanger 0.0.18

NickNameChanger is a server mod for Hytale 0.6.x, built against Hytale Server 0.6.8. Its manifest requires server 0.6.8 or newer.

Set a nickname in chat, above your character, in the player list and on map player markers. Chat nicknames support colors, gradients, bold, italic and underline. Choose a separate color or gradient for your chat messages. Nameplates, the player list and map labels use plain text.

Your real username stays unchanged, so other plugins can still find you by your account name.

Report bugs on [GitHub Issues](https://github.com/UberMorgott/Hytale-Mod-NickNameChanger/issues). I don't read CurseForge comments.

The name at the top of the inventory or character screen comes from the game client and your account. No server mod can change it.

## Install in your own world

1. Download the NickNameChanger 0.0.18 `.jar` from [CurseForge](https://www.curseforge.com/hytale/mods/nick-name-changer).
2. Press **Win+R**.
3. Type `%AppData%\Hytale\UserData\Mods` and press Enter. If it is missing, open `%AppData%\Hytale\UserData` and create a folder named `Mods`.
4. Put the downloaded jar in `Mods`. Do not unpack it.
5. Start Hytale.
6. Open your world settings, go to **Mods**, then **Global Mods**, and enable **NickNameChanger**.
7. Enter the world and run `/nnc` in chat.

These are the default Windows paths. If you chose a custom Hytale data location, use its `UserData\Mods` folder instead.

For a dedicated server, put the jar in the server's `mods` folder and restart the server. No other mod is required for NNC's basic features.

## Check that it loaded

Open `%AppData%\Hytale\UserData\Logs` after starting your world. Open the newest log and search for the exact prefix `Integrations:`. This line lists detected optional plugins and the selected chat route. Timestamps and logger names may appear before it. On a dedicated server, check its console or server logs.

## Upgrade from 0.0.17 or older

Stop the world or server. Replace the old NickNameChanger jar with 0.0.18 and delete the old jar so only one version remains. Keep the mod's data folder.

Configuration migration runs automatically at startup. When old camelCase keys need conversion, the original is saved beside the config as `config.json.pre-0.0.18.bak`. Current keys use PascalCase. If both spellings exist, the PascalCase value wins; missing nested values are carried over.

Unreadable nickname data is not overwritten. A failed migration stops loading instead of replacing settings with defaults. Failed saves are reported. The update cannot recover data already deleted or overwritten; use an existing backup for that.

## Commands

`/nnc` is the main command. `/nick` and `/nickname` are aliases. Use `/nnc` if another plugin owns either alias.

| Command | Action |
| --- | --- |
| `/nnc` | Open the nickname editor |
| `/nnc <name>` | Set a nickname |
| `/nnc reset` | Reset your nickname and message color |
| `/nnc msgcolor #FF5555` | Set a message color |
| `/nnc msgcolor gradient:#FF5555:#5555FF` | Set a message gradient |
| `/nnc msgcolor reset` | Reset only your message color |
| `/nnc settings` | Open global display settings |

The editor has 12 color choices including reset, 8 gradient presets, custom colors and text styles. Message colors have their own tab.

Use the editor for colors and gradients, or enter supported markup, for example `/nnc <gradient:#FF5555:#5555FF>Steve</gradient>`. Formatting requires `nickname.format`. Commands and the editor use the same nickname rules. Supported tags are `color`, `gradient`, `b`/`bold`, `i`/`italic` and `u`/`underline`.

![Nickname editor](img/editor_gradient.png)

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `nickname.use` | Allowed | Nickname command and editor |
| `nickname.format` | Allowed | Nickname colors and formatting |
| `nickname.msgcolor` | Allowed | Message colors in the command and editor |
| `nickname.admin` | Denied | Global display settings |

With LuckPerms, unset nodes use these defaults. An explicit denial overrides the default. Message color and nickname formatting are separate permissions.

```text
/lp user Steve permission set nickname.admin true
/lp group Admin permission set nickname.admin true
/lp group default permission set nickname.format false
/lp group default permission set nickname.msgcolor false
/lp user Steve permission set nickname.use false
```

Without LuckPerms, use Hytale's native permissions. The `OP` group's `*` includes `nickname.admin`. A native denial is `-nickname.format` or `-nickname.msgcolor`. These are sample group entries in `permissions.json`; merge them into the existing file without removing player assignments:

```json
{
  "groups": {
    "OP": {"permissions": ["*"]},
    "Admin": {"permissions": ["nickname.admin"]},
    "Default": {"permissions": ["-nickname.msgcolor"]}
  }
}
```

## Optional mods and chat setup

Choose one plugin to format chat. NNC uses its own format when no other plugin does. `ChatFormat` controls NNC's own output.

| Setup | What it enables and what to configure |
| --- | --- |
| NNC alone | Nicknames and message colors in NNC's `ChatFormat`. No dependency needed. |
| LuckPerms | Permissions and prefix/suffix in NNC chat. For old LuckPerms 5.5.28 betas with their own formatter, set `chat-formatter.enabled` to `false` in LuckPerms. |
| mini-chat-formatter 0.1.x, optionally with LuckPerms or PlaceholderAPI | `<username>` shows the nickname and `<message>` gets the message color automatically. No PlaceholderAPI needed for this adapter. For other versions, use `%nnc_nickname_mini%` instead of `<username>` with HelpChat PlaceholderAPI. Check `Integrations:` for the selected route. |
| EssentialsPlus, optionally with LuckPerms or PlaceholderAPI | With EP's `chat.enabled` on, NNC syncs the nickname to EP's `{player}` and applies message colors while EP alone owns chat. Native nickname sync accepts only A-Z, a-z, 0-9 and `_`. Rejections are reported. For other characters, use `%nnc_nickname_mini%` in EP's chat format with HelpChat PlaceholderAPI. |
| EliteEssentials, optionally with LuckPerms or PlaceholderAPI | With EE's `chatFormat.enabled` on, NNC syncs the nickname to EE's `{player}`. Message color is not automatic: with HelpChat PlaceholderAPI, put `%nnc_msgcolor_legacy%` immediately before `{message}` in EE's chat formats. Through this legacy token, a message gradient becomes its first color. |
| HelpChat PlaceholderAPI | Exposes NNC's placeholders and lets NNC's `ChatFormat` resolve external `%placeholders%`. Optional; check that its version supports your server. |
| HyperPerms | NNC registers `%nnc_nickname%` directly with HyperPerms. Replace `%player%` with it in HyperPerms' chat format. No PlaceholderAPI needed for this token. HyperPerms 2.10.0 has not been updated for server 0.6.8 and fails there with `NoSuchMethodError` for `ServerPlayerListPlayer.<init>`. This adapter does not fix that incompatibility. |
| KyuubiSoft titles | Requires Kyuubi Chat Title Bridge and HelpChat PlaceholderAPI. Put `%ks_title_raw% ` in NNC's `ChatFormat`, for example before `{prefix}`. Set Kyuubi's `displayMode` to `nametag` or `none` so it does not also format chat. `{title}` is not an NNC token. |
| MysticNameTags | With its PlaceholderAPI expansion, put `%mystictags_tag%` in NNC's `ChatFormat`. If MysticNameTags controls nameplates, set NNC's `Display.ShowOnNameplate` to `false` and use `%nnc_nameplate%` in MysticNameTags' format. |

If EssentialsPlus and EliteEssentials both format chat, messages can be sent twice. Disable chat formatting in one of them. NNC logs a warning. Do not enable several chat formatters just because they appear in this table.

## PlaceholderAPI placeholders

The expansion identifier is `nnc`. Put tokens in a plugin's format where it supports HelpChat PlaceholderAPI and the corresponding color syntax.

| Placeholder | Value |
| --- | --- |
| `%nnc_nickname%` | Plain chat nickname, or real name |
| `%nnc_nickname_mini%` | Chat nickname with MiniMessage / EssentialsPlus color tags |
| `%nnc_nickname_legacy%` | Chat nickname with `&#RRGGBB` and legacy style codes |
| `%nnc_nameplate%` | Plain nickname, independent of `ShowInChat` |
| `%nnc_has_nickname%` | `true` or `false` |
| `%nnc_realname%` | Real username |
| `%nnc_msgcolor_open%` | Opening MiniMessage message-color tags |
| `%nnc_msgcolor_close%` | Closing MiniMessage message-color tags |
| `%nnc_msgcolor_legacy%` | Message color as `&#RRGGBB`; first color for a gradient |

The three chat nickname placeholders return the real name when `Display.ShowInChat` is off. Message-color tokens are empty without a saved color or permission. In a compatible MiniMessage format, put `%nnc_msgcolor_open%` before the message token and `%nnc_msgcolor_close%` after it. Keep that formatter's own message token.

## Display settings and configuration

`/nnc settings` changes chat, nameplate and player-list display for all players. Saving applies the settings to online players and keeps them across restarts. Hiding nicknames does not delete them. Map display is separate: edit `Display.ShowOnMap`. It is off by default.

![Display settings](img/settings_panel.png)

The server creates `mods/NickNameChanger_NickNameChanger/config.json` in its data directory. This is the running server's data folder, including the local server used by your world.

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

`ChatFormat` accepts `{prefix}`, `{username}`, `{suffix}` and `{message}`. Prefix and suffix come from LuckPerms when its integration and the corresponding switches are enabled. External `%placeholders%` require HelpChat PlaceholderAPI and their provider.

Nickname rules apply to visible text after supported formatting is parsed:

- `MinLength` and `MaxLength` exclude formatting tags.
- `AllowedCharactersRegex` must match the whole visible nickname. Examples: `^[A-Za-z0-9_]+$` or `^[A-Za-zА-Яа-яЁё0-9_]+$`.
- An empty regex uses the built-in letters/digits rules plus spaces and `_ - . ! ?`. `AllowCyrillic` and `AllowUnicode` extend the allowed letters and digits.
- Literal `< > & § % { } \` are forbidden in visible names. Supported color and gradient tags still work; they are parsed before these checks.
- `BannedWords` rejects any nickname containing a listed word, ignoring case.
- `UniqueNicknames` compares names without colors or case differences.
- `BlockRealUsernames` prevents using another known player's real name, including offline players. It knows names recorded by this server, not every Hytale account.
- Invalid rules stop nickname changes with an error. Invalid names are rejected with a reason, not silently shortened or rewritten.

## Not working?

1. Check that NNC is enabled for this world and only one jar is installed.
2. Try `/nnc`, especially if another plugin has a `/nick` command.
3. Check the newest log for `Integrations:` and earlier loading errors.
4. Check permissions and `Display` settings. Map display is off by default.
5. Use one chat formatter. Changing NNC's `ChatFormat` does not change EP, EE or MCF's format.
6. Check the limits below. If it still fails, include server and plugin versions, relevant config and log errors in [GitHub Issues](https://github.com/UberMorgott/Hytale-Mod-NickNameChanger/issues). I don't read CurseForge comments.

## Known limits

- The name at the top of the inventory or character screen is your account name from the game client. No server mod can change it.
- Nameplates, the player list and map labels show plain names, without chat gradients.
- EssentialsPlus's native nickname sync only accepts A-Z, a-z, 0-9 and `_`. Use its PlaceholderAPI route for other characters.
- HyperPerms 2.10.0 is not compatible with server 0.6.8. NNC cannot repair its server API mismatch.
- A placeholder needs its provider and a formatter that understands its output. An adapter does not guarantee every third-party plugin version supports 0.6.8.

## Developer API

The read-only API is `com.nickname.plugin.api.NicknameAPI`. Add the jar as a compile-only dependency:

```kotlin
dependencies {
    compileOnly(fileTree("libs") { include("NickNameChanger-*.jar") })
}
```

Merge this into your plugin's manifest:

```json
{
  "OptionalDependencies": {
    "NickNameChanger:NickNameChanger": "*"
  }
}
```

Check for the plugin before loading your integration class:

```java
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.common.plugin.PluginIdentifier;

boolean hasNNC = PluginManager.get().getPlugin(
    new PluginIdentifier("NickNameChanger", "NickNameChanger")) != null;
```

Keep direct `NicknameAPI` references in a separate adapter loaded only when NNC is present. `OptionalDependencies` sets load order; it does not supply missing Java classes. Calling this class without the jar can cause a linkage error.

| Static method | Result |
| --- | --- |
| `getNickname(UUID uuid)` | Raw nickname with markup, or `null` |
| `getDisplayName(UUID uuid, String defaultName)` | Raw nickname with markup, or your non-null fallback |
| `hasNickname(UUID uuid)` | Whether a nickname is saved |
| `getOriginalUsername(UUID uuid)` | Last recorded real username, or `null` if unknown |
| `isShowInChat()` | Global chat display switch |
| `isShowOnNameplate()` | Global nameplate display switch |
| `isShowInTabList()` | Global player-list switch; does not report map display |

```java
import com.nickname.plugin.api.NicknameAPI;
import java.util.UUID;

UUID uuid = playerRef.getUuid();
String rawName = NicknameAPI.getDisplayName(uuid, playerRef.getUsername());
String original = NicknameAPI.getOriginalUsername(uuid);
boolean useNickname = NicknameAPI.isShowInChat() && NicknameAPI.hasNickname(uuid);
```

Neither name getter applies `ShowInChat` or strips markup. Your renderer must parse or remove it. For a custom join message, get the `PlayerRef` from the ready event's entity store. This handler uses NNC's `MessageUtil` helper:

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.api.NicknameAPI;
import com.nickname.plugin.util.MessageUtil;

public void onPlayerReady(PlayerReadyEvent event) {
    if (event.getReadyId() != 0) return;
    Ref<EntityStore> ref = event.getPlayerRef();
    if (ref == null || !ref.isValid()) return;
    Store<EntityStore> store = ref.getStore();
    PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
    if (player == null) return;
    String rawName = NicknameAPI.isShowInChat()
        ? NicknameAPI.getDisplayName(player.getUuid(), player.getUsername())
        : player.getUsername();
    Message message = Message.join(MessageUtil.parse(rawName), Message.raw(" joined!"));
    for (PlayerRef viewer : Universe.get().getPlayers()) {
        viewer.sendMessage(message);
    }
}
```

Register this handler through your plugin's event registry. Keep it in the guarded integration class. The ready event runs on the world thread; do not move entity-store access to an arbitrary executor.

The API has no setters. Reads use synchronized nickname storage and volatile display flags. Before initialization, name getters return `null`, `hasNickname` returns `false`, `getDisplayName` returns its fallback, and display-switch getters return `true`. These defaults apply only when the class is available; they do not make a missing jar safe to reference.

## Languages and links

English (`en-US`), Russian (`ru-RU`) and Brazilian Portuguese (`pt-BR`). Author: Morgott.

- [CurseForge](https://www.curseforge.com/hytale/mods/nick-name-changer)
- [Bug reports on GitHub Issues](https://github.com/UberMorgott/Hytale-Mod-NickNameChanger/issues)
- [Русская версия](README.ru.md)
