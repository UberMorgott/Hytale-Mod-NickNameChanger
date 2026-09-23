package com.nickname.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.NicknameChanger;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.util.PlayerRefUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.UUID;

public class NicknameSettingsPage extends InteractiveCustomUIPage<NicknameSettingsPage.EventData> {

    private final NicknameStorage storage;
    private final PluginConfig config;
    private boolean showInChat;
    private boolean showOnNameplate;
    private boolean showInTabList;

    public NicknameSettingsPage(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config, @Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventData.CODEC);
        this.storage = storage;
        this.config = config;

        this.showInChat = config.display.showInChat;
        this.showOnNameplate = config.display.showOnNameplate;
        this.showInTabList = config.display.showInTabList;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/nickname_settings.ui");

        commandBuilder.set("#TitleText.Text", Messages.get(playerRef, Messages.UI_SETTINGS_TITLE));

        commandBuilder.set("#ChatCheckbox #CheckBox.Value", showInChat);
        commandBuilder.set("#NameplateCheckbox #CheckBox.Value", showOnNameplate);
        commandBuilder.set("#TabListCheckbox #CheckBox.Value", showInTabList);

        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ChatCheckbox #CheckBox",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "toggle_chat").append("@Checked", "#ChatCheckbox #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NameplateCheckbox #CheckBox",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "toggle_nameplate").append("@Checked", "#NameplateCheckbox #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TabListCheckbox #CheckBox",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "toggle_tablist").append("@Checked", "#TabListCheckbox #CheckBox.Value"), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "save"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "cancel"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventData data) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());

        if (data.action != null) {
            switch (data.action) {
                case "toggle_chat" -> showInChat = data.checked;
                case "toggle_nameplate" -> showOnNameplate = data.checked;
                case "toggle_tablist" -> showInTabList = data.checked;
                case "save" -> {
                    config.display.showInChat = showInChat;
                    config.display.showOnNameplate = showOnNameplate;
                    config.display.showInTabList = showInTabList;
                    NicknameChanger.getInstance().getConfigHolder().save();

                    applyToAllPlayers();

                    playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.SETTINGS_SAVED)).color("#55FF55"));
                    playerComponent.getPageManager().setPage(ref, store, Page.None);
                    return;
                }
                case "cancel" -> {
                    playerComponent.getPageManager().setPage(ref, store, Page.None);
                    return;
                }
            }
        }
    }

    private void applyToAllPlayers() {
        Collection<PlayerRef> players = Universe.get().getPlayers();

        for (PlayerRef pr : players) {
            UUID uuid = pr.getUuid();
            String nickname = storage.getNickname(uuid);
            if (nickname == null) continue;

            String plainName = MessageUtil.stripTags(nickname);
            String orig = storage.getOriginalUsername(uuid);
            String originalName = orig != null ? orig : pr.getUsername();

            Ref<EntityStore> playerEntityRef = pr.getReference();
            if (playerEntityRef == null || !playerEntityRef.isValid()) continue;
            Store<EntityStore> playerStore = playerEntityRef.getStore();
            if (playerStore == null) continue;
            EntityStore externalData = (EntityStore) playerStore.getExternalData();
            if (externalData == null) continue;
            World playerWorld = externalData.getWorld();
            if (playerWorld == null) continue;

            // Schedule store access on the player's own world thread to avoid
            // IllegalStateException when players are in different worlds
            playerWorld.execute(() -> {
                if (!playerEntityRef.isValid()) return;

                // Nameplate
                Nameplate nameplate = playerStore.ensureAndGetComponent(playerEntityRef, Nameplate.getComponentType());
                if (showOnNameplate) {
                    nameplate.setText(plainName);
                    playerStore.putComponent(playerEntityRef, DisplayNameComponent.getComponentType(),
                        new DisplayNameComponent(Message.raw(plainName)));
                } else {
                    nameplate.setText(originalName);
                    playerStore.removeComponentIfExists(playerEntityRef, DisplayNameComponent.getComponentType());
                }
            });

            // Tab list (broadcastPacket is thread-safe, doesn't touch ECS store)
            RemoveFromServerPlayerList removePacket = new RemoveFromServerPlayerList(new UUID[]{uuid});
            Universe.get().broadcastPacket(removePacket);

            String tabName = showInTabList ? plainName : originalName;
            ServerPlayerListPlayer entry = PlayerRefUtil.tabListEntry(pr, tabName);
            AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[]{entry});
            Universe.get().broadcastPacket(addPacket);

            // PlayerRef.username (for map markers only)
            if (config.display.showOnMap) {
                if (showInTabList || showOnNameplate) {
                    PlayerRefUtil.setUsername(pr, plainName);
                } else {
                    PlayerRefUtil.setUsername(pr, originalName);
                }
            }
        }
    }

    public static class EventData {
        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (e, b) -> e.checked = b, e -> e.checked).add()
            .build();

        public String action;
        public boolean checked;

        public EventData() {}
    }
}
