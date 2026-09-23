package com.nickname.plugin.storage;

import com.nickname.plugin.config.PluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NicknameStorageTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path dir;

    private NicknameStorage storageWith(String file, String content) throws Exception {
        Files.writeString(dir.resolve(file), content, StandardCharsets.UTF_8);
        return new NicknameStorage(dir, new PluginConfig());
    }

    @Test
    void nullOrNonObjectFileIsKeptAndClaimFails() throws Exception {
        for (String content : new String[]{"null", "", "[]", "{\"" + PLAYER + "\": 5}", "{\"not-a-uuid\": \"x\"}"}) {
            NicknameStorage storage = storageWith("nicknames.json", content);
            assertEquals(NicknameStorage.ClaimResult.STORAGE_ERROR,
                storage.claimNickname(PLAYER, "Bob", "Bob", true, false), content);
            assertNull(storage.getNickname(PLAYER));
            assertEquals(content, Files.readString(dir.resolve("nicknames.json"), StandardCharsets.UTF_8));
        }
    }

    @Test
    void failedMessageColorSaveIsNotReportedAndRolledBack() throws Exception {
        NicknameStorage storage = storageWith("messagecolors.json", "{broken");
        assertFalse(storage.setMessageColor(PLAYER, "#FF0000"));
        assertNull(storage.getMessageColor(PLAYER));
        assertEquals("{broken", Files.readString(dir.resolve("messagecolors.json"), StandardCharsets.UTF_8));
    }

    @Test
    void unreadableKnownNamesBlockRealNameCheck() throws Exception {
        NicknameStorage storage = storageWith("originals.json", "{broken");
        assertEquals(NicknameStorage.ClaimResult.STORAGE_ERROR, storage.claimNickname(PLAYER, "Bob", "Bob", true, true));
        assertEquals(NicknameStorage.ClaimResult.OK, storage.claimNickname(PLAYER, "Bob", "Bob", true, false));
    }

    @Test
    void validFileWithBomLoadsAndSaves() throws Exception {
        NicknameStorage storage = storageWith("nicknames.json", "﻿{\"" + PLAYER + "\": \"<b>Bob</b>\"}");
        assertEquals("<b>Bob</b>", storage.getNickname(PLAYER));
        assertTrue(storage.removeNickname(PLAYER));
        assertNull(new NicknameStorage(dir, new PluginConfig()).getNickname(PLAYER));
    }
}
