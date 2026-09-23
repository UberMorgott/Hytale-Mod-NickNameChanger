package com.nickname.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.NicknameChanger;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.service.NicknameService;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.util.Permissions;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class NicknameSettingsPage extends InteractiveCustomUIPage<NicknameSettingsPage.EventData> {

    private final PluginConfig config;
    private final NicknameService service;
    private boolean showInChat;
    private boolean showOnNameplate;
    private boolean showInTabList;

    public NicknameSettingsPage(@Nonnull PluginConfig config, @Nonnull NicknameService service, @Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventData.CODEC);
        this.config = config;
        this.service = service;

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
                    // Permissions may have changed since the page was opened
                    if (!Permissions.has(playerRef.getUuid(), NickCommand.PERM_ADMIN, false)) {
                        playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_SETTINGS_PERM)).color("#FF5555"));
                    } else {
                        config.display.showInChat = showInChat;
                        config.display.showOnNameplate = showOnNameplate;
                        config.display.showInTabList = showInTabList;
                        service.reapplyAll();
                        NicknameChanger.getInstance().getConfigHolder().save().whenComplete((ignored, error) -> {
                            if (error == null) {
                                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.SETTINGS_SAVED)).color("#55FF55"));
                            } else {
                                NicknameChanger.getInstance().getLogger().at(Level.SEVERE).withCause(error).log("Failed to save config.json");
                                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.SETTINGS_SAVE_FAILED)).color("#FF5555"));
                            }
                        });
                    }
                    playerComponent.getPageManager().setPage(ref, store, Page.None);
                }
                case "cancel" -> playerComponent.getPageManager().setPage(ref, store, Page.None);
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
