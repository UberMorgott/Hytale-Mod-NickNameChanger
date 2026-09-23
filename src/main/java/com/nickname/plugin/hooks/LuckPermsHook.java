package com.nickname.plugin.hooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

/**
 * Safe facade for optional LuckPerms integration (prefix / suffix in NNC's chat format).
 * All direct LuckPerms API references are in {@link com.nickname.plugin.compat.LuckPermsCompat},
 * which the JVM only loads when LuckPerms is actually present.
 */
public class LuckPermsHook {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private static volatile boolean available = false;

    /** Called once from plugin start(); LuckPerms is an optional dependency, so it is loaded before us. */
    public static void init(boolean enabled) {
        if (!enabled) {
            LOGGER.at(Level.INFO).log("LuckPerms integration disabled in config.");
            return;
        }
        if (!PluginDetector.isLoaded(PluginDetector.LUCKPERMS)) {
            LOGGER.at(Level.INFO).log("LuckPerms not found, running without it.");
            return;
        }
        try {
            com.nickname.plugin.compat.LuckPermsCompat.init();
            available = true;
            LOGGER.at(Level.INFO).log("LuckPerms integration enabled!");
        } catch (IllegalStateException | NoClassDefFoundError e) {
            LOGGER.at(Level.WARNING).withCause(e).log("LuckPerms API not available, running without it.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static void disableHook(Throwable e) {
        if (available) {
            available = false;
            LOGGER.at(Level.WARNING).log("LuckPerms became unavailable, disabling integration: %s", e.getMessage());
        }
    }

    @Nullable
    public static String getPrefix(@Nonnull UUID uuid) {
        if (!available) return null;
        try {
            return com.nickname.plugin.compat.LuckPermsCompat.getPrefix(uuid);
        } catch (NoClassDefFoundError | Exception e) {
            disableHook(e);
            return null;
        }
    }

    @Nullable
    public static String getSuffix(@Nonnull UUID uuid) {
        if (!available) return null;
        try {
            return com.nickname.plugin.compat.LuckPermsCompat.getSuffix(uuid);
        } catch (NoClassDefFoundError | Exception e) {
            disableHook(e);
            return null;
        }
    }
}
