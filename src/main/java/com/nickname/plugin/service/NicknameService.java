package com.nickname.plugin.service;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.display.NicknameDisplay;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.validation.NicknameValidator;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Set / reset logic shared by the /nick command and the editor UI, so both apply the same
 * rules and update chat, nameplate, tab list and map the same way.
 * All methods run on the player's world thread and report the result to the player.
 */
public final class NicknameService {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final NicknameDisplay display;
    private final NicknameValidator validator;

    public NicknameService(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config, @Nonnull NicknameDisplay display) {
        this.storage = storage;
        this.config = config;
        this.display = display;
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
        }

        NicknameStorage.ClaimResult claim = storage.claimNickname(uuid, result.nickname(), result.plain(),
            config.nicknames.uniqueNicknames, config.nicknames.blockRealUsernames);
        String claimError = switch (claim) {
            case OK -> null;
            case TAKEN_BY_NICKNAME -> Messages.ERROR_NICKNAME_TAKEN;
            case TAKEN_BY_USERNAME -> Messages.ERROR_REAL_USERNAME;
            case NOT_SAVED -> Messages.ERROR_NOT_SAVED;
        };
        if (claimError != null) {
            error(playerRef, Messages.get(playerRef, claimError));
            return false;
        }

        if (LuckPermsHook.isAvailable()) {
            LuckPermsHook.setDisplayName(uuid, result.nickname());
        }
        display.refresh(ref, store, playerRef);

        playerRef.sendMessage(Message.join(
            Message.raw(Messages.get(playerRef, Messages.SET_SUCCESS) + " ").color("#55FF55"),
            MessageUtil.parse(result.nickname())
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
}
