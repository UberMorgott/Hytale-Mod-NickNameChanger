package com.nickname.plugin.listeners;

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.nickname.plugin.chat.ChatFormatParser;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.storage.NicknameStorage;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ChatListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final ChatFormatParser formatParser;
    private final Set<String> reportedFormatters = ConcurrentHashMap.newKeySet();

    public ChatListener(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config) {
        this.storage = storage;
        this.config = config;
        this.formatParser = new ChatFormatParser(config.chatFormat);
    }

    /**
     * LAST priority: format the message only if no other plugin owns chat.
     * <p>
     * Chat plugins either cancel the event and send their own messages (EssentialsPlus,
     * EliteEssentials, Werchat) or install their own formatter (mini-chat-formatter, HyperPerms,
     * old LuckPerms, KyuubiSoft). Both are left untouched: those plugins show nicknames through
     * NNC's PlaceholderAPI placeholders instead. The player's username is never modified.
     */
    public void onPlayerChat(@Nonnull PlayerChatEvent event) {
        if (event.isCancelled()) return;

        PlayerChatEvent.Formatter current = event.getFormatter();
        if (current != PlayerChatEvent.DEFAULT_FORMATTER) {
            if (reportedFormatters.add(current.getClass().getName())) {
                LOGGER.at(Level.INFO).log("Chat is formatted by another plugin (%s); NickNameChanger leaves it alone. "
                    + "Show nicknames there with the %%nnc_nickname%% PlaceholderAPI placeholder.", current.getClass().getName());
            }
            return;
        }

        PlayerRef sender = event.getSender();
        UUID senderUuid = sender.getUuid();
        boolean hasNickname = storage.isShowInChat() && storage.hasNickname(senderUuid);

        boolean hasLuckPerms = LuckPermsHook.isAvailable();
        final String prefix = (hasLuckPerms && config.integrations.luckperms.showPrefix)
                ? LuckPermsHook.getPrefix(senderUuid) : null;
        final String suffix = (hasLuckPerms && config.integrations.luckperms.showSuffix)
                ? LuckPermsHook.getSuffix(senderUuid) : null;

        boolean hasLpData = (prefix != null && !prefix.isEmpty())
                || (suffix != null && !suffix.isEmpty());
        // A stored color is ignored once nickname.msgcolor is revoked
        final String msgColor = PermissionsModule.get().hasPermission(senderUuid, NickCommand.PERM_MSGCOLOR, true)
                ? storage.getMessageColor(senderUuid) : null;
        boolean hasMsgColor = msgColor != null;

        // Skip if nothing to contribute
        if (!hasNickname && !hasLpData && !hasMsgColor) {
            return;
        }

        String realName = sender.getUsername();
        final String safeName = hasNickname ? storage.getDisplayName(senderUuid, realName) : realName;

        event.setFormatter((playerRef, message) -> {
            Message result = Message.empty();

            for (ChatFormatParser.Token token : formatParser.getTokens()) {
                if (token.type == ChatFormatParser.TokenType.PLACEHOLDER) {
                    switch (token.value) {
                        case "prefix":
                            if (prefix != null && !prefix.isEmpty()) {
                                result = result.insert(MessageUtil.parse(prefix));
                            }
                            break;
                        case "suffix":
                            if (suffix != null && !suffix.isEmpty()) {
                                result = result.insert(MessageUtil.parse(suffix));
                            }
                            break;
                        case "username":
                            result = result.insert(buildUsername(senderUuid, safeName));
                            break;
                        case "message":
                            if (msgColor != null) {
                                result = result.insert(buildMessage(message, msgColor));
                            } else {
                                result = result.insert(Message.raw(message).color("#FFFFFF"));
                            }
                            break;
                    }
                } else {
                    String text = token.value;
                    if (MessageUtil.hasMarkup(text)) {
                        result = result.insert(MessageUtil.parse(text));
                    } else {
                        result = result.insert(Message.raw(text).color("#AAAAAA"));
                    }
                }
            }

            return result;
        });
    }

    private Message buildMessage(String message, String colorSpec) {
        if (colorSpec.startsWith("gradient:")) {
            String[] parts = colorSpec.split(":");
            if (parts.length == 3) {
                // Escape angle brackets to prevent tag injection (e.g. player typing "</gradient>")
                String safeMessage = message.replace("<", "").replace(">", "");
                return MessageUtil.parse("<gradient:" + parts[1] + ":" + parts[2] + ">" + safeMessage + "</gradient>");
            }
        }
        // Solid color — Message.raw() treats input as literal text, no injection risk
        return Message.raw(message).color(colorSpec);
    }

    private Message buildUsername(UUID uuid, String name) {
        if (MessageUtil.hasMarkup(name)) {
            return MessageUtil.parse(name);
        } else if (storage.hasNickname(uuid)) {
            return Message.raw(name).color("#FFFF55");
        } else {
            return Message.raw(name).color("#FFFFFF");
        }
    }
}
