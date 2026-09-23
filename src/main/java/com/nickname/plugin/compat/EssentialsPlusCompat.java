package com.nickname.plugin.compat;

import com.nickname.plugin.hooks.PluginDetector;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * EssentialsPlus (fof1092) formats chat itself and shows {@code {player}} from its own
 * {@code NickManager}; this mirror keeps that nickname equal to the NNC nickname.
 * EP only accepts A-Z, a-z, 0-9 and _ (plus its own length/blacklist/duplicate rules); other
 * nicknames are refused by EP and reported. Reflection, verified against EssentialsPlus 1.20.0.
 */
public final class EssentialsPlusCompat extends NicknameMirror {

    private static final String BASE = "de.fof1092.essentialsplus.";

    private final Method getInstance;
    private final Method getNickname;
    private final Method setNickname;
    private final Method removeNickname;
    private final Method resultIsValid;
    private final Method resultReason;
    private final Method getConfigManager;
    private final Method getChatConfig;
    private final Method chatEnabled;

    private EssentialsPlusCompat(NicknameStorage storage) throws ReflectiveOperationException {
        super(storage, "essentialsplus");
        Class<?> nickManager = Class.forName(BASE + "features.player.nick.NickManager");
        Class<?> result = Class.forName(BASE + "features.player.nick.NickManager$NicknameValidationResult");
        Class<?> configManager = Class.forName(BASE + "core.ConfigManager");
        this.getInstance = nickManager.getMethod("getInstance");
        this.getNickname = nickManager.getMethod("getNickname", UUID.class);
        this.setNickname = nickManager.getMethod("setNickname", UUID.class, String.class);
        this.removeNickname = nickManager.getMethod("removeNickname", UUID.class);
        this.resultIsValid = result.getMethod("isValid");
        this.resultReason = result.getMethod("getReason");
        this.getConfigManager = Class.forName(BASE + "EssentialsPlus").getMethod("getConfigManager");
        this.getChatConfig = configManager.getMethod("getChat");
        this.chatEnabled = Class.forName(BASE + "core.ConfigManager$ChatConfig").getMethod("isEnabled");
    }

    /** Returns the adapter, or {@code null} if EssentialsPlus is not installed or its API differs. */
    @Nullable
    public static EssentialsPlusCompat create(@Nonnull NicknameStorage storage) {
        if (!PluginDetector.isLoaded(PluginDetector.ESSENTIALS_PLUS)) return null;
        try {
            return new EssentialsPlusCompat(storage);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Unsupported EssentialsPlus version; nickname sync disabled. "
                + "Use %%nnc_nickname_mini%% (PlaceholderAPI) in its chat format instead.");
            return null;
        }
    }

    @Nonnull
    @Override
    public String name() {
        return "EssentialsPlus";
    }

    /** True if EssentialsPlus currently formats chat (its chat.enabled setting). */
    public boolean isChatEnabled() {
        try {
            return (boolean) chatEnabled.invoke(getChatConfig.invoke(getConfigManager.invoke(null)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Nonnull
    @Override
    protected String convert(@Nonnull String nickname) {
        return MessageUtil.toEssentialsPlus(nickname);
    }

    @Nullable
    @Override
    protected String current(@Nonnull UUID uuid) throws ReflectiveOperationException {
        return (String) getNickname.invoke(getInstance.invoke(null), uuid);
    }

    @Nullable
    @Override
    protected String set(@Nonnull UUID uuid, @Nonnull String nickname) throws ReflectiveOperationException {
        Object result = setNickname.invoke(getInstance.invoke(null), uuid, nickname);
        return (boolean) resultIsValid.invoke(result)
            ? null : Objects.requireNonNullElse((String) resultReason.invoke(result), "refused");
    }

    @Override
    protected void remove(@Nonnull UUID uuid) throws ReflectiveOperationException {
        removeNickname.invoke(getInstance.invoke(null), uuid);
    }

    /** EssentialsPlus markup wrapping the chat text in the player's message color. */
    @Nonnull
    public static String colorMessage(@Nonnull String content, @Nonnull String colorSpec) {
        if (colorSpec.startsWith("gradient:")) {
            String[] parts = colorSpec.split(":");
            return "<gradient:" + parts[1] + ":" + parts[2] + ">" + content + "</gradient>";
        }
        return "<" + colorSpec + ">" + content + "</" + colorSpec + ">";
    }
}
