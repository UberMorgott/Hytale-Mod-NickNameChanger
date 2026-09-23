package com.nickname.plugin.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.util.PlayerRefUtil;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.ui.NicknameEditorPage;
import com.nickname.plugin.ui.NicknameSettingsPage;

import javax.annotation.Nonnull;
import java.util.UUID;

public class NickCommand extends AbstractPlayerCommand {

    public static final String PERM_USE = "nickname.use";
    public static final String PERM_FORMAT = "nickname.format";
    public static final String PERM_ADMIN = "nickname.admin";

    private final NicknameStorage storage;
    private final PluginConfig config;

    public NickCommand(NicknameStorage storage, PluginConfig config) {
        // "nnc" is unique to this plugin; "nick"/"nickname" still work unless another plugin
        // (EliteEssentials, EssentialsPlus, ...) registers a command with that name.
        super("nnc", "Set your display nickname");
        this.storage = storage;
        this.config = config;
        setAllowsExtraArguments(true);
        addAliases("nick", "nickname");
    }

    @Override
    public boolean hasPermission(@Nonnull CommandSender sender) {
        // Default: allowed. Deny only with explicit "-nickname.use"
        return sender.hasPermission(PERM_USE, true);
    }

    /** Runs on the player's world thread; AbstractPlayerCommand rejects non-player senders. */
    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerUuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        String arg = "";
        String fullInput = context.getInputString();
        if (fullInput != null) {
            String[] parts = fullInput.trim().split("\\s+", 2);
            if (parts.length > 1) {
                arg = parts[1].trim();
            }
        }

        if (arg.isEmpty()) {
            openNicknameEditor(ref, store, playerRef);
            return;
        }

        if (arg.equalsIgnoreCase("settings")) {
            if (!PermissionsModule.get().hasPermission(playerUuid, PERM_ADMIN, false)) {
                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_SETTINGS_PERM)).color("#FF5555"));
                return;
            }
            openNicknameSettings(ref, store, playerRef);
            return;
        }

        if (arg.equalsIgnoreCase("reset") || arg.equalsIgnoreCase("clear") ||
            arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("remove")) {
            resetNickname(ref, store, playerRef, playerUuid, username);
            return;
        }

        if (arg.split("\\s+", 2)[0].equalsIgnoreCase("msgcolor")) {
            handleMsgColor(playerRef, playerUuid, arg);
            return;
        }

        setNickname(ref, store, playerRef, playerUuid, username, arg);
    }

    private void openNicknameEditor(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.getPageManager().openCustomPage(ref, store, new NicknameEditorPage(storage, config, playerRef));
    }

    private void openNicknameSettings(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        player.getPageManager().openCustomPage(ref, store, new NicknameSettingsPage(storage, config, playerRef));
    }

    private void resetNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef, @Nonnull UUID uuid, @Nonnull String username) {
        if (storage.hasNickname(uuid)) {
            // Get original username before clearing
            String originalUsername = storage.getOriginalUsername(uuid);
            if (originalUsername == null) {
                originalUsername = username;
            }

            storage.removeNickname(uuid);
            storage.removeOriginalUsername(uuid);
            storage.removeMessageColor(uuid);

            // Restore PlayerRef.username to original (only if map nicknames enabled)
            if (config.display.showOnMap) {
                PlayerRefUtil.setUsername(playerRef, originalUsername);
            }

            // Remove nickname from LuckPerms if available
            if (LuckPermsHook.isAvailable()) {
                LuckPermsHook.removeDisplayName(uuid);
            }

            // Reset nameplate to original username
            if (config.display.showOnNameplate) {
                resetNameplate(ref, store, originalUsername);
            }

            // Reset player list to original username
            if (config.display.showInTabList) {
                updatePlayerList(playerRef, originalUsername);
            }

            playerRef.sendMessage(Message.join(
                Message.raw(Messages.get(playerRef, Messages.RESET_SUCCESS) + " ").color("#55FF55"),
                Message.raw(originalUsername).color("#FFFFFF")
            ));
        } else {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.RESET_NO_NICKNAME)).color("#FFFF55"));
        }
    }

    private void setNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef, @Nonnull UUID uuid, @Nonnull String username, @Nonnull String nickname) {
        // Check format permission if nickname contains markup (default: allowed)
        if (MessageUtil.hasMarkup(nickname) && !PermissionsModule.get().hasPermission(uuid, PERM_FORMAT, true)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_FORMAT_PERM)).color("#FF5555"));
            return;
        }

        // Check length without color tags
        String plainNickname = MessageUtil.stripTags(nickname);
        int minLen = config.nicknames.minLength;
        int maxLen = config.nicknames.maxLength;
        if (plainNickname.length() < minLen) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_MIN_LENGTH, "min", minLen)).color("#FF5555"));
            return;
        }
        if (plainNickname.length() > maxLen) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_MAX_LENGTH, "max", maxLen)).color("#FF5555"));
            return;
        }

        String filtered = filterNickname(nickname);
        if (filtered.isEmpty()) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_INVALID)).color("#FF5555"));
            return;
        }

        // Check banned words
        String plainFiltered = MessageUtil.stripTags(filtered).toLowerCase();
        for (String banned : config.nicknames.bannedWords) {
            if (plainFiltered.contains(banned.toLowerCase())) {
                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_BANNED_WORD)).color("#FF5555"));
                return;
            }
        }

        // Check uniqueness
        if (config.nicknames.uniqueNicknames && storage.isNicknameTaken(plainFiltered, uuid)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NICKNAME_TAKEN)).color("#FF5555"));
            return;
        }

        // Store original username for reset
        storage.setNickname(uuid, filtered);
        storage.setOriginalUsername(uuid, username);

        String plainName = MessageUtil.stripTags(filtered);

        // Update PlayerRef.username for map markers (breaks other plugins' player lookups)
        if (config.display.showOnMap) {
            PlayerRefUtil.setUsername(playerRef, plainName);
        }

        // Sync nickname to LuckPerms if available (for chat formatting compatibility)
        if (LuckPermsHook.isAvailable()) {
            LuckPermsHook.setDisplayName(uuid, filtered);
        }

        // Update nameplate (above head)
        if (storage.isShowOnNameplate()) {
            updateNameplate(ref, store, filtered);
        }

        // Update player list (map, tab)
        if (storage.isShowInTabList()) {
            updatePlayerList(playerRef, filtered);
        }

        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.SET_SUCCESS) + " ").color("#55FF55"),
            MessageUtil.parse(filtered)
        ));
    }

    private void handleMsgColor(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid, @Nonnull String arg) {
        // Check format permission
        if (!PermissionsModule.get().hasPermission(uuid, PERM_FORMAT, true)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_FORMAT_PERM)).color("#FF5555"));
            return;
        }

        // Parse: "msgcolor #FF5555" or "msgcolor gradient:#FF5555:#5555FF" or "msgcolor reset"
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
            return;
        }

        String value = parts[1].trim();

        if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("clear")) {
            storage.removeMessageColor(uuid);
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_RESET)).color("#55FF55"));
            return;
        }

        // Validate: #RRGGBB or gradient:#HEX1:#HEX2
        if (value.matches("^#[0-9A-Fa-f]{6}$")) {
            // Solid color
            storage.setMessageColor(uuid, value.toUpperCase());
            playerRef.sendMessage(Message.join(
                Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_SET) + " ").color("#55FF55"),
                Message.raw(value).color(value)
            ));
        } else if (value.toLowerCase().startsWith("gradient:")) {
            // gradient:#HEX1:#HEX2
            String[] gradParts = value.split(":");
            if (gradParts.length == 3 && gradParts[1].matches("^#[0-9A-Fa-f]{6}$") && gradParts[2].matches("^#[0-9A-Fa-f]{6}$")) {
                String stored = "gradient:" + gradParts[1].toUpperCase() + ":" + gradParts[2].toUpperCase();
                storage.setMessageColor(uuid, stored);
                playerRef.sendMessage(Message.join(
                    Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_SET) + " ").color("#55FF55"),
                    MessageUtil.parse("<gradient:" + gradParts[1] + ":" + gradParts[2] + ">Example text</gradient>")
                ));
            } else {
                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
            }
        } else {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
        }
    }

    private void updateNameplate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String displayName) {
        // Update Nameplate component (text above head) - same pattern as EntityNameplateCommand
        Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());

        // Strip color tags for nameplate text (it doesn't support rich text)
        String plainName = MessageUtil.stripTags(displayName);
        nameplate.setText(plainName);

        // Update DisplayNameComponent (plain text only — nametag can't render per-char gradient Messages)
        DisplayNameComponent displayNameComponent = new DisplayNameComponent(Message.raw(plainName));
        store.putComponent(ref, DisplayNameComponent.getComponentType(), displayNameComponent);
    }

    private void updatePlayerList(@Nonnull PlayerRef playerRef, @Nonnull String displayName) {
        UUID uuid = playerRef.getUuid();
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) return;

        // Strip color tags for player list (it may not support rich text)
        String plainName = MessageUtil.stripTags(displayName);

        // Remove player from list
        RemoveFromServerPlayerList removePacket = new RemoveFromServerPlayerList(new UUID[]{uuid});
        Universe.get().broadcastPacket(removePacket);

        // Add player back with new display name
        ServerPlayerListPlayer playerListEntry = PlayerRefUtil.tabListEntry(playerRef, plainName);
        AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[]{playerListEntry});
        Universe.get().broadcastPacket(addPacket);
    }

    private void resetNameplate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String originalUsername) {
        // Reset Nameplate to original username
        Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
        if (nameplate == null) return;
        nameplate.setText(originalUsername);

        // Set DisplayNameComponent to original name (don't remove — NameplateRefChangeSystem.onComponentRemoved clears nameplate to "")
        if (!ref.isValid()) return;
        DisplayNameComponent displayNameComponent = new DisplayNameComponent(Message.raw(originalUsername));
        store.putComponent(ref, DisplayNameComponent.getComponentType(), displayNameComponent);
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
        // Allow markup tags
        if (MessageUtil.hasMarkup(nickname)) {
            return filterNicknameWithTags(nickname);
        }

        StringBuilder filtered = new StringBuilder();
        for (char c : nickname.toCharArray()) {
            if (isAllowedChar(c)) {
                filtered.append(c);
            }
        }
        return filtered.toString().trim();
    }

    private static final java.util.Set<String> ALLOWED_TAGS = java.util.Set.of(
        "color", "gradient", "b", "bold", "i", "italic", "u", "underline"
    );

    @Nonnull
    private String filterNicknameWithTags(@Nonnull String nickname) {
        // Allow only whitelisted TinyMessage tags while filtering regular text
        StringBuilder filtered = new StringBuilder();
        int i = 0;

        while (i < nickname.length()) {
            char c = nickname.charAt(i);

            if (c == '<') {
                // Find closing >
                int end = nickname.indexOf('>', i);
                if (end == -1) {
                    // No closing > — treat as text
                    if (isAllowedChar(c)) filtered.append(c);
                    i++;
                    continue;
                }

                String tagContent = nickname.substring(i + 1, end);
                // Extract tag name: strip leading / and everything after :
                String tagName = tagContent.startsWith("/") ? tagContent.substring(1) : tagContent;
                int colonIdx = tagName.indexOf(':');
                if (colonIdx >= 0) tagName = tagName.substring(0, colonIdx);
                tagName = tagName.toLowerCase().trim();

                if (ALLOWED_TAGS.contains(tagName)) {
                    // Keep the whole tag as-is
                    filtered.append(nickname, i, end + 1);
                }
                // else: skip the tag entirely
                i = end + 1;
            } else {
                if (isAllowedChar(c)) {
                    filtered.append(c);
                }
                i++;
            }
        }
        return filtered.toString().trim();
    }
}
