package com.nickname.plugin.hooks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

/**
 * Safe facade for optional LuckPerms integration: permission checks and prefix / suffix in NNC's
 * chat format. All direct LuckPerms API references are in {@link com.nickname.plugin.compat.LuckPermsCompat},
 * which the JVM only loads when LuckPerms is actually present.
 */
public class LuckPermsHook {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    /** LuckPerms API usable (permissions are always checked through it when installed). */
    private static volatile boolean loaded = false;
    /** Prefix / suffix in chat (config Integrations.Luckperms.Enabled). */
    private static volatile boolean chatEnabled = false;

    /** Called once from plugin start(); LuckPerms is an optional dependency, so it is loaded before us. */
    public static void init(boolean enabled) {
        if (!PluginDetector.isLoaded(PluginDetector.LUCKPERMS)) {
            return;
        }
        try {
            com.nickname.plugin.compat.LuckPermsCompat.init();
            loaded = true;
            chatEnabled = enabled;
            if (!enabled) {
                LOGGER.at(Level.INFO).log("LuckPerms prefixes/suffixes disabled in config.");
            }
        } catch (IllegalStateException | NoClassDefFoundError e) {
            LOGGER.at(Level.WARNING).withCause(e).log("LuckPerms API not available, running without it.");
        }
    }

    /** True if LuckPerms is installed and its API usable. */
    public static boolean isLoaded() {
        return loaded;
    }

    /** True if LuckPerms prefixes / suffixes should be shown in NNC's chat format. */
    public static boolean isAvailable() {
        return loaded && chatEnabled;
    }

    private static void disableHook(Throwable e) {
        if (loaded) {
            loaded = false;
            LOGGER.at(Level.WARNING).log("LuckPerms became unavailable, disabling integration: %s", e.getMessage());
        }
    }

    /** LuckPerms' explicit value for the node, or {@code null} if not set / user not loaded / LP absent. */
    @Nullable
    public static Boolean checkPermission(@Nonnull UUID uuid, @Nonnull String node) {
        if (!loaded) return null;
        try {
            return com.nickname.plugin.compat.LuckPermsCompat.checkPermission(uuid, node);
        } catch (NoClassDefFoundError | Exception e) {
            disableHook(e);
            return null;
        }
    }

    @Nullable
    public static String getPrefix(@Nonnull UUID uuid) {
        if (!isAvailable()) return null;
        try {
            return com.nickname.plugin.compat.LuckPermsCompat.getPrefix(uuid);
        } catch (NoClassDefFoundError | Exception e) {
            disableHook(e);
            return null;
        }
    }

    @Nullable
    public static String getSuffix(@Nonnull UUID uuid) {
        if (!isAvailable()) return null;
        try {
            return com.nickname.plugin.compat.LuckPermsCompat.getSuffix(uuid);
        } catch (NoClassDefFoundError | Exception e) {
            disableHook(e);
            return null;
        }
    }
}
