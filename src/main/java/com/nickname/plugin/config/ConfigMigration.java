package com.nickname.plugin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Converts the pre-0.0.17 Gson config (camelCase keys) to the PascalCase keys read by
 * {@link PluginConfig#CODEC}. The server codec silently skips unknown keys, so without this
 * every customised setting of an old config would fall back to its default.
 * <p>
 * Runs before the config is loaded. Where a file has both spellings of a key, the PascalCase
 * value wins. The original file is copied to {@code config.json.pre-0.0.18.bak} first.
 */
public final class ConfigMigration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<String, String> ROOT_KEYS = Map.of(
        "pluginName", "PluginName",
        "version", "Version",
        "debugMode", "DebugMode",
        "chatFormat", "ChatFormat",
        "display", "Display",
        "nicknames", "Nicknames",
        "integrations", "Integrations");
    private static final Map<String, String> DISPLAY_KEYS = Map.of(
        "showInChat", "ShowInChat",
        "showOnNameplate", "ShowOnNameplate",
        "showInTabList", "ShowInTabList",
        "showOnMap", "ShowOnMap");
    private static final Map<String, String> NICKNAME_KEYS = Map.of(
        "minLength", "MinLength",
        "maxLength", "MaxLength",
        "allowCyrillic", "AllowCyrillic",
        "allowUnicode", "AllowUnicode",
        "uniqueNicknames", "UniqueNicknames",
        "bannedWords", "BannedWords");
    private static final Map<String, String> INTEGRATION_KEYS = Map.of("luckperms", "Luckperms");
    private static final Map<String, String> LUCKPERMS_KEYS = Map.of(
        "enabled", "Enabled",
        "showPrefix", "ShowPrefix",
        "showSuffix", "ShowSuffix");

    private ConfigMigration() {}

    /**
     * Migrates the file in place if it uses legacy keys or starts with a UTF-8 BOM.
     *
     * @return {@code true} if the file was rewritten
     */
    public static boolean migrate(@Nonnull Path configFile) throws IOException {
        if (!Files.exists(configFile)) return false;

        String json = Files.readString(configFile, StandardCharsets.UTF_8);
        boolean changed = false;
        if (json.startsWith("﻿")) {
            json = json.substring(1);
            changed = true;
        }

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        changed |= renameKeys(root, ROOT_KEYS);
        changed |= renameKeys(child(root, "Display"), DISPLAY_KEYS);
        changed |= renameKeys(child(root, "Nicknames"), NICKNAME_KEYS);
        JsonObject integrations = child(root, "Integrations");
        changed |= renameKeys(integrations, INTEGRATION_KEYS);
        changed |= renameKeys(child(integrations, "Luckperms"), LUCKPERMS_KEYS);
        if (!changed) return false;

        Path backup = configFile.resolveSibling(configFile.getFileName() + ".pre-0.0.18.bak");
        if (!Files.exists(backup)) {
            Files.copy(configFile, backup);
        }
        Path tmp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private static JsonObject child(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** Renames legacy keys; an existing PascalCase value wins, nested objects are merged. */
    private static boolean renameKeys(JsonObject object, Map<String, String> names) {
        if (object == null) return false;
        boolean changed = false;
        for (Map.Entry<String, String> name : names.entrySet()) {
            JsonElement legacy = object.remove(name.getKey());
            if (legacy == null) continue;
            changed = true;
            JsonElement current = object.get(name.getValue());
            if (current == null) {
                object.add(name.getValue(), legacy);
            } else if (current.isJsonObject() && legacy.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : legacy.getAsJsonObject().entrySet()) {
                    if (!current.getAsJsonObject().has(entry.getKey())) {
                        current.getAsJsonObject().add(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return changed;
    }
}
