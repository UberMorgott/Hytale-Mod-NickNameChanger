package com.nickname.plugin.compat;

import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.storage.NicknameStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NicknameMirrorTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @TempDir
    Path dir;

    private PluginConfig config;
    private NicknameStorage storage;
    private FakeMirror mirror;

    /** In-memory stand-in for a chat plugin's nickname store with its own validation. */
    private static final class FakeMirror extends NicknameMirror {
        final Map<UUID, String> nicks = new HashMap<>();
        final Set<String> refused = new HashSet<>();

        FakeMirror(NicknameStorage storage) {
            super(storage, "fake");
        }

        @Override public String name() { return "Fake"; }
        @Override protected String convert(String nickname) { return nickname; }
        @Override protected String current(UUID uuid) { return nicks.get(uuid); }
        @Override protected String set(UUID uuid, String nickname) {
            if (refused.contains(nickname) || !nickname.matches("[A-Za-z0-9_]+")) return "refused";
            nicks.put(uuid, nickname);
            return null;
        }
        @Override protected String remove(UUID uuid) {
            return nicks.remove(uuid) != null ? null : "not found";
        }
    }

    @BeforeEach
    void setUp() {
        config = new PluginConfig();
        storage = new NicknameStorage(dir, config);
        mirror = new FakeMirror(storage);
    }

    private void nnc(String nickname) {
        if (nickname == null) {
            storage.removeNickname(PLAYER);
        } else {
            assertEquals(NicknameStorage.ClaimResult.OK, storage.claimNickname(PLAYER, nickname, nickname, true, false));
        }
    }

    @Test
    void ownNicknameIsRestoredOnResetAndShowInChatOff() {
        mirror.nicks.put(PLAYER, "Own");
        nnc("Nick");
        assertNull(mirror.sync(PLAYER));
        assertEquals("Nick", mirror.nicks.get(PLAYER));

        config.display.showInChat = false;
        assertNull(mirror.sync(PLAYER));
        assertEquals("Own", mirror.nicks.get(PLAYER));

        config.display.showInChat = true;
        assertNull(mirror.sync(PLAYER));
        nnc(null);
        assertNull(mirror.sync(PLAYER));
        assertEquals("Own", mirror.nicks.get(PLAYER));
        assertNull(storage.getMirrorRecord("fake", PLAYER));
    }

    @Test
    void equalPreexistingNicknameIsNotTakenAsOurs() {
        mirror.nicks.put(PLAYER, "Same");
        nnc("Same");
        assertNull(mirror.sync(PLAYER));
        nnc(null);
        assertNull(mirror.sync(PLAYER));
        assertEquals("Same", mirror.nicks.get(PLAYER));
    }

    @Test
    void refusedRenameStillReleasesTheLastMirroredValue() {
        nnc("Accepted");
        assertNull(mirror.sync(PLAYER));
        nnc("Ник");
        assertEquals("refused", mirror.sync(PLAYER));
        assertEquals("Accepted", mirror.nicks.get(PLAYER));
        nnc(null);
        assertNull(mirror.sync(PLAYER));
        assertNull(mirror.nicks.get(PLAYER), "the nickname NNC wrote must not stay behind");
    }

    @Test
    void failedRestoreKeepsTheRecordAndReportsIt() {
        mirror.nicks.put(PLAYER, "Original");
        nnc("Nick");
        assertNull(mirror.sync(PLAYER));
        mirror.refused.add("Original"); // e.g. blacklisted in the meantime
        nnc(null);
        assertEquals("refused", mirror.sync(PLAYER));
        assertEquals("Nick", mirror.nicks.get(PLAYER));
        assertEquals("Original", storage.getMirrorRecord("fake", PLAYER).prior());

        mirror.refused.clear();
        assertNull(mirror.sync(PLAYER));
        assertEquals("Original", mirror.nicks.get(PLAYER));
        assertNull(storage.getMirrorRecord("fake", PLAYER));
    }

    @Test
    void nicknameChangedInThePluginIsLeftAlone() {
        nnc("Nick");
        assertNull(mirror.sync(PLAYER));
        mirror.nicks.put(PLAYER, "ChangedThere");
        nnc(null);
        assertNull(mirror.sync(PLAYER));
        assertEquals("ChangedThere", mirror.nicks.get(PLAYER));
        assertNull(storage.getMirrorRecord("fake", PLAYER));
    }

    @Test
    void recordSurvivesRestart() {
        nnc("Nick");
        assertNull(mirror.sync(PLAYER));
        NicknameStorage reloaded = new NicknameStorage(dir, config);
        assertEquals(new NicknameStorage.MirrorRecord(null, "Nick"), reloaded.getMirrorRecord("fake", PLAYER));
    }
}
