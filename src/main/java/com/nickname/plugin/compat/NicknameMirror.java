package com.nickname.plugin.compat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.storage.NicknameStorage.MirrorRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Chat plugins with their own nickname store (EssentialsPlus, EliteEssentials) show that nickname
 * in their chat. A mirror keeps it equal to the NNC nickname through the plugin's public API.
 * <p>
 * Ownership is explicit: before the first write NNC saves a {@link MirrorRecord} with the
 * plugin's previous nickname (or none), and after every successful write the value it wrote.
 * On reset / ShowInChat off the previous nickname is restored, but only if the plugin still shows
 * what NNC wrote; a nickname the player changed there in the meantime is left alone. Equal text
 * is never taken as proof that NNC wrote it. Refusals are reported and never advance the record.
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

    /** @return the plugin's reason if it could not remove the nickname, otherwise {@code null} */
    @Nullable
    protected abstract String remove(@Nonnull UUID uuid) throws ReflectiveOperationException;

    /**
     * Brings the plugin's nickname in line with the NNC nickname, or gives the plugin its own
     * nickname back. Call after the NNC nickname or ShowInChat changed and when the player joins.
     *
     * @return a reason if the plugin refused or something could not be saved, otherwise {@code null}
     */
    @Nullable
    public String sync(@Nonnull UUID uuid) {
        String nickname = storage.getNickname(uuid);
        String desired = nickname != null && storage.isShowInChat() ? convert(nickname) : null;
        try {
            String current = current(uuid);
            MirrorRecord record = storage.getMirrorRecord(id, uuid);
            return desired != null ? mirror(uuid, desired, current, record) : release(uuid, current, record);
        } catch (InvocationTargetException e) {
            LOGGER.at(Level.WARNING).withCause(e.getCause()).log("%s nickname sync failed for %s", name(), uuid);
            return String.valueOf(e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("%s nickname sync failed for %s", name(), uuid);
            return e.toString();
        }
    }

    @Nullable
    private String mirror(UUID uuid, String desired, @Nullable String current, @Nullable MirrorRecord record)
            throws ReflectiveOperationException {
        if (desired.equals(current)) {
            // Already shown; without a record this is the player's own nickname, not ours
            return null;
        }
        boolean ours = record != null && (Objects.equals(current, record.mirrored())
            || (record.mirrored() == null && Objects.equals(current, record.prior())));
        if (!ours) {
            // Take over (again): remember what the plugin showed until now
            record = new MirrorRecord(current, null);
            if (!storage.setMirrorRecord(id, uuid, record)) return notSaved();
        }
        String refusal = set(uuid, desired);
        if (refusal != null) return refusal;
        return storage.setMirrorRecord(id, uuid, new MirrorRecord(record.prior(), desired)) ? null : notSaved();
    }

    @Nullable
    private String release(UUID uuid, @Nullable String current, @Nullable MirrorRecord record)
            throws ReflectiveOperationException {
        if (record == null) return null;
        if (record.mirrored() != null && Objects.equals(current, record.mirrored())) {
            // Still showing what NNC wrote: give the plugin its previous nickname back
            String refusal = record.prior() != null ? set(uuid, record.prior()) : remove(uuid);
            if (refusal != null) {
                return refusal; // keep the record, nothing is lost
            }
        }
        // Restored, never written, or changed in the plugin since: NNC no longer owns anything there
        return storage.setMirrorRecord(id, uuid, null) ? null : notSaved();
    }

    private String notSaved() {
        return "could not save " + id + "-mirror.json";
    }
}
