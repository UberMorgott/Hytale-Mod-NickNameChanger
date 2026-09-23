package com.nickname.plugin.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.nickname.plugin.storage.NicknameStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Chat plugins with their own nickname store (EssentialsPlus, EliteEssentials) show that nickname
 * in their chat. A mirror keeps it equal to the NNC nickname through the plugin's public API:
 * set when the NNC nickname changes or the player joins, removed on reset or when
 * Display.ShowInChat is off. A nickname the player already had there is kept aside and restored
 * when NNC stops using it. The plugin's own validation is respected; refusals are reported.
 */
public abstract class NicknameMirror {

    protected static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private final NicknameStorage storage;
    private final String id;

    protected NicknameMirror(@Nonnull NicknameStorage storage, @Nonnull String id) {
        this.storage = storage;
        this.id = id;
    }

    /** Plugin name for messages. */
    @Nonnull
    public abstract String name();

    /** The NNC nickname in the plugin's markup. */
    @Nonnull
    protected abstract String convert(@Nonnull String nickname);

    @Nullable
    protected abstract String current(@Nonnull UUID uuid) throws ReflectiveOperationException;

    /** @return the plugin's reason if it refused, otherwise {@code null} */
    @Nullable
    protected abstract String set(@Nonnull UUID uuid, @Nonnull String nickname) throws ReflectiveOperationException;

    protected abstract void remove(@Nonnull UUID uuid) throws ReflectiveOperationException;

    /**
     * Brings the plugin's nickname in line with the NNC nickname (or removes it). Call after the
     * NNC nickname changed and when the player joins (the plugin has loaded its player data by then).
     *
     * @param previousNickname the NNC nickname before the change, to recognise what NNC set earlier
     * @return the plugin's reason if it refused the nickname, otherwise {@code null}
     */
    @Nullable
    public String sync(@Nonnull UUID uuid, @Nullable String previousNickname) {
        String nickname = storage.getNickname(uuid);
        String desired = nickname != null && storage.isShowInChat() ? convert(nickname) : null;
        try {
            String current = current(uuid);
            boolean setByNnc = current != null && (current.equals(desired)
                || (previousNickname != null && current.equals(convert(previousNickname)))
                || (nickname != null && current.equals(convert(nickname))));

            if (desired != null) {
                if (desired.equals(current)) return null;
                if (current != null && !setByNnc && !storage.setReplacedNickname(id, uuid, current)) {
                    return "could not back up the " + name() + " nickname";
                }
                return set(uuid, desired);
            }

            if (setByNnc) {
                String own = storage.getReplacedNickname(id, uuid);
                String refusal = own != null ? set(uuid, own) : null;
                if (own == null || refusal != null) {
                    remove(uuid);
                }
                if (refusal != null) {
                    LOGGER.at(Level.INFO).log("Could not restore %s nickname '%s' of %s: %s", name(), own, uuid, refusal);
                }
                if (own != null) {
                    storage.setReplacedNickname(id, uuid, null);
                }
            }
            return null;
        } catch (InvocationTargetException e) {
            LOGGER.at(Level.WARNING).withCause(e.getCause()).log("%s nickname sync failed for %s", name(), uuid);
            return String.valueOf(e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("%s nickname sync failed for %s", name(), uuid);
            return e.toString();
        }
    }
}
