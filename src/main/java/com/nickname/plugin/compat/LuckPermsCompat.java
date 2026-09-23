package com.nickname.plugin.compat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;

import java.util.UUID;

/**
 * Direct LuckPerms API implementation.
 * This class is ONLY loaded by the JVM when LuckPerms is present on the server.
 * Never reference this class directly — use {@link com.nickname.plugin.hooks.LuckPermsHook} instead.
 */
public class LuckPermsCompat {

    private static LuckPerms luckPerms;

    public static void init() {
        luckPerms = LuckPermsProvider.get();
    }

    /** Metadata of an online (cached) user only; never loads from storage, safe on the chat thread. */
    private static CachedMetaData cachedMeta(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        return user != null ? user.getCachedData().getMetaData() : null;
    }

    public static String getPrefix(UUID uuid) {
        CachedMetaData meta = cachedMeta(uuid);
        return meta != null ? meta.getPrefix() : null;
    }

    public static String getSuffix(UUID uuid) {
        CachedMetaData meta = cachedMeta(uuid);
        return meta != null ? meta.getSuffix() : null;
    }
}