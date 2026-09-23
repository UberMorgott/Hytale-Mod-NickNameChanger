package com.nickname.plugin.compat;

import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.storage.NicknameStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NicknameMirrorTest {

    @TempDir
    Path dir;

    /** In-memory stand-in for a chat plugin's nickname store that only accepts ASCII names. */
    private static final class FakeMirror extends NicknameMirror {
        final Map<UUID, String> nicks = new HashMap<>();

        FakeMirror(NicknameStorage storage) {
            super(storage, "fake");
        }

        @Override public String name() { return "Fake"; }
        @Override protected String convert(String nickname) { return "[" + nickname + "]"; }
        @Override protected String current(UUID uuid) { return nicks.get(uuid); }
        @Override protected String set(UUID uuid, String nickname) {
            if (!nickname.matches("[\\[\\]A-Za-z0-9_]+")) return "only A-Z";
            nicks.put(uuid, nickname);
            return null;
        }
        @Override protected void remove(UUID uuid) { nicks.remove(uuid); }
    }

    @Test
    void keepsOwnNicknameAsideAndRestoresIt() {
        UUID uuid = UUID.randomUUID();
        PluginConfig config = new PluginConfig();
        NicknameStorage storage = new NicknameStorage(dir, config);
        FakeMirror mirror = new FakeMirror(storage);
        mirror.nicks.put(uuid, "Own");

        storage.claimNickname(uuid, "Nick", "Nick", true, false);
        assertNull(mirror.sync(uuid, null));
        assertEquals("[Nick]", mirror.nicks.get(uuid));

        config.display.showInChat = false;
        assertNull(mirror.sync(uuid, null));
        assertEquals("Own", mirror.nicks.get(uuid));

        config.display.showInChat = true;
        assertNull(mirror.sync(uuid, null));
        storage.removeNickname(uuid);
        assertNull(mirror.sync(uuid, "Nick"));
        assertEquals("Own", mirror.nicks.get(uuid));
        assertNull(storage.getReplacedNickname("fake", uuid));
    }

    @Test
    void refusalIsReportedAndNothingIsForced() {
        UUID uuid = UUID.randomUUID();
        NicknameStorage storage = new NicknameStorage(dir, new PluginConfig());
        FakeMirror mirror = new FakeMirror(storage);
        storage.claimNickname(uuid, "Ник", "Ник", true, false);
        assertEquals("only A-Z", mirror.sync(uuid, null));
        assertNull(mirror.nicks.get(uuid));
    }
}
