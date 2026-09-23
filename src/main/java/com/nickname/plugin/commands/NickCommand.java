package com.nickname.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.display.NicknameDisplay;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.service.NicknameService;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.ui.NicknameEditorPage;
import com.nickname.plugin.ui.NicknameSettingsPage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

public class NickCommand extends AbstractPlayerCommand {

    public static final String PERM_USE = "nickname.use";
    public static final String PERM_FORMAT = "nickname.format";
    public static final String PERM_ADMIN = "nickname.admin";

    private final NicknameStorage storage;
    private final PluginConfig config;
    private final NicknameDisplay display;
    private final NicknameService service;

    public NickCommand(NicknameStorage storage, PluginConfig config, NicknameDisplay display, NicknameService service) {
        // "nnc" is unique to this plugin; "nick"/"nickname" still work unless another plugin
        // (EliteEssentials, EssentialsPlus, ...) registers a command with that name.
        super("nnc", "Set your display nickname");
        this.storage = storage;
        this.config = config;
        this.display = display;
        this.service = service;
        setAllowsExtraArguments(true);
        addAliases("nick", "nickname");
    }

    @Override
    public boolean hasPermission(@Nonnull CommandSender sender) {
        // Default: allowed. Deny only with explicit "-nickname.use"
        return sender.hasPermission(PERM_USE, true);
    }

    /** Runs on the player's world thread; AbstractPlayerCommand rejects non-player senders. */
    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerUuid = playerRef.getUuid();

        String arg = "";
        String fullInput = context.getInputString();
        if (fullInput != null) {
            String[] parts = fullInput.trim().split("\\s+", 2);
            if (parts.length > 1) {
                arg = parts[1].trim();
            }
        }

        if (arg.isEmpty()) {
            openPage(ref, store, new NicknameEditorPage(storage, service, playerRef));
            return;
        }

        if (arg.equalsIgnoreCase("settings")) {
            if (!PermissionsModule.get().hasPermission(playerUuid, PERM_ADMIN, false)) {
                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_SETTINGS_PERM)).color("#FF5555"));
                return;
            }
            openPage(ref, store, new NicknameSettingsPage(config, display, playerRef));
            return;
        }

        if (arg.equalsIgnoreCase("reset") || arg.equalsIgnoreCase("clear") ||
            arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("remove")) {
            service.resetNickname(ref, store, playerRef);
            return;
        }

        if (arg.split("\\s+", 2)[0].equalsIgnoreCase("msgcolor")) {
            handleMsgColor(playerRef, playerUuid, arg);
            return;
        }

        // Check format permission if nickname contains markup (default: allowed)
        if (MessageUtil.hasMarkup(arg) && !PermissionsModule.get().hasPermission(playerUuid, PERM_FORMAT, true)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_FORMAT_PERM)).color("#FF5555"));
            return;
        }
        service.setNickname(ref, store, playerRef, arg);
    }

    private static void openPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                 @Nonnull CustomUIPage page) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    private void handleMsgColor(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid, @Nonnull String arg) {
        // Check format permission
        if (!PermissionsModule.get().hasPermission(uuid, PERM_FORMAT, true)) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_FORMAT_PERM)).color("#FF5555"));
            return;
        }

        // Parse: "msgcolor #FF5555" or "msgcolor gradient:#FF5555:#5555FF" or "msgcolor reset"
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
            return;
        }

        String value = parts[1].trim();

        if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("clear")) {
            storage.removeMessageColor(uuid);
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_RESET)).color("#55FF55"));
            return;
        }

        // Validate: #RRGGBB or gradient:#HEX1:#HEX2
        if (value.matches("^#[0-9A-Fa-f]{6}$")) {
            // Solid color
            storage.setMessageColor(uuid, value.toUpperCase());
            playerRef.sendMessage(Message.join(
                Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_SET) + " ").color("#55FF55"),
                Message.raw(value).color(value)
            ));
        } else if (value.toLowerCase().startsWith("gradient:")) {
            // gradient:#HEX1:#HEX2
            String[] gradParts = value.split(":");
            if (gradParts.length == 3 && gradParts[1].matches("^#[0-9A-Fa-f]{6}$") && gradParts[2].matches("^#[0-9A-Fa-f]{6}$")) {
                String stored = "gradient:" + gradParts[1].toUpperCase() + ":" + gradParts[2].toUpperCase();
                storage.setMessageColor(uuid, stored);
                playerRef.sendMessage(Message.join(
                    Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_SET) + " ").color("#55FF55"),
                    MessageUtil.parse("<gradient:" + gradParts[1] + ":" + gradParts[2] + ">Example text</gradient>")
                ));
            } else {
                playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
            }
        } else {
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.MSGCOLOR_USAGE)).color("#FFFF55"));
        }
    }
}
