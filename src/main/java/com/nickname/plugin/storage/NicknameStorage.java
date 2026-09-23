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
import java.util.Set;
import java.util.UUID;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * Handles persistent storage of player nicknames.
 */
public class NicknameStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> nicknames = new HashMap<>();
    private final Map<UUID, String> originalUsernames = new HashMap<>();
    private final Map<UUID, String> messageColors = new HashMap<>();
    private final Set<Path> unwritableFiles = new HashSet<>();
    private final Path storageFile;
    private final Path originalsFile;
    private final Path messageColorsFile;
    private final PluginConfig config;

    public NicknameStorage(Path dataFolder, PluginConfig config) {
        this.storageFile = dataFolder.resolve("nicknames.json");
        this.originalsFile = dataFolder.resolve("originals.json");
        this.messageColorsFile = dataFolder.resolve("messagecolors.json");
        this.config = config;
        load();
    }

    public synchronized String getNickname(UUID uuid) {
        return nicknames.get(uuid);
    }

    /** Why {@link #claimNickname} refused a nickname. */
    public enum ClaimResult { OK, TAKEN_BY_NICKNAME, TAKEN_BY_USERNAME, NOT_SAVED }

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
        String previous = nicknames.put(uuid, nickname);
        if (!saveNicknames()) {
            if (previous != null) nicknames.put(uuid, previous); else nicknames.remove(uuid);
            return ClaimResult.NOT_SAVED;
        }
        return ClaimResult.OK;
    }

    public synchronized void removeNickname(UUID uuid) {
        if (nicknames.remove(uuid) != null) {
            saveNicknames();
        }
    }

    /**
     * Records the player's real username (called on every connect). Known usernames are kept after
     * a nickname reset, so nicknames can't impersonate players that are offline.
     */
    public synchronized void rememberUsername(UUID uuid, String username) {
        if (!username.equals(originalUsernames.put(uuid, username))) {
            saveOriginals();
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

    public synchronized void setMessageColor(UUID uuid, String color) {
        if (color == null || color.isEmpty()) {
            messageColors.remove(uuid);
        } else {
            messageColors.put(uuid, color);
        }
        saveMessageColors();
    }

    public synchronized void removeMessageColor(UUID uuid) {
        if (messageColors.remove(uuid) != null) {
            saveMessageColors();
        }
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

    private void load() {
        loadMap(storageFile, nicknames);
        loadMap(messageColorsFile, messageColors);
        loadMap(originalsFile, originalUsernames);
    }

    /**
     * Loads a UUID-to-string map. A file that cannot be read completely (bad JSON, bad encoding,
     * invalid UUID keys) is never overwritten: it is marked unwritable so the data stays on disk
     * for manual repair instead of being replaced by a partial map on the next save.
     */
    private void loadMap(Path file, Map<UUID, String> target) {
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.startsWith("﻿")) {
                json = json.substring(1); // UTF-8 BOM written by some editors (e.g. Notepad)
            }
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> loaded = GSON.fromJson(json, type);
            Map<UUID, String> parsed = new HashMap<>();
            if (loaded != null) {
                for (Map.Entry<String, String> entry : loaded.entrySet()) {
                    if (entry.getValue() == null) {
                        throw new IllegalArgumentException("null value for key " + entry.getKey());
                    }
                    parsed.put(UUID.fromString(entry.getKey()), entry.getValue());
                }
            }
            target.putAll(parsed);
        } catch (Exception e) {
            unwritableFiles.add(file);
            LOGGER.at(Level.SEVERE).withCause(e).log(
                "Could not read %s. The file is left untouched and will NOT be saved until it is fixed and the server restarted.",
                file.toAbsolutePath());
        }
    }

    private synchronized boolean saveMap(Path file, Map<UUID, String> source) {
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

    private boolean saveNicknames() {
        return saveMap(storageFile, nicknames);
    }

    private void saveOriginals() {
        saveMap(originalsFile, originalUsernames);
    }

    private void saveMessageColors() {
        saveMap(messageColorsFile, messageColors);
    }
}
