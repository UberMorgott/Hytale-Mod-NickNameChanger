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
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.util.MessageUtil;
import com.nickname.plugin.i18n.Messages;
import com.nickname.plugin.service.NicknameService;
import com.nickname.plugin.storage.NicknameStorage;

import javax.annotation.Nonnull;
import java.util.UUID;

public class NicknameEditorPage extends InteractiveCustomUIPage<NicknameEditorPage.EventData> {

    private final NicknameStorage storage;
    private final NicknameService service;
    private String currentNickname;
    private String currentColor = "";
    private boolean isBold = false;
    private boolean isItalic = false;
    private boolean isUnderline = false;
    private boolean isGradient = false;
    private boolean isMessage = false;
    private String nickColorMode = "none"; // "none", "color", "gradient"
    private String gradColor1 = "#FF5555";
    private String gradColor2 = "#5555FF";
    private String msgColor = "";

    public NicknameEditorPage(@Nonnull NicknameStorage storage, @Nonnull NicknameService service, @Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventData.CODEC);
        this.storage = storage;
        this.service = service;

        String existingNick = storage.getNickname(playerRef.getUuid());
        if (existingNick != null) {
            parseExistingNickname(existingNick);
        } else {
            this.currentNickname = playerRef.getUsername();
        }

        // Load existing message color
        String existingMsgColor = storage.getMessageColor(playerRef.getUuid());
        if (existingMsgColor != null) {
            this.msgColor = existingMsgColor;
        }
    }

    private void parseExistingNickname(String formatted) {
        this.isBold = formatted.contains("<b>") || formatted.contains("<bold>");
        this.isItalic = formatted.contains("<i>") || formatted.contains("<italic>");
        this.isUnderline = formatted.contains("<u>") || formatted.contains("<underline>");

        if (formatted.contains("<gradient:")) {
            this.isGradient = true;
            this.nickColorMode = "gradient";
            int start = formatted.indexOf("<gradient:") + 10;
            int end = formatted.indexOf(">", start);
            if (end > start) {
                String[] colors = formatted.substring(start, end).split(":");
                if (colors.length >= 2) {
                    this.gradColor1 = colors[0];
                    this.gradColor2 = colors[1];
                }
            }
        } else if (formatted.contains("<color:")) {
            this.nickColorMode = "color";
            int start = formatted.indexOf("<color:") + 7;
            int end = formatted.indexOf(">", start);
            if (end > start) {
                this.currentColor = formatted.substring(start, end);
            }
        } else {
            this.nickColorMode = "none";
        }

        this.currentNickname = MessageUtil.stripTags(formatted);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("Pages/nickname_editor.ui");

        // Set title (programmatically to support localized fonts)
        commandBuilder.set("#TitleText.Text", Messages.get(playerRef, Messages.UI_TITLE));

        // Set initial values
        commandBuilder.set("#NicknameInput.Value", currentNickname);
        commandBuilder.set("#ColorPicker.Value", toPickerValue(currentColor.isEmpty() ? "#FFFFFF" : currentColor));
        commandBuilder.set("#GradColorPicker1.Value", toPickerValue(gradColor1));
        commandBuilder.set("#GradColorPicker2.Value", toPickerValue(gradColor2));
        commandBuilder.set("#GradColor1Preview.Background", gradColor1);
        commandBuilder.set("#GradColor2Preview.Background", gradColor2);

        // Set checkbox states
        commandBuilder.set("#BoldCheckbox #CheckBox.Value", isBold);
        commandBuilder.set("#ItalicCheckbox #CheckBox.Value", isItalic);
        commandBuilder.set("#UnderlineCheckbox #CheckBox.Value", isUnderline);

        // Set message color picker
        commandBuilder.set("#MsgColorPicker.Value", toPickerValue(msgColor.isEmpty() ? "#FFFFFF" : msgColor));

        // Set tab states and visibility
        updateTabStyles(commandBuilder);
        updateSectionVisibility(commandBuilder);
        updatePreview(commandBuilder);

        // Nickname input
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#NicknameInput",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "nickname_changed").append("@Nickname", "#NicknameInput.Value"), false);

        // Style checkboxes
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#BoldCheckbox #CheckBox",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "toggle_bold").append("@Checked", "#BoldCheckbox #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ItalicCheckbox #CheckBox",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "toggle_italic").append("@Checked", "#ItalicCheckbox #CheckBox.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#UnderlineCheckbox #CheckBox",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "toggle_underline").append("@Checked", "#UnderlineCheckbox #CheckBox.Value"), false);

        // Tab buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabColor",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab_color"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabGradient",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab_gradient"), false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TabMessage",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab_message"), false);

        // Message color preset buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorRed",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#FF5555"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorOrange",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#FFAA00"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorYellow",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#FFFF55"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorGreen",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#55FF55"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorAqua",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#55FFFF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorBlue",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#5555FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorPurple",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#AA55FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorPink",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#FF55FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorWhite",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#FFFFFF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorGold",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#FFD700"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorGray",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", "#AAAAAA"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MsgColorNone",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color").append("Color", ""), false);

        // Message color picker
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#MsgColorPicker",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "msg_color_picker").append("@PickerColor", "#MsgColorPicker.Value"), false);

        // Main action buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "cancel"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "apply").append("@Nickname", "#NicknameInput.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ResetBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "reset"), false);

        // Color preset buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorRed",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#FF5555"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorOrange",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#FFAA00"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorYellow",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#FFFF55"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorGreen",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#55FF55"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorAqua",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#55FFFF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorBlue",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#5555FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorPurple",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#AA55FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorPink",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#FF55FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorWhite",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#FFFFFF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorGray",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#AAAAAA"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorGold",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", "#FFD700"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ColorNone",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color").append("Color", ""), false);

        // ColorPicker value change
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ColorPicker",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "color_picker").append("@PickerColor", "#ColorPicker.Value"), false);

        // Gradient presets (8 total)
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset1",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#FF5555").append("G2", "#FFAA00"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset2",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#55FFFF").append("G2", "#5555FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset3",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#FF5555").append("G2", "#FF0000"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset4",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#55FF55").append("G2", "#00AA00"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset5",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#AA55FF").append("G2", "#FF55FF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset6",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#FFD700").append("G2", "#FFA500"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset7",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#AAFFFF").append("G2", "#55AAFF"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GradPreset8",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_preset").append("G1", "#FF0000").append("G2", "#FF00FF"), false);

        // Gradient ColorPickers value change
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GradColorPicker1",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_color1").append("@G1", "#GradColorPicker1.Value"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GradColorPicker2",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "grad_color2").append("@G2", "#GradColorPicker2.Value"), false);
    }

    private void updateTabStyles(@Nonnull UICommandBuilder commandBuilder) {
        boolean isColor = !isGradient && !isMessage;

        // Toggle highlight bars under tabs
        commandBuilder.set("#TabColorHighlight.Visible", isColor);
        commandBuilder.set("#TabGradientHighlight.Visible", isGradient);
        commandBuilder.set("#TabMessageHighlight.Visible", isMessage);

        commandBuilder.set("#TabColor.Text", Messages.get(playerRef, Messages.UI_TAB_COLOR));
        commandBuilder.set("#TabGradient.Text", Messages.get(playerRef, Messages.UI_TAB_GRADIENT));
        commandBuilder.set("#TabMessage.Text", Messages.get(playerRef, Messages.UI_TAB_MESSAGE));
    }

    private void updateSectionVisibility(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set("#SingleColorSection.Visible", !isGradient && !isMessage);
        commandBuilder.set("#GradientSection.Visible", isGradient);
        commandBuilder.set("#MessageSection.Visible", isMessage);
    }

    private void updatePreview(@Nonnull UICommandBuilder commandBuilder) {
        String previewText = buildFormattedNickname();
        commandBuilder.set("#PreviewText.TextSpans", MessageUtil.parseForUI(previewText));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventData data) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());

        // Update nickname if provided
        if (data.nickname != null && !data.nickname.isEmpty()) {
            currentNickname = data.nickname;
        }

        if (data.action != null) {
            switch (data.action) {
                case "nickname_changed" -> {
                    // Just update preview, nickname already set above
                }
                case "apply" -> {
                    if (applyNickname(ref, store)) {
                        playerComponent.getPageManager().setPage(ref, store, Page.None);
                    }
                    return;
                }
                case "reset" -> {
                    // Reset settings without closing - clear all formatting and reset nickname in storage
                    currentNickname = playerRef.getUsername();
                    currentColor = "";
                    nickColorMode = "none";
                    isBold = false;
                    isItalic = false;
                    isUnderline = false;
                    isGradient = false;
                    isMessage = false;
                    gradColor1 = "#FF5555";
                    gradColor2 = "#5555FF";
                    msgColor = "";
                    service.resetNickname(ref, store, playerRef);
                }
                case "cancel" -> {
                    playerComponent.getPageManager().setPage(ref, store, Page.None);
                    return;
                }
                case "tab_color" -> {
                    isGradient = false;
                    isMessage = false;
                }
                case "tab_gradient" -> {
                    isGradient = true;
                    isMessage = false;
                    currentColor = "";
                }
                case "tab_message" -> {
                    isGradient = false;
                    isMessage = true;
                }
                case "msg_color" -> {
                    if (isPresetColor(data.color)) {
                        msgColor = data.color;
                    }
                }
                case "msg_color_picker" -> {
                    if (data.pickerColor != null && isValidHexColor(data.pickerColor)) {
                        msgColor = normalizeHexColor(data.pickerColor);
                    }
                }
                case "toggle_bold" -> isBold = data.checked;
                case "toggle_italic" -> isItalic = data.checked;
                case "toggle_underline" -> isUnderline = data.checked;
                case "color" -> {
                    if (isPresetColor(data.color)) {
                        currentColor = data.color;
                        nickColorMode = data.color.isEmpty() ? "none" : "color";
                    }
                }
                case "color_picker" -> {
                    if (data.pickerColor != null && isValidHexColor(data.pickerColor)) {
                        currentColor = normalizeHexColor(data.pickerColor);
                        nickColorMode = "color";
                    }
                }
                case "grad_preset" -> {
                    if (isPresetColor(data.g1) && !data.g1.isEmpty()) gradColor1 = data.g1;
                    if (isPresetColor(data.g2) && !data.g2.isEmpty()) gradColor2 = data.g2;
                    nickColorMode = "gradient";
                }
                case "grad_color1" -> {
                    if (data.g1 != null && isValidHexColor(data.g1)) {
                        gradColor1 = normalizeHexColor(data.g1);
                        nickColorMode = "gradient";
                    }
                }
                case "grad_color2" -> {
                    if (data.g2 != null && isValidHexColor(data.g2)) {
                        gradColor2 = normalizeHexColor(data.g2);
                        nickColorMode = "gradient";
                    }
                }
            }
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        updateTabStyles(commandBuilder);
        updateSectionVisibility(commandBuilder);
        updatePreview(commandBuilder);

        // Update gradient color previews and pickers
        if ("grad_preset".equals(data.action) || "grad_color1".equals(data.action) || "grad_color2".equals(data.action)) {
            commandBuilder.set("#GradColor1Preview.Background", gradColor1);
            commandBuilder.set("#GradColor2Preview.Background", gradColor2);
        }
        if ("grad_preset".equals(data.action)) {
            commandBuilder.set("#GradColorPicker1.Value", toPickerValue(gradColor1));
            commandBuilder.set("#GradColorPicker2.Value", toPickerValue(gradColor2));
        }

        // Update single color picker when preset selected
        if ("color".equals(data.action) && !currentColor.isEmpty()) {
            commandBuilder.set("#ColorPicker.Value", toPickerValue(currentColor));
        }

        // Update message color picker when preset selected
        if ("msg_color".equals(data.action) && !msgColor.isEmpty()) {
            commandBuilder.set("#MsgColorPicker.Value", toPickerValue(msgColor));
        }

        // Reset all UI elements when reset button pressed
        if ("reset".equals(data.action)) {
            commandBuilder.set("#NicknameInput.Value", currentNickname);
            commandBuilder.set("#ColorPicker.Value", toPickerValue("#FFFFFF"));
            commandBuilder.set("#GradColorPicker1.Value", toPickerValue(gradColor1));
            commandBuilder.set("#GradColorPicker2.Value", toPickerValue(gradColor2));
            commandBuilder.set("#GradColor1Preview.Background", gradColor1);
            commandBuilder.set("#GradColor2Preview.Background", gradColor2);
            commandBuilder.set("#BoldCheckbox #CheckBox.Value", false);
            commandBuilder.set("#ItalicCheckbox #CheckBox.Value", false);
            commandBuilder.set("#UnderlineCheckbox #CheckBox.Value", false);
            commandBuilder.set("#MsgColorPicker.Value", toPickerValue(msgColor.isEmpty() ? "#FFFFFF" : msgColor));
        }

        sendUpdate(commandBuilder);
    }

    /** Preset colors come from the client: accept only "#RRGGBB" or "" (none). */
    private static boolean isPresetColor(String color) {
        return color != null && (color.isEmpty() || color.matches("^#[0-9A-Fa-f]{6}$"));
    }

    private boolean isValidHexColor(String color) {
        if (color == null || color.isEmpty()) return false;
        // Support 6 or 8 character hex (with or without alpha)
        return color.matches("^#?[0-9A-Fa-f]{6,8}$");
    }

    /**
     * Convert a 6-char hex color (#RRGGBB) to 8-char ColorPicker format (#RRGGBBVV).
     * The ColorPicker uses an inverted brightness byte: 0x00 = full brightness, 0xFF = black.
     * Appending "00" ensures the brightness slider is at max when setting a preset color.
     */
    private String toPickerValue(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) return hexColor;
        // Only append brightness byte if it's a standard 6-char hex (7 with #)
        if (hexColor.length() == 7 && hexColor.startsWith("#")) {
            return hexColor + "00";
        }
        return hexColor;
    }

    private String normalizeHexColor(String color) {
        if (color == null || color.isEmpty()) return "";
        if (!color.startsWith("#")) {
            color = "#" + color;
        }
        // Handle 8-character hex colors: #RRGGBBVV where VV is brightness/value
        // ColorPicker returns base color + brightness byte
        // We need to apply the brightness to the RGB values
        if (color.length() == 9) {
            int r = Integer.parseInt(color.substring(1, 3), 16);
            int g = Integer.parseInt(color.substring(3, 5), 16);
            int b = Integer.parseInt(color.substring(5, 7), 16);
            int brightness = Integer.parseInt(color.substring(7, 9), 16);

            // Brightness is inverted: 0xFF = dark, 0x00 = full color
            // So we invert it: scale = (255 - brightness) / 255
            float scale = (255 - brightness) / 255.0f;
            r = Math.min(255, Math.round(r * scale));
            g = Math.min(255, Math.round(g * scale));
            b = Math.min(255, Math.round(b * scale));

            color = String.format("#%02X%02X%02X", r, g, b);
        }
        return color.toUpperCase();
    }

    private String buildFormattedNickname() {
        StringBuilder sb = new StringBuilder();

        if ("gradient".equals(nickColorMode)) {
            sb.append("<gradient:").append(gradColor1).append(":").append(gradColor2).append(">");
        } else if ("color".equals(nickColorMode) && !currentColor.isEmpty()) {
            sb.append("<color:").append(currentColor).append(">");
        }

        if (isBold) sb.append("<b>");
        if (isItalic) sb.append("<i>");
        if (isUnderline) sb.append("<u>");

        sb.append(currentNickname);

        if (isUnderline) sb.append("</u>");
        if (isItalic) sb.append("</i>");
        if (isBold) sb.append("</b>");

        if ("gradient".equals(nickColorMode)) {
            sb.append("</gradient>");
        } else if ("color".equals(nickColorMode) && !currentColor.isEmpty()) {
            sb.append("</color>");
        }

        return sb.toString();
    }

    private boolean hasFormatting() {
        return !currentColor.isEmpty() || "gradient".equals(nickColorMode) || isBold || isItalic || isUnderline;
    }

    private boolean applyNickname(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID uuid = playerRef.getUuid();

        // Strip formatting if player lacks nickname.format permission
        String formattedNickname;
        if (hasFormatting() && !PermissionsModule.get().hasPermission(uuid, NickCommand.PERM_FORMAT, true)) {
            formattedNickname = currentNickname.trim();
            playerRef.sendMessage(Message.raw(Messages.get(playerRef, Messages.ERROR_NO_FORMAT_PERM)).color("#FF5555"));
        } else {
            formattedNickname = buildFormattedNickname();
        }

        if (!service.setNickname(ref, store, playerRef, formattedNickname)) {
            return false;
        }

        // Message color only if it was changed (setting it is checked against nickname.msgcolor)
        String storedColor = storage.getMessageColor(uuid);
        if (!msgColor.equals(storedColor == null ? "" : storedColor)) {
            service.setMessageColor(playerRef, msgColor);
        }
        return true;
    }
    public static class EventData {
        public static final BuilderCodec<EventData> CODEC = BuilderCodec.builder(EventData.class, EventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
            .append(new KeyedCodec<>("@Nickname", Codec.STRING), (e, s) -> e.nickname = s, e -> e.nickname).add()
            .append(new KeyedCodec<>("Color", Codec.STRING), (e, s) -> e.color = s, e -> e.color).add()
            .append(new KeyedCodec<>("@Checked", Codec.BOOLEAN), (e, b) -> e.checked = b, e -> e.checked).add()
            .append(new KeyedCodec<>("@PickerColor", Codec.STRING), (e, s) -> e.pickerColor = s, e -> e.pickerColor).add()
            .append(new KeyedCodec<>("G1", Codec.STRING), (e, s) -> e.g1 = s, e -> e.g1).add()
            .append(new KeyedCodec<>("G2", Codec.STRING), (e, s) -> e.g2 = s, e -> e.g2).add()
            .append(new KeyedCodec<>("@G1", Codec.STRING), (e, s) -> e.g1 = s, e -> e.g1).add()
            .append(new KeyedCodec<>("@G2", Codec.STRING), (e, s) -> e.g2 = s, e -> e.g2).add()
            .build();

        public String action;
        public String nickname;
        public String color;
        public boolean checked;
        public String pickerColor;
        public String g1;
        public String g2;

        public EventData() {}
    }
}
