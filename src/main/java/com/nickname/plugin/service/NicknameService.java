package com.nickname.plugin.service;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.display.NicknameDisplay;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * Set / reset logic shared by the /nick command and the editor UI, so both apply the same
 * rules and update chat, nameplate, tab list and map the same way.
 * All methods run on the player's world thread and report the result to the player.
 */
public final class NicknameService {

    private static final Set<String> ALLOWED_TAGS = Set.of(
        "color", "gradient", "b", "bold", "i", "italic", "u", "underline"
    );

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final NicknameDisplay display;

    public NicknameService(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config, @Nonnull NicknameDisplay display) {
        this.storage = storage;
        this.config = config;
        this.display = display;
    }

    /** Validates and applies a nickname (may contain formatting tags). Returns {@code true} on success. */
    public boolean setNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull PlayerRef playerRef, @Nonnull String nickname) {
        UUID uuid = playerRef.getUuid();

        // Check length without color tags
        String plainNickname = MessageUtil.stripTags(nickname);
        int minLen = config.nicknames.minLength;
        int maxLen = config.nicknames.maxLength;
        if (plainNickname.length() < minLen) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_MIN_LENGTH, "min", minLen));
            return false;
        }
        if (plainNickname.length() > maxLen) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_MAX_LENGTH, "max", maxLen));
            return false;
        }

        String filtered = filterNickname(nickname);
        if (filtered.isEmpty()) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_INVALID));
            return false;
        }

        String plainFiltered = MessageUtil.stripTags(filtered).toLowerCase();
        for (String banned : config.nicknames.bannedWords) {
            if (plainFiltered.contains(banned.toLowerCase())) {
                error(playerRef, Messages.get(playerRef, Messages.ERROR_BANNED_WORD));
                return false;
            }
        }

        if (config.nicknames.uniqueNicknames && storage.isNicknameTaken(plainFiltered, uuid)) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_NICKNAME_TAKEN));
            return false;
        }

        storage.setNickname(uuid, filtered);
        storage.setOriginalUsername(uuid, playerRef.getUsername());

        if (LuckPermsHook.isAvailable()) {
            LuckPermsHook.setDisplayName(uuid, filtered);
        }
        display.refresh(ref, store, playerRef);

        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.SET_SUCCESS) + " ").color("#55FF55"),
            MessageUtil.parse(filtered)
        ));
        return true;
    }

    /** Removes the nickname and message color. */
    public void resetNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (!storage.hasNickname(uuid)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.RESET_NO_NICKNAME)).color("#FFFF55"));
            return;
        }

        storage.removeNickname(uuid);
        storage.removeMessageColor(uuid);

        if (LuckPermsHook.isAvailable()) {
            LuckPermsHook.removeDisplayName(uuid);
        }
        display.refresh(ref, store, playerRef);

        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.RESET_SUCCESS) + " ").color("#55FF55"),
            Message.raw(playerRef.getUsername()).color("#FFFFFF")
        ));
    }

    private static void error(@Nonnull PlayerRef playerRef, @Nonnull String text) {
        playerRef.sendMessage(Message.raw(text).color("#FF5555"));
    }

    private boolean isAllowedChar(char c) {
        // Basic punctuation always allowed
        if (c == ' ' || c == '_' || c == '-' || c == '.' || c == '!' || c == '?') return true;
        // ASCII letters and digits always allowed
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
        // Cyrillic
        if (config.nicknames.allowCyrillic && Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) return true;
        // All other Unicode
        if (config.nicknames.allowUnicode && Character.isLetterOrDigit(c)) return true;
        return false;
    }

    @Nonnull
    private String filterNickname(@Nonnull String nickname) {
        // Keep only whitelisted formatting tags and allowed characters
        StringBuilder filtered = new StringBuilder();
        int i = 0;
        while (i < nickname.length()) {
            char c = nickname.charAt(i);
            int end = c == '<' ? nickname.indexOf('>', i) : -1;
            if (end == -1) {
                if (isAllowedChar(c)) filtered.append(c);
                i++;
                continue;
            }
            String tagName = nickname.substring(i + 1, end);
            if (tagName.startsWith("/")) tagName = tagName.substring(1);
            int colonIdx = tagName.indexOf(':');
            if (colonIdx >= 0) tagName = tagName.substring(0, colonIdx);
            if (ALLOWED_TAGS.contains(tagName.toLowerCase().trim())) {
                filtered.append(nickname, i, end + 1);
            }
            i = end + 1;
        }
        return filtered.toString().trim();
    }
}
