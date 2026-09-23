package com.nickname.plugin.hooks;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;

/** Checks for other plugins by their manifest Group and Name (exact match). */
public final class PluginDetector {

    public static final PluginIdentifier LUCKPERMS = new PluginIdentifier("LuckPerms", "LuckPerms");
    public static final PluginIdentifier PLACEHOLDER_API = new PluginIdentifier("HelpChat", "PlaceholderAPI");
    public static final PluginIdentifier HYPERPERMS = new PluginIdentifier("com.hyperperms", "HyperPerms");
    public static final PluginIdentifier MINI_CHAT_FORMATTER = new PluginIdentifier("lucko", "mini-chat-formatter");
    public static final PluginIdentifier ESSENTIALS_PLUS = new PluginIdentifier("fof1092", "EssentialsPlus");
    public static final PluginIdentifier ELITE_ESSENTIALS = new PluginIdentifier("com.eliteessentials", "EliteEssentials");

    private PluginDetector() {}

    public static boolean isLoaded(@Nonnull PluginIdentifier id) {
        PluginManager pluginManager = PluginManager.get();
        return pluginManager != null && pluginManager.getPlugin(id) != null;
    }
}
