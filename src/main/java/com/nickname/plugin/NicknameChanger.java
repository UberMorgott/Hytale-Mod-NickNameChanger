package com.nickname.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;

import com.nickname.plugin.api.NicknameAPI;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.compat.EssentialsPlusCompat;
import com.nickname.plugin.compat.HyperPermsCompat;
import com.nickname.plugin.compat.MiniChatFormatterCompat;
import com.nickname.plugin.compat.NicknamePlaceholders;
import com.nickname.plugin.config.ConfigMigration;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.display.NicknameDisplay;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.hooks.PlaceholderApiHook;
import com.nickname.plugin.hooks.PluginDetector;
import com.nickname.plugin.listeners.ChatListener;
import com.nickname.plugin.listeners.PlayerListener;
import com.nickname.plugin.service.NicknameService;
import com.nickname.plugin.storage.NicknameStorage;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class NicknameChanger extends JavaPlugin {

    private static NicknameChanger instance;

    private final Config<PluginConfig> configHolder = withConfig(PluginConfig.CODEC);

    private NicknameStorage storage;
    private PluginConfig config;
    private Path dataFolder;
    private NicknameDisplay display;

    public NicknameChanger(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static NicknameChanger getInstance() {
        return instance;
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        // Must run before the config is loaded: the codec silently ignores legacy camelCase keys,
        // so loading an unmigrated file would silently fall back to defaults. Fail loading instead
        // (the server reports the plugin error); the file and any backup stay untouched.
        try {
            if (ConfigMigration.migrate(getDataDirectory().resolve("config.json"))) {
                getLogger().at(Level.INFO).log("Migrated config.json to the current key format (backup: config.json.pre-0.0.18.bak).");
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not migrate config.json; fix or remove it and restart", e);
        }
        return super.preLoad();
    }

    @Override
    protected void setup() {
        this.dataFolder = getDataDirectory();
        this.config = configHolder.get();
        // Rewrite the (migrated) file so it lists every current option with its effective value
        config.version = getManifest().getVersion().toString();
        configHolder.save().join();

        this.storage = new NicknameStorage(dataFolder, config);
        NicknameAPI.init(storage);
        this.display = new NicknameDisplay(storage, config);

        EssentialsPlusCompat essentialsPlus = EssentialsPlusCompat.create(storage);
        NicknameService service = new NicknameService(storage, config, display, essentialsPlus);
        ChatListener chatListener = new ChatListener(storage, config, essentialsPlus);
        PlayerListener playerListener = new PlayerListener(storage, config, display, service);

        getCommandRegistry().registerCommand(new NickCommand(storage, config, service));
        if (essentialsPlus != null) {
            // FIRST: message color for EssentialsPlus, which formats chat in its own NORMAL handler
            getEventRegistry().registerGlobal(EventPriority.FIRST, PlayerChatEvent.class, chatListener::onPlayerChatEarly);
        }
        // LAST: only format chat that no other plugin has taken over
        getEventRegistry().registerGlobal(EventPriority.LAST, PlayerChatEvent.class, chatListener::onPlayerChat);
        getEventRegistry().register(PlayerConnectEvent.class, playerListener::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, playerListener::onPlayerReady);
        getEventRegistry().registerGlobal(AddWorldEvent.class, display::onAddWorld);
    }

    @Override
    protected void start() {
        // Optional integrations: every plugin is set up by now
        LuckPermsHook.init(config.integrations.luckperms.enabled);
        NicknamePlaceholders placeholders = new NicknamePlaceholders(storage);
        PlaceholderApiHook.init(placeholders, getManifest().getVersion().toString());
        HyperPermsCompat.register(placeholders);
        MiniChatFormatterCompat miniChatFormatter = MiniChatFormatterCompat.create(placeholders);
        if (miniChatFormatter != null) {
            // LATE: after mini-chat-formatter installs its formatter (priority 1), before its LAST check
            getEventRegistry().registerGlobal(EventPriority.LATE, PlayerChatEvent.class, miniChatFormatter::onPlayerChat);
        }
        logChatPluginHints();
        display.start();
    }

    @Override
    protected void shutdown() {
        if (display != null) {
            display.stop();
        }
        PlaceholderApiHook.shutdown();
        HyperPermsCompat.unregister();
    }

    /** EliteEssentials formats chat from its own nick store; it can only show NNC nicknames through PlaceholderAPI. */
    private void logChatPluginHints() {
        if (!PluginDetector.isLoaded(PluginDetector.ELITE_ESSENTIALS)) return;
        if (PlaceholderApiHook.isAvailable()) {
            getLogger().at(Level.INFO).log("EliteEssentials formats chat: put %%nnc_nickname_legacy%% instead of {player} "
                + "into its chat formats to show nicknames.");
        } else {
            getLogger().at(Level.WARNING).log("EliteEssentials formats chat, so nicknames are not shown there. "
                + "Install PlaceholderAPI (HelpChat) and use %%nnc_nickname_legacy%% in its chat formats.");
        }
    }
    public NicknameStorage getStorage() {
        return storage;
    }

    public PluginConfig getConfig() {
        return config;
    }

    public Config<PluginConfig> getConfigHolder() {
        return configHolder;
    }

    public Path getDataFolder() {
        return dataFolder;
    }
}
