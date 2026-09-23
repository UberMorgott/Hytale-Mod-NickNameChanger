package com.nickname.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;

import com.nickname.plugin.api.NicknameAPI;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.compat.EssentialsPlusCompat;
import com.nickname.plugin.compat.MiniChatFormatterCompat;
import com.nickname.plugin.config.ConfigMigration;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.hooks.LuckPermsHook;
import com.nickname.plugin.listeners.ChatListener;
import com.nickname.plugin.listeners.PlayerListener;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.PlayerRefUtil;

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
    private ChatListener chatListener;
    private PlayerListener playerListener;
    private MiniChatFormatterCompat mcfCompat;
    private EssentialsPlusCompat epCompat;

    public NicknameChanger(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static NicknameChanger getInstance() {
        return instance;
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        // Must run before the config is loaded: the codec silently drops legacy camelCase keys
        try {
            if (ConfigMigration.migrate(getDataDirectory().resolve("config.json"))) {
                getLogger().at(Level.INFO).log("Migrated config.json to the current key format (backup: config.json.pre-0.0.18.bak).");
            }
        } catch (IOException | RuntimeException e) {
            // Leave the file as it is; the regular config loader reports what is wrong with it
            getLogger().at(Level.SEVERE).withCause(e).log("Could not migrate config.json");
        }
        return super.preLoad();
    }

    @Override
    protected void setup() {
        this.dataFolder = getDataDirectory();
        this.config = configHolder.get();
        // Rewrite the file so it always lists every current option with its effective value
        config.version = getManifest().getVersion().toString();
        configHolder.save();
        PlayerRefUtil.init();

        this.storage = new NicknameStorage(dataFolder, config);
        NicknameAPI.init(storage);

        this.mcfCompat = new MiniChatFormatterCompat();
        this.epCompat = new EssentialsPlusCompat();
        this.chatListener = new ChatListener(storage, config, mcfCompat, epCompat);
        this.playerListener = new PlayerListener(storage, config);

        getCommandRegistry().registerCommand(new NickCommand(storage, config));
        // Two handlers for chat formatting:
        // FIRST: set plain nickname into PlayerRef.username BEFORE external formatters (EP, MCF) read it
        // LATE: restore original username, then either defer to external formatters or apply own formatting
        getEventRegistry().registerGlobal(EventPriority.FIRST, PlayerChatEvent.class, chatListener::onPlayerChatEarly);
        getEventRegistry().registerGlobal(EventPriority.LATE, PlayerChatEvent.class, chatListener::onPlayerChat);
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, playerListener::onPlayerReady);
    }

    @Override
    protected void start() {
        // Initialize optional integrations after all plugins are enabled
        try {
            LuckPermsHook.init(config.integrations.luckperms.enabled);
        } catch (NoClassDefFoundError e) {
            getLogger().at(Level.INFO).log("LuckPerms not found, running without it.");
        }

        // Eagerly detect external formatters so ChatListener knows from the first chat event
        mcfCompat.detect();
        epCompat.detect();
    }

    @Override
    protected void shutdown() {
        // Plugin disabled
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
