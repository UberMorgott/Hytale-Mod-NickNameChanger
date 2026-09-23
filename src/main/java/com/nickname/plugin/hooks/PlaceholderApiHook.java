package com.nickname.plugin.hooks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.nickname.plugin.compat.NicknamePlaceholders;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Safe facade for the optional HelpChat PlaceholderAPI integration: registers the
 * {@code %nnc_...%} placeholders and resolves external placeholders in NNC's chat format.
 * All PlaceholderAPI classes are referenced only from {@link com.nickname.plugin.compat.PlaceholderApiCompat}.
 */
public final class PlaceholderApiHook {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private static volatile boolean available = false;

    private PlaceholderApiHook() {}

    public static void init(@Nonnull NicknamePlaceholders placeholders, @Nonnull String version) {
        if (!PluginDetector.isLoaded(PluginDetector.PLACEHOLDER_API)) return;
        try {
            if (com.nickname.plugin.compat.PlaceholderApiCompat.register(placeholders, version)) {
                available = true;
                LOGGER.at(Level.INFO).log("PlaceholderAPI found: registered %%nnc_nickname%% and related placeholders.");
            } else {
                LOGGER.at(Level.WARNING).log("PlaceholderAPI refused to register the 'nnc' placeholders.");
            }
        } catch (LinkageError | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("PlaceholderAPI integration failed; continuing without it.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** Replaces {@code %...%} placeholders of any PlaceholderAPI expansion; returns the text unchanged if unavailable. */
    @Nonnull
    public static String apply(@Nonnull PlayerRef player, @Nonnull String text) {
        if (!available || text.indexOf('%') < 0) return text;
        try {
            return com.nickname.plugin.compat.PlaceholderApiCompat.setPlaceholders(player, text);
        } catch (LinkageError | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("PlaceholderAPI failed to resolve '%s'", text);
            return text;
        }
    }

    public static void shutdown() {
        if (!available) return;
        available = false;
        try {
            com.nickname.plugin.compat.PlaceholderApiCompat.unregister();
        } catch (LinkageError | RuntimeException ignored) {
            // PlaceholderAPI already gone
        }
    }
}
