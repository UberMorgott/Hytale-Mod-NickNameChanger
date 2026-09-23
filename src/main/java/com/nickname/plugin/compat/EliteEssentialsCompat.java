package com.nickname.plugin.compat;

import com.nickname.plugin.hooks.PluginDetector;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * EliteEssentials formats chat itself and shows {@code {player}} / {@code {displayname}} from its
 * own {@code NickService}; this mirror keeps that nickname equal to the NNC nickname, with colors
 * as {@code &#RRGGBB} codes. Reflection, verified against EliteEssentials 2.0.11.
 */
public final class EliteEssentialsCompat extends NicknameMirror {

    private final Object nickService;
    private final Method getNickname;
    private final Method setNick;
    private final Method clearNick;
    private final Object configManager;
    private final Method getConfig;
    private final Field chatFormat;
    private final Field chatFormatEnabled;

    private EliteEssentialsCompat(NicknameStorage storage) throws ReflectiveOperationException {
        super(storage, "eliteessentials");
        Class<?> plugin = Class.forName("com.eliteessentials.EliteEssentials");
        Class<?> service = Class.forName("com.eliteessentials.services.NickService");
        Object instance = plugin.getMethod("getInstance").invoke(null);
        this.nickService = plugin.getMethod("getNickService").invoke(instance);
        this.configManager = plugin.getMethod("getConfigManager").invoke(instance);
        this.getConfig = Class.forName("com.eliteessentials.config.ConfigManager").getMethod("getConfig");
        this.chatFormat = Class.forName("com.eliteessentials.config.PluginConfig").getField("chatFormat");
        this.chatFormatEnabled = Class.forName("com.eliteessentials.config.PluginConfig$ChatFormatConfig").getField("enabled");
        this.getNickname = service.getMethod("getNickname", UUID.class);
        this.setNick = service.getMethod("setNick", UUID.class, String.class);
        this.clearNick = service.getMethod("clearNick", UUID.class);
        if (nickService == null || configManager == null) throw new IllegalStateException("EliteEssentials NickService not available");
    }

    /** Returns the adapter, or {@code null} if EliteEssentials is not installed or its API differs. Call from start(). */
    @Nullable
    public static EliteEssentialsCompat create(@Nonnull NicknameStorage storage) {
        if (!PluginDetector.isLoaded(PluginDetector.ELITE_ESSENTIALS)) return null;
        try {
            return new EliteEssentialsCompat(storage);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Unsupported EliteEssentials version; nickname sync disabled. "
                + "Use %%nnc_nickname_legacy%% (PlaceholderAPI) in its chat format instead.");
            return null;
        }
    }

    @Nonnull
    @Override
    public String name() {
        return "EliteEssentials";
    }

    /** True if EliteEssentials currently formats chat (its chatFormat.enabled setting). */
    public boolean isChatEnabled() {
        try {
            return chatFormatEnabled.getBoolean(chatFormat.get(getConfig.invoke(configManager)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Nonnull
    @Override
    protected String convert(@Nonnull String nickname) {
        return MessageUtil.toLegacy(nickname);
    }

    @Nullable
    @Override
    protected String current(@Nonnull UUID uuid) throws ReflectiveOperationException {
        return (String) getNickname.invoke(nickService, uuid);
    }

    @Nullable
    @Override
    protected String set(@Nonnull UUID uuid, @Nonnull String nickname) throws ReflectiveOperationException {
        Object result = setNick.invoke(nickService, uuid, nickname);
        return "SET".equals(String.valueOf(result)) ? null : String.valueOf(result);
    }

    @Nullable
    @Override
    protected String remove(@Nonnull UUID uuid) throws ReflectiveOperationException {
        return (boolean) clearNick.invoke(nickService, uuid) ? null : "PLAYER_NOT_FOUND";
    }
}
