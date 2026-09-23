package com.nickname.plugin.service;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.compat.EssentialsPlusCompat;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.display.NicknameDisplay;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.validation.NicknameValidator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Set / reset logic shared by the /nick command and the editor UI, so both apply the same
 * rules and update chat, nameplate, tab list and map the same way.
 * All methods run on the player's world thread and report the result to the player.
 */
public final class NicknameService {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final NicknameDisplay display;
    private final NicknameValidator validator;
    @Nullable
    private final EssentialsPlusCompat essentialsPlus;

    public NicknameService(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config, @Nonnull NicknameDisplay display,
                           @Nullable EssentialsPlusCompat essentialsPlus) {
        this.storage = storage;
        this.config = config;
        this.display = display;
        this.essentialsPlus = essentialsPlus;
        this.validator = new NicknameValidator(config.nicknames, message -> LOGGER.at(Level.SEVERE).log(message));
    }

    /** Validates and applies a nickname (may contain formatting tags). Returns {@code true} on success. */
    public boolean setNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull PlayerRef playerRef, @Nonnull String input) {
        UUID uuid = playerRef.getUuid();
        NicknameValidator.Result result = validator.validate(input);
        if (!result.isValid()) {
            error(playerRef, Messages.get(playerRef, result.errorKey(), result.args()));
            return false;
        }        // Checked here, not in the UI: the editor's text field can carry tags too
        if (MessageUtil.hasMarkup(result.nickname())
                && !PermissionsModule.get().hasPermission(uuid, NickCommand.PERM_FORMAT, true)) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_NO_FORMAT_PERM));
            return false;
        }

        String previous = storage.getNickname(uuid);
        NicknameStorage.ClaimResult claim = storage.claimNickname(uuid, result.nickname(), result.plain(),
            config.nicknames.uniqueNicknames, config.nicknames.blockRealUsernames);
        String claimError = switch (claim) {
            case OK -> null;
            case TAKEN_BY_NICKNAME -> Messages.ERROR_NICKNAME_TAKEN;
            case TAKEN_BY_USERNAME -> Messages.ERROR_REAL_USERNAME;
            case STORAGE_ERROR -> Messages.ERROR_NOT_SAVED;
        };
        if (claimError != null) {
            error(playerRef, Messages.get(playerRef, claimError));
            return false;
        }

        display.refresh(ref, store, playerRef);

        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.SET_SUCCESS) + " ").color("#55FF55"),
            MessageUtil.parse(result.nickname())
        ));
        syncEssentialsPlus(playerRef, previous);
        return true;
    }

    /** Removes the nickname and message color. */
    public void resetNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (!storage.hasNickname(uuid)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.RESET_NO_NICKNAME)).color("#FFFF55"));
            return;
        }

        String previous = storage.getNickname(uuid);
        if (!storage.removeNickname(uuid)) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_NOT_SAVED));
            return;
        }
        display.refresh(ref, store, playerRef);

        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.RESET_SUCCESS) + " ").color("#55FF55"),
            Message.raw(playerRef.getUsername()).color("#FFFFFF")
        ));
        syncEssentialsPlus(playerRef, previous);
        if (!storage.removeMessageColor(uuid)) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_NOT_SAVED));
        }
    }

    /**
     * Sets ({@code #RRGGBB} or {@code gradient:#RRGGBB:#RRGGBB}) or clears (empty, reset, off, clear)
     * the chat message color. Setting needs {@code nickname.msgcolor}; clearing is always allowed.
     */
    public void setMessageColor(@Nonnull PlayerRef playerRef, @Nonnull String value) {
        UUID uuid = playerRef.getUuid();
        if (value.isEmpty() || value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("clear")) {
            if (!storage.removeMessageColor(uuid)) {
                error(playerRef, Messages.get(playerRef, Messages.ERROR_NOT_SAVED));
                return;
            }
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_RESET)).color("#55FF55"));
            return;
        }
        if (!PermissionsModule.get().hasPermission(uuid, NickCommand.PERM_MSGCOLOR, true)) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_NO_MSGCOLOR_PERM));
            return;
        }
        String color = parseMessageColor(value);
        if (color == null) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
            return;
        }
        if (!storage.setMessageColor(uuid, color)) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_NOT_SAVED));
            return;
        }
        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_SET) + " ").color("#55FF55"),
            messagePreview(color)
        ));
    }

    /** Normalized {@code #RRGGBB} / {@code gradient:#RRGGBB:#RRGGBB}, or {@code null} if invalid. */
    @Nullable
    public static String parseMessageColor(@Nonnull String value) {
        if (HEX_COLOR.matcher(value).matches()) {
            return value.toUpperCase(Locale.ROOT);
        }
        String[] parts = value.split(":");
        if (parts.length == 3 && parts[0].equalsIgnoreCase("gradient")
                && HEX_COLOR.matcher(parts[1]).matches() && HEX_COLOR.matcher(parts[2]).matches()) {
            return "gradient:" + parts[1].toUpperCase(Locale.ROOT) + ":" + parts[2].toUpperCase(Locale.ROOT);
        }
        return null;
    }

    @Nonnull
    private static Message messagePreview(@Nonnull String color) {
        if (color.startsWith("gradient:")) {
            String[] parts = color.split(":");
            return MessageUtil.parse("<gradient:" + parts[1] + ":" + parts[2] + ">Example text</gradient>");
        }
        return Message.raw("Example text").color(color);
    }

    /** EssentialsPlus shows its own nickname in its chat; keep it equal to ours and say so if EP refuses. */
    private void syncEssentialsPlus(@Nonnull PlayerRef playerRef, @Nullable String previousNickname) {
        if (essentialsPlus == null) return;
        String refusal = essentialsPlus.sync(playerRef.getUuid(), previousNickname);
        if (refusal != null) {
            error(playerRef, Messages.get(playerRef, Messages.ERROR_ESSENTIALSPLUS, "reason", refusal));
        }
    }

    /** On join (EP has loaded the player by PlayerReady): make EP's nickname match. Refusals are logged. */
    public void syncOnJoin(@Nonnull PlayerRef playerRef) {
        if (essentialsPlus == null) return;
        String refusal = essentialsPlus.sync(playerRef.getUuid(), null);
        if (refusal != null) {
            LOGGER.at(Level.INFO).log("EssentialsPlus chat can't show the nickname of %s: %s", playerRef.getUsername(), refusal);
        }
    }

    /** After display settings changed: nameplates / tab list and EssentialsPlus nicknames of everyone online. */
    public void reapplyAll() {
        display.refreshAll();
        if (essentialsPlus == null) return;
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (storage.hasNickname(playerRef.getUuid())) {
                syncOnJoin(playerRef);
            }
        }
    }

    private static void error(@Nonnull PlayerRef playerRef, @Nonnull String text) {
        playerRef.sendMessage(Message.raw(text).color("#FF5555"));
    }
}
