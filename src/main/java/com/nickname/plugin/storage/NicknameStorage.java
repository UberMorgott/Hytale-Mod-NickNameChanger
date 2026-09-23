package com.nickname.plugin.storage;

import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.validation.NicknameValidator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;

/**
 * Handles persistent storage of player nicknames.
 * <p>
 * Every mutation is saved immediately; if the save fails the in-memory change is rolled back
 * and the method reports {@code false}, so callers never confirm a change that would be lost.
 */
public class NicknameStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> nicknames = new HashMap<>();
    private final Map<UUID, String> originalUsernames = new HashMap<>();
    private final Map<UUID, String> messageColors = new HashMap<>();
    /** EssentialsPlus nicknames players had before NNC synced theirs (restored on reset). */
    private final Map<UUID, String> essentialsPlusNicknames = new HashMap<>();
    private final Set<Path> unwritableFiles = new HashSet<>();
    private final Path storageFile;
    private final Path originalsFile;
    private final Path messageColorsFile;
    private final Path essentialsPlusFile;
    private final PluginConfig config;

    public NicknameStorage(Path dataFolder, PluginConfig config) {
        this.storageFile = dataFolder.resolve("nicknames.json");
        this.originalsFile = dataFolder.resolve("originals.json");
        this.messageColorsFile = dataFolder.resolve("messagecolors.json");
        this.essentialsPlusFile = dataFolder.resolve("essentialsplus-nicknames.json");
        this.config = config;
        load();
    }

    public synchronized String getNickname(UUID uuid) {
        return nicknames.get(uuid);
    }

    /** Why {@link #claimNickname} refused a nickname. */
    public enum ClaimResult { OK, TAKEN_BY_NICKNAME, TAKEN_BY_USERNAME, STORAGE_ERROR }

    /**
     * Checks uniqueness and stores the nickname in one step, so two players can't take the same
     * name at once. Names are compared by {@link NicknameValidator#canonical} of the visible text.
     *
     * @param checkNicknames reject if another player's nickname has the same visible text
     * @param checkUsernames reject if it equals another known player's real username
     */
    public synchronized ClaimResult claimNickname(UUID uuid, String nickname, String plain,
                                                  boolean checkNicknames, boolean checkUsernames) {
        String key = NicknameValidator.canonical(plain);
        if (checkUsernames) {
            if (unwritableFiles.contains(originalsFile)) {
                // Known names could not be loaded: real-name protection can't be guaranteed
                return ClaimResult.STORAGE_ERROR;
            }
            for (Map.Entry<UUID, String> entry : originalUsernames.entrySet()) {
                if (!entry.getKey().equals(uuid) && NicknameValidator.canonical(entry.getValue()).equals(key)) {
                    return ClaimResult.TAKEN_BY_USERNAME;
                }
            }
        }
        if (checkNicknames) {
            for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
                if (!entry.getKey().equals(uuid)
                        && NicknameValidator.canonical(MessageUtil.stripTags(entry.getValue())).equals(key)) {
                    return ClaimResult.TAKEN_BY_NICKNAME;
                }
            }
        }
        return update(nicknames, storageFile, uuid, nickname) ? ClaimResult.OK : ClaimResult.STORAGE_ERROR;
    }

    /** @return false if the change could not be saved (nothing changed then) */
    public synchronized boolean removeNickname(UUID uuid) {
        return update(nicknames, storageFile, uuid, null);
    }

    /**
     * Records the player's real username (called on every connect). Known usernames are kept after
     * a nickname reset, so nicknames can't impersonate players that are offline.
     */
    public synchronized void rememberUsername(UUID uuid, String username) {
        if (!unwritableFiles.contains(originalsFile)) {
            update(originalUsernames, originalsFile, uuid, username);
        }
    }

    /** Players (other than {@code uuid}) whose nickname has the same visible text as this username. */
    public synchronized List<UUID> findNicknameOwners(String username, UUID uuid) {
        String key = NicknameValidator.canonical(username);
        List<UUID> owners = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : nicknames.entrySet()) {
            if (!entry.getKey().equals(uuid) && NicknameValidator.canonical(MessageUtil.stripTags(entry.getValue())).equals(key)) {
                owners.add(entry.getKey());
            }
        }
        return owners;
    }

    public synchronized String getOriginalUsername(UUID uuid) {
        return originalUsernames.get(uuid);
    }

    public synchronized boolean hasNickname(UUID uuid) {
        return nicknames.containsKey(uuid);
    }

    public synchronized String getDisplayName(UUID uuid, String defaultName) {
        String nickname = nicknames.get(uuid);
        return nickname != null ? nickname : defaultName;
    }

    // --- Message colors ---

    public synchronized String getMessageColor(UUID uuid) {
        return messageColors.get(uuid);
    }

    /** @param color the color, or null/empty to remove it; returns false if it could not be saved */
    public synchronized boolean setMessageColor(UUID uuid, @Nullable String color) {
        return update(messageColors, messageColorsFile, uuid, color == null || color.isEmpty() ? null : color);
    }

    public synchronized boolean removeMessageColor(UUID uuid) {
        return setMessageColor(uuid, null);
    }

    // --- EssentialsPlus nicknames replaced by NNC ---

    public synchronized String getEssentialsPlusNickname(UUID uuid) {
        return essentialsPlusNicknames.get(uuid);
    }

    public synchronized boolean setEssentialsPlusNickname(UUID uuid, @Nullable String nickname) {
        return update(essentialsPlusNicknames, essentialsPlusFile, uuid, nickname);
    }

    // --- Global display settings (read from config) ---

    public boolean isShowInChat() {
        return config.display.showInChat;
    }

    public boolean isShowOnNameplate() {
        return config.display.showOnNameplate;
    }

    public boolean isShowInTabList() {
        return config.display.showInTabList;
    }

    /** Sets ({@code value != null}) or removes one entry and saves; rolls back and returns false if saving fails. */
    private boolean update(Map<UUID, String> map, Path file, UUID uuid, @Nullable String value) {
        String previous = value == null ? map.remove(uuid) : map.put(uuid, value);
        if (Objects.equals(previous, value) || saveMap(file, map)) {
            return true;
        }
        if (previous == null) map.remove(uuid); else map.put(uuid, previous);
        return false;
    }

    private void load() {
        loadMap(storageFile, nicknames);
        loadMap(messageColorsFile, messageColors);
        loadMap(originalsFile, originalUsernames);
        loadMap(essentialsPlusFile, essentialsPlusNicknames);
    }

    /**
     * Loads a UUID-to-string map. An existing file that is not exactly that (bad JSON or encoding,
     * empty, {@code null}, non-object root, invalid UUID key, non-string value) is never overwritten:
     * it is marked unwritable so the data stays on disk for manual repair. A missing file is empty.
     */
    private void loadMap(Path file, Map<UUID, String> target) {
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.startsWith("﻿")) {
                json = json.substring(1); // UTF-8 BOM written by some editors (e.g. Notepad)
            }
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("expected a JSON object, found: " + root);
            }
            Map<UUID, String> parsed = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) root).entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("value of " + entry.getKey() + " is not a string");
                }
                parsed.put(UUID.fromString(entry.getKey()), value.getAsString());
            }
            target.putAll(parsed);
        } catch (Exception e) {
            unwritableFiles.add(file);
            LOGGER.at(Level.SEVERE).withCause(e).log(
                "Could not read %s. The file is left untouched and will NOT be saved until it is fixed and the server restarted.",
                file.toAbsolutePath());
        }
    }

    private boolean saveMap(Path file, Map<UUID, String> source) {
        if (unwritableFiles.contains(file)) {
            LOGGER.at(Level.SEVERE).log("Not saving %s: it failed to load at startup (see earlier error).", file.getFileName());
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            Map<String, String> toSave = new HashMap<>();
            for (Map.Entry<UUID, String> entry : source.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }
            Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmpFile, StandardCharsets.UTF_8)) {
                GSON.toJson(toSave, writer);
            }
            try {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save %s", file.getFileName());
            return false;
        }
        return true;
    }
}
