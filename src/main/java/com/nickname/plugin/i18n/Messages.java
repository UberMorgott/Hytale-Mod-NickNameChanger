package com.nickname.plugin.i18n;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class Messages {

    private static final String PREFIX = "nickname.";

    // Errors
    public static final String ERROR_MIN_LENGTH = PREFIX + "error.minLength";
    public static final String ERROR_MAX_LENGTH = PREFIX + "error.maxLength";
    public static final String ERROR_INVALID = PREFIX + "error.invalid";
    public static final String ERROR_NO_FORMAT_PERM = PREFIX + "error.noFormatPermission";
    public static final String ERROR_NO_MSGCOLOR_PERM = PREFIX + "error.noMsgColorPermission";
    public static final String ERROR_BANNED_WORD = PREFIX + "error.bannedWord";
    public static final String ERROR_NICKNAME_TAKEN = PREFIX + "error.nicknameTaken";
    public static final String ERROR_INVALID_CHARACTERS = PREFIX + "error.invalidCharacters";
    public static final String ERROR_REAL_USERNAME = PREFIX + "error.realUsername";
    public static final String ERROR_NOT_SAVED = PREFIX + "error.notSaved";
    public static final String ERROR_NO_SETTINGS_PERM = PREFIX + "error.noSettingsPermission";

    // Nickname
    public static final String RESET_SUCCESS = PREFIX + "reset.success";
    public static final String RESET_NO_NICKNAME = PREFIX + "reset.noNickname";
    public static final String SET_SUCCESS = PREFIX + "set.success";
    public static final String WELCOME_NICKNAME = PREFIX + "welcome.nickname";
    public static final String WELCOME_RESET_HINT = PREFIX + "welcome.resetHint";

    // Message color
    public static final String MSGCOLOR_SET = PREFIX + "msgcolor.set";
    public static final String MSGCOLOR_RESET = PREFIX + "msgcolor.reset";
    public static final String MSGCOLOR_USAGE = PREFIX + "msgcolor.usage";

    // UI
    public static final String UI_TITLE = PREFIX + "ui.title";
    public static final String UI_TAB_COLOR = PREFIX + "ui.tabColor";
    public static final String UI_TAB_GRADIENT = PREFIX + "ui.tabGradient";
    public static final String UI_TAB_MESSAGE = PREFIX + "ui.tabMessage";

    // Settings
    public static final String UI_SETTINGS_TITLE = PREFIX + "ui.settings.title";
    public static final String SETTINGS_SAVED = PREFIX + "settings.saved";
    public static final String SETTINGS_SAVE_FAILED = PREFIX + "settings.saveFailed";

    private Messages() {}

    @Nonnull
    public static String get(@Nonnull PlayerRef playerRef, @Nonnull String key) {
        return get(playerRef.getLanguage(), key);
    }

    @Nonnull
    public static String get(@Nullable String language, @Nonnull String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            return key;
        }
        String message = i18n.getMessage(language, key);
        return message != null ? message : key;
    }

    @Nonnull
    public static String get(@Nonnull PlayerRef playerRef, @Nonnull String key, Object... placeholders) {
        return get(playerRef.getLanguage(), key, placeholders);
    }

    @Nonnull
    public static String get(@Nullable String language, @Nonnull String key, Object... placeholders) {
        String message = get(language, key);

        if (placeholders != null && placeholders.length >= 2) {
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                String placeholder = "{" + placeholders[i] + "}";
                String value = String.valueOf(placeholders[i + 1]);
                message = message.replace(placeholder, value);
            }
        }

        return message;
    }
}
