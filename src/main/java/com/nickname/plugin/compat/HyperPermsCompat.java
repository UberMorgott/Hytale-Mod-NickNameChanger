package com.nickname.plugin.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.nickname.plugin.hooks.PluginDetector;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Registers {@code %nnc_nickname%} (legacy color codes) in HyperPerms' own chat format through
 * {@code com.hyperperms.chat.ChatFormatter.registerPlaceholder}. Uses reflection, so nothing
 * of HyperPerms is needed at compile time or when it is not installed.
 */
public final class HyperPermsCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");
    private static final String FORMATTER_CLASS = "com.hyperperms.chat.ChatFormatter";
    private static final String PLACEHOLDER = "nnc_nickname";

    private static boolean registered = false;

    private HyperPermsCompat() {}

    /** @return true if the placeholder was registered */
    public static boolean register(@Nonnull NicknamePlaceholders placeholders) {
        if (!PluginDetector.isLoaded(PluginDetector.HYPERPERMS)) return false;
        try {
            Class<?> formatter = Class.forName(FORMATTER_CLASS);
            Class<?> context = Class.forName(FORMATTER_CLASS + "$PlaceholderContext");
            Method getUuid = context.getMethod("getUuid");
            Method getPlayerName = context.getMethod("getPlayerName");
            Function<Object, String> handler = ctx -> {
                try {
                    UUID uuid = (UUID) getUuid.invoke(ctx);
                    String name = (String) getPlayerName.invoke(ctx);
                    return uuid == null ? name : placeholders.resolve(uuid, name != null ? name : "", "nickname_legacy");
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            };
            formatter.getMethod("registerPlaceholder", String.class, Function.class).invoke(null, PLACEHOLDER, handler);
            registered = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not register the HyperPerms chat placeholder.");
            return false;
        }
    }

    public static void unregister() {
        if (!registered) return;
        registered = false;
        try {
            Class.forName(FORMATTER_CLASS).getMethod("unregisterPlaceholder", String.class).invoke(null, PLACEHOLDER);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // HyperPerms already gone
        }
    }
}
