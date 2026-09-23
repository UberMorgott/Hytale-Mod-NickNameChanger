package com.nickname.plugin.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.nickname.plugin.hooks.PluginDetector;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * EssentialsPlus (fof1092) formats chat itself and shows {@code {player}} from its own
 * {@code NickManager}. This adapter keeps EP's nickname in sync with the NNC nickname through
 * EP's public NickManager API (set on nickname change and on join, removed on reset or when
 * Display.ShowInChat is off). The player's username is never touched.
 * <p>
 * EP validates nicknames itself (letters, digits and _ only, its own length/blacklist/duplicate
 * rules); a refusal is reported, never bypassed. An EP nickname the player had before is kept
 * aside and restored when NNC stops using EP's nickname.
 * Uses reflection (verified against EssentialsPlus 1.20.0), so nothing of EP is needed at compile time.
 */
public final class EssentialsPlusCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");
    private static final String BASE = "de.fof1092.essentialsplus.";

    private final NicknameStorage storage;
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
        this.storage = storage;
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
            EssentialsPlusCompat compat = new EssentialsPlusCompat(storage);
            LOGGER.at(Level.INFO).log("EssentialsPlus found: NNC nicknames are synced to EssentialsPlus' {player}.");
            return compat;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Unsupported EssentialsPlus version; nickname sync disabled. "
                + "Use %%nnc_nickname_mini%% (PlaceholderAPI) in its chat format instead.");
            return null;
        }
    }

    /** True if EssentialsPlus currently formats chat (its chat.enabled setting). */
    public boolean isChatEnabled() {
        try {
            return (boolean) chatEnabled.invoke(getChatConfig.invoke(getConfigManager.invoke(null)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Brings EP's nickname in line with the NNC nickname (or removes it). Call after the NNC
     * nickname changed and when the player joins (EP has loaded its user by then).
     *
     * @param previousNickname the NNC nickname before the change, to recognise the EP nickname NNC set earlier
     * @return EP's reason if it refused the nickname, otherwise {@code null}
     */
    @Nullable
    public String sync(@Nonnull UUID uuid, @Nullable String previousNickname) {
        String nickname = storage.getNickname(uuid);
        String desired = nickname != null && storage.isShowInChat() ? MessageUtil.toEssentialsPlus(nickname) : null;
        try {
            Object manager = getInstance.invoke(null);
            String current = (String) getNickname.invoke(manager, uuid);
            boolean setByNnc = current != null && (current.equals(desired)
                || (previousNickname != null && current.equals(MessageUtil.toEssentialsPlus(previousNickname)))
                || (nickname != null && current.equals(MessageUtil.toEssentialsPlus(nickname))));

            if (desired != null) {
                if (desired.equals(current)) return null;
                if (current != null && !setByNnc && !storage.setEssentialsPlusNickname(uuid, current)) {
                    return "could not back up the EssentialsPlus nickname";
                }
                Object result = setNickname.invoke(manager, uuid, desired);
                return (boolean) resultIsValid.invoke(result)
                    ? null : Objects.requireNonNullElse((String) resultReason.invoke(result), "refused");
            }

            if (setByNnc) {
                String own = storage.getEssentialsPlusNickname(uuid);
                if (own == null) {
                    removeNickname.invoke(manager, uuid);
                } else {
                    Object result = setNickname.invoke(manager, uuid, own);
                    if (!(boolean) resultIsValid.invoke(result)) {
                        removeNickname.invoke(manager, uuid);
                        LOGGER.at(Level.INFO).log("Could not restore EssentialsPlus nickname '%s' of %s: %s",
                            own, uuid, resultReason.invoke(result));
                    }
                    storage.setEssentialsPlusNickname(uuid, null);
                }
            }
            return null;
        } catch (InvocationTargetException e) {
            LOGGER.at(Level.WARNING).withCause(e.getCause()).log("EssentialsPlus nickname sync failed for %s", uuid);
            return String.valueOf(e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("EssentialsPlus nickname sync failed for %s", uuid);
            return e.toString();
        }
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
