package com.nickname.plugin.compat;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * HelpChat PlaceholderAPI integration.
 * This class is ONLY loaded by the JVM when PlaceholderAPI is present on the server.
 * Never reference it directly — use {@link com.nickname.plugin.hooks.PlaceholderApiHook}.
 */
public final class PlaceholderApiCompat {

    private static Expansion expansion;

    private PlaceholderApiCompat() {}

    public static boolean register(@Nonnull NicknamePlaceholders placeholders, @Nonnull String version) {
        expansion = new Expansion(placeholders, version);
        return expansion.register();
    }

    public static void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    @Nonnull
    public static String setPlaceholders(@Nonnull PlayerRef player, @Nonnull String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /** {@code %nnc_<name>%}, see {@link NicknamePlaceholders}. */
    private static final class Expansion extends PlaceholderExpansion {
        private final NicknamePlaceholders placeholders;
        private final String version;

        Expansion(NicknamePlaceholders placeholders, String version) {
            this.placeholders = placeholders;
            this.version = version;
        }

        @Override
        public String getIdentifier() {
            return "nnc";
        }

        @Override
        public String getAuthor() {
            return "Morgott";
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(PlayerRef player, String params) {
            if (player == null || params == null) return null;
            return placeholders.resolve(player.getUuid(), player.getUsername(), params.toLowerCase(Locale.ROOT));
        }
    }
}
