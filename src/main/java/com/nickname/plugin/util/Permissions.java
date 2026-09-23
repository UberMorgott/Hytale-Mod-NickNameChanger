package com.nickname.plugin.util;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.nickname.plugin.hooks.LuckPermsHook;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * The one permission check used everywhere in NNC.
 * <p>
 * LuckPerms answers {@code false} through the server's permission API for nodes that are not set
 * at all, which would turn NNC's "allowed unless denied" nodes into "denied unless granted". With
 * LuckPerms, the node is therefore asked from LuckPerms directly: set true/false wins, not set
 * means NNC's default.
 */
public final class Permissions {

    private Permissions() {}

    public static boolean has(@Nonnull UUID uuid, @Nonnull String node, boolean defaultValue) {
        Boolean luckPerms = LuckPermsHook.checkPermission(uuid, node);
        if (luckPerms != null) {
            return luckPerms;
        }
        if (LuckPermsHook.isLoaded()) {
            // LuckPerms has no explicit value (or no cached user): use our default, not its implicit false
            return defaultValue;
        }
        return PermissionsModule.get().hasPermission(uuid, node, defaultValue);
    }
}
