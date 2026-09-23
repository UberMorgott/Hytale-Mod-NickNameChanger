package com.nickname.plugin.listeners;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.util.PlayerRefUtil;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PlayerListener {

    private final NicknameStorage storage;
    private final PluginConfig config;

    public PlayerListener(NicknameStorage storage, PluginConfig config) {
        this.storage = storage;
        this.config = config;
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        if (store == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (playerRef == null) return;

        UUID uuid = playerRef.getUuid();

        String nickname = storage.getNickname(uuid);
        if (nickname != null) {
            String plainName = MessageUtil.stripTags(nickname);

            // Update PlayerRef.username for map markers (breaks other plugins' player lookups)
            if (config.display.showOnMap) {
                PlayerRefUtil.setUsername(playerRef, plainName);
            }

            // Apply nickname to nameplate (above head)
            if (storage.isShowOnNameplate()) {
                updateNameplate(ref, store, nickname);
            }

            // Update player list (inventory header, map, tab)
            if (storage.isShowInTabList()) {
                updatePlayerList(playerRef, nickname);
            }

            playerRef.sendMessage(Message.join(
                Message.raw(Messages.get(playerRef, Messages.WELCOME_NICKNAME) + " ").color("#55FF55"),
                MessageUtil.parse(nickname)
            ));
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.WELCOME_RESET_HINT)).color("#AAAAAA"));
        }
    }

    private void updateNameplate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String displayName) {
        String plainName = MessageUtil.stripTags(displayName);

        // Update Nameplate component (text above head)
        Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
        nameplate.setText(plainName);

        // Update DisplayNameComponent (plain text only — nametag can't render per-char gradient Messages)
        DisplayNameComponent displayNameComponent = new DisplayNameComponent(Message.raw(plainName));
        store.putComponent(ref, DisplayNameComponent.getComponentType(), displayNameComponent);
    }

    private void updatePlayerList(PlayerRef playerRef, String displayName) {
        UUID uuid = playerRef.getUuid();
        UUID worldUuid = playerRef.getWorldUuid();
        String plainName = MessageUtil.stripTags(displayName);

        // Remove player from list
        RemoveFromServerPlayerList removePacket = new RemoveFromServerPlayerList(new UUID[]{uuid});
        Universe.get().broadcastPacket(removePacket);

        // Add player back with new display name
        ServerPlayerListPlayer playerListEntry = PlayerRefUtil.tabListEntry(playerRef, plainName);
        AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[]{playerListEntry});
        Universe.get().broadcastPacket(addPacket);
    }
}
