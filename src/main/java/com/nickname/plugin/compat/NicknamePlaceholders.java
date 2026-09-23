package com.nickname.plugin.compat;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.nickname.plugin.commands.NickCommand;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Values of the {@code %nnc_...%} placeholders, independent of any placeholder plugin:
 * <ul>
 *   <li>{@code nickname} – nickname as plain text, or the real name</li>
 *   <li>{@code nickname_mini} – with colors as MiniMessage / EssentialsPlus tags</li>
 *   <li>{@code nickname_legacy} – with colors as {@code &#RRGGBB} / {@code &l} codes</li>
 *   <li>{@code has_nickname} – true / false</li>
 *   <li>{@code realname} – the real username</li>
 *   <li>{@code msgcolor_open}, {@code msgcolor_close} – MiniMessage tags to put around the message</li>
 *   <li>{@code msgcolor_legacy} – {@code &#RRGGBB} message color (first color of a gradient)</li>
 * </ul>
 */
public final class NicknamePlaceholders {

    private final NicknameStorage storage;

    public NicknamePlaceholders(@Nonnull NicknameStorage storage) {
        this.storage = storage;
    }

    /** Returns the value, or {@code null} for an unknown placeholder. */
    @Nullable
    public String resolve(@Nonnull UUID uuid, @Nonnull String realName, @Nonnull String name) {
        String nickname = storage.getNickname(uuid);
        String styled = nickname != null ? nickname : realName;
        return switch (name) {
            case "nickname" -> MessageUtil.stripTags(styled);
            case "nickname_mini" -> MessageUtil.toMiniMessage(styled);
            case "nickname_legacy" -> MessageUtil.toLegacy(styled);
            case "has_nickname" -> String.valueOf(nickname != null);
            case "realname" -> realName;
            case "msgcolor_open" -> messageColorTag(uuid, false);
            case "msgcolor_close" -> messageColorTag(uuid, true);
            case "msgcolor_legacy" -> {
                String color = messageColor(uuid);
                yield color == null ? "" : "&" + (color.startsWith("gradient:") ? color.split(":")[1] : color);
            }
            default -> null;
        };
    }

    @Nullable
    private String messageColor(@Nonnull UUID uuid) {
        if (!PermissionsModule.get().hasPermission(uuid, NickCommand.PERM_MSGCOLOR, true)) return null;
        return storage.getMessageColor(uuid);
    }

    @Nonnull
    private String messageColorTag(@Nonnull UUID uuid, boolean close) {
        String color = messageColor(uuid);
        if (color == null) return "";
        if (color.startsWith("gradient:")) {
            String[] parts = color.split(":");
            return close ? "</gradient>" : "<gradient:" + parts[1] + ":" + parts[2] + ">";
        }
        return close ? "</" + color + ">" : "<" + color + ">";
    }
}
