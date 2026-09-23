package com.nickname.plugin.listeners;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.display.NicknameDisplay;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final NicknameDisplay display;

    public PlayerListener(NicknameStorage storage, PluginConfig config, NicknameDisplay display) {
        this.storage = storage;
        this.config = config;
        this.display = display;
    }

    /**
     * Records every player's real username, so nicknames can't copy the name of a known player
     * (online or offline). Warns if a nickname stored earlier equals this newly seen username.
     */
    public void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        storage.rememberUsername(playerRef.getUuid(), playerRef.getUsername());
        for (UUID owner : storage.findNicknameOwners(playerRef.getUsername(), playerRef.getUuid())) {
            LOGGER.at(Level.WARNING).log("Player %s (%s) has the nickname '%s', which is the username of %s (%s). "
                + "Consider resetting that nickname.", storage.getOriginalUsername(owner), owner,
                storage.getNickname(owner), playerRef.getUsername(), playerRef.getUuid());
        }
    }

    /**
     * Fires on the world thread every time the client finishes loading a world (join, portal,
     * instance). The server resets the nameplate on each world add, so it is re-applied here;
     * the tab list is renamed by {@link NicknameDisplay} as the server sends it.
     */
    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        String nickname = storage.getNickname(playerRef.getUuid());
        if (nickname == null) return;

        if (config.display.showOnNameplate) {
            display.applyNameplate(ref, store, playerRef);
        }

        // Greet once per login, not on every world change
        if (event.getReadyId() == 0) {
            playerRef.sendMessage(Message.join(
                Message.raw(Messages.get(playerRef, Messages.WELCOME_NICKNAME) + " ").color("#55FF55"),
                MessageUtil.parse(nickname)
            ));
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.WELCOME_RESET_HINT)).color("#AAAAAA"));
        }
    }
}
