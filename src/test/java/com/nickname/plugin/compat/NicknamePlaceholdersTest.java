package com.nickname.plugin.compat;

import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.storage.NicknameStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NicknamePlaceholdersTest {

    @TempDir
    Path dir;

    @Test
    void chatPlaceholdersFollowShowInChat() {
        UUID uuid = UUID.randomUUID();
        PluginConfig config = new PluginConfig();
        NicknameStorage storage = new NicknameStorage(dir, config);
        storage.claimNickname(uuid, "<color:#FF0000>Nick</color>", "Nick", true, false);
        NicknamePlaceholders placeholders = new NicknamePlaceholders(storage);

        assertEquals("Nick", placeholders.resolve(uuid, "Real", "nickname"));
        assertEquals("&#FF0000Nick", placeholders.resolve(uuid, "Real", "nickname_legacy"));

        config.display.showInChat = false;
        assertEquals("Real", placeholders.resolve(uuid, "Real", "nickname"));
        assertEquals("Real", placeholders.resolve(uuid, "Real", "nickname_mini"));
        assertEquals("Nick", placeholders.resolve(uuid, "Real", "nameplate"));
        assertNull(placeholders.resolve(uuid, "Real", "unknown"));
    }
}
