package com.nickname.plugin.listeners;

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.nickname.plugin.chat.ChatFormatParser;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.compat.EssentialsPlusCompat;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.hooks.PlaceholderApiHook;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.storage.NicknameStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ChatListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final ChatFormatParser formatParser;
    private final Set<String> reportedFormatters = ConcurrentHashMap.newKeySet();
    @Nullable
    private volatile EssentialsPlusCompat essentialsPlus;
    /** Chat text before EssentialsPlus color tags were added, to undo it if EP didn't take the message. */
    private final Map<PlayerChatEvent, String> uncoloredContent = Collections.synchronizedMap(new WeakHashMap<>());

    public ChatListener(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config) {
        this.storage = storage;
        this.config = config;
        this.formatParser = new ChatFormatParser(config.chatFormat);
    }

    /** Enables {@link #onPlayerChatEarly} message colors for EssentialsPlus chat. */
    public void setEssentialsPlus(@Nonnull EssentialsPlusCompat essentialsPlus) {
        this.essentialsPlus = essentialsPlus;
    }

    /**
     * FIRST priority, only with EssentialsPlus formatting chat: EP renders color tags in the chat
     * text, so the player's message color is added around it (EP parses players' text as markup anyway).
     */
    public void onPlayerChatEarly(@Nonnull PlayerChatEvent event) {
        EssentialsPlusCompat essentialsPlus = this.essentialsPlus;
        if (event.isCancelled() || essentialsPlus == null || !essentialsPlus.isChatEnabled()) return;
        String color = messageColor(event.getSender().getUuid());
        if (color != null) {
            uncoloredContent.put(event, event.getContent());
            event.setContent(EssentialsPlusCompat.colorMessage(event.getContent(), color));
        }
    }

    /** The stored message color, ignored once nickname.msgcolor is revoked. */
    @Nullable
    private String messageColor(@Nonnull UUID uuid) {
        return PermissionsModule.get().hasPermission(uuid, NickCommand.PERM_MSGCOLOR, true)
            ? storage.getMessageColor(uuid) : null;
    }
    /**
     * LAST priority: format the message only if no other plugin owns chat.
     * <p>
     * Chat plugins either cancel the event and send their own messages (EssentialsPlus,
     * EliteEssentials, Werchat) or install their own formatter (mini-chat-formatter, HyperPerms,
     * old LuckPerms, KyuubiSoft). Both are left untouched: those plugins show nicknames through
     * NNC's adapters or placeholders instead. The player's username is never modified.
     */
    public void onPlayerChat(@Nonnull PlayerChatEvent event) {
        String uncolored = uncoloredContent.remove(event);
        if (event.isCancelled()) return;
        if (uncolored != null) {
            event.setContent(uncolored); // EssentialsPlus did not send it (e.g. muted); show the plain text
        }
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
        final String msgColor = messageColor(senderUuid);
        boolean hasMsgColor = msgColor != null;

        // External placeholders (e.g. %ks_title_raw%, %mystictags_tag%) can be in the format itself
        boolean hasExternalPlaceholders = PlaceholderApiHook.isAvailable() && config.chatFormat.indexOf('%') >= 0;

        // Skip if nothing to contribute
        if (!hasNickname && !hasLpData && !hasMsgColor && !hasExternalPlaceholders) {
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
                    // Only the configured format goes through PlaceholderAPI, never the player's text
                    String text = PlaceholderApiHook.apply(playerRef, token.value);
                    result = result.insert(MessageUtil.parse(text, "#AAAAAA"));
                }
            }

            return result;
        });
    }

    /** The player's text is never parsed as markup; a gradient colors it character by character. */
    private Message buildMessage(String message, String colorSpec) {
        if (colorSpec.startsWith("gradient:")) {
            String[] parts = colorSpec.split(":");
            if (parts.length == 3) {
                return MessageUtil.gradient(message, parts[1], parts[2]);
            }
        }
        return Message.raw(message).color(colorSpec);
    }

    private Message buildUsername(UUID uuid, String name) {
        // Nicknames without their own color are yellow, real names white
        return MessageUtil.parse(name, storage.hasNickname(uuid) ? "#FFFF55" : "#FFFFFF");
    }
}