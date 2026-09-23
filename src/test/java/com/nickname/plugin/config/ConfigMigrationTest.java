package com.nickname.plugin.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigrationTest {

    @TempDir
    Path dir;

    private JsonObject migrate(String json) throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        ConfigMigration.migrate(file);
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void legacyKeysAreRenamedAndValuesKept() throws Exception {
        JsonObject root = migrate("""
            {"chatFormat": "[{username}] {message}",
             "display": {"showInChat": false, "showOnMap": true},
             "nicknames": {"maxLength": 12, "bannedWords": ["foo"]},
             "integrations": {"luckperms": {"enabled": false, "showPrefix": false}}}
            """);
        assertEquals("[{username}] {message}", root.get("ChatFormat").getAsString());
        assertFalse(root.getAsJsonObject("Display").get("ShowInChat").getAsBoolean());
        assertTrue(root.getAsJsonObject("Display").get("ShowOnMap").getAsBoolean());
        assertEquals(12, root.getAsJsonObject("Nicknames").get("MaxLength").getAsInt());
        assertEquals("foo", root.getAsJsonObject("Nicknames").getAsJsonArray("BannedWords").get(0).getAsString());
        JsonObject lp = root.getAsJsonObject("Integrations").getAsJsonObject("Luckperms");
        assertFalse(lp.get("Enabled").getAsBoolean());
        assertFalse(lp.get("ShowPrefix").getAsBoolean());
        assertFalse(root.has("chatFormat"));
        assertTrue(Files.exists(dir.resolve("config.json.pre-0.0.18.bak")));
    }

    @Test
    void currentKeysWinOverLegacyOnes() throws Exception {
        JsonObject root = migrate("""
            {"Display": {"ShowInChat": true}, "display": {"showInChat": false, "showOnMap": true}}
            """);
        JsonObject display = root.getAsJsonObject("Display");
        assertTrue(display.get("ShowInChat").getAsBoolean());
        assertTrue(display.get("ShowOnMap").getAsBoolean());
        assertFalse(display.has("showInChat"));
    }

    @Test
    void nestedMixedObjectsKeepLegacyOnlyLeaves() throws Exception {
        JsonObject root = migrate("""
            {"integrations": {"luckperms": {"showPrefix": false, "enabled": true}},
             "Integrations": {"luckperms": {"enabled": false}}}
            """);
        JsonObject lp = root.getAsJsonObject("Integrations").getAsJsonObject("Luckperms");
        assertFalse(lp.get("ShowPrefix").getAsBoolean());
        assertFalse(lp.get("Enabled").getAsBoolean(), "the current spelling wins at the same leaf");
        assertEquals(2, lp.size());
        assertEquals(1, root.getAsJsonObject("Integrations").size());
    }

    @Test
    void unreadableFileFailsAndStaysUntouched() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"chatFormat\": ", StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> ConfigMigration.migrate(file));
        assertEquals("{\"chatFormat\": ", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void currentFileIsNotRewritten() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"ChatFormat\": \"x\"}", StandardCharsets.UTF_8);
        assertFalse(ConfigMigration.migrate(file));
        assertFalse(Files.exists(dir.resolve("config.json.pre-0.0.18.bak")));
    }

    @Test
    void byteOrderMarkIsRemoved() throws Exception {
        JsonObject root = migrate("﻿{\"ChatFormat\": \"x\"}");
        assertEquals("x", root.get("ChatFormat").getAsString());
    }
}
