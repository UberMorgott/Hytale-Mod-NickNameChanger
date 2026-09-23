package com.nickname.plugin.validation;

import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.i18n.Messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Nickname rules from {@code config.json → Nicknames}, shared by the command and the editor.
 * Invalid input is rejected with a reason instead of being silently altered.
 * Uniqueness is checked by the storage when the nickname is saved.
 */
public final class NicknameValidator {

    /** Upper bound for the raw input (tags included) before any parsing. */
    private static final int MAX_INPUT_LENGTH = 512;
    private static final Set<String> STYLE_TAGS = Set.of("b", "bold", "i", "italic", "u", "underline");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    /** Markup characters of NNC and other chat plugins (MiniMessage, legacy &, PlaceholderAPI, HyperPerms). */
    private static final String MARKUP_CHARS = "<>&§%{}\\";

    private final PluginConfig.NicknameRules rules;
    private final int minLength;
    private final int maxLength;
    @Nullable
    private final Pattern allowedCharacters;
    private final boolean configValid;

    /** Validated nickname: {@code nickname} keeps the formatting tags, {@code plain} is the visible text. */
    public record Result(@Nullable String nickname, @Nullable String plain, @Nullable String errorKey, Object[] args) {
        public boolean isValid() {
            return errorKey == null;
        }

        static Result error(String key, Object... args) {
            return new Result(null, null, key, args);
        }
    }

    /**
     * Invalid settings don't fall back to something more permissive: the validator then refuses
     * every nickname (fail closed) until the config is fixed.
     *
     * @param configError receives a message for each invalid setting
     */
    public NicknameValidator(@Nonnull PluginConfig.NicknameRules rules, @Nonnull Consumer<String> configError) {
        this.rules = rules;
        this.minLength = rules.minLength;
        this.maxLength = rules.maxLength;
        Pattern pattern = null;
        String problem = null;
        if (minLength < 1 || maxLength < minLength) {
            problem = "Invalid Nicknames.MinLength/MaxLength (" + minLength + "/" + maxLength + ")";
        }
        String regex = rules.allowedCharactersRegex;
        if (regex != null && !regex.isEmpty()) {
            try {
                pattern = Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                problem = "Invalid Nicknames.AllowedCharactersRegex '" + regex + "': " + e.getDescription();
            }
        }
        this.allowedCharacters = pattern;
        this.configValid = problem == null;
        if (problem != null) {
            configError.accept(problem + ". Nobody can set a nickname until this is fixed in config.json.");
        }
    }
    @Nonnull
    public Result validate(@Nonnull String input) {
        if (!configValid) {
            return Result.error(Messages.ERROR_RULES_INVALID);
        }
        String nickname = Normalizer.normalize(input.trim(), Normalizer.Form.NFC);
        if (nickname.isEmpty() || nickname.length() > MAX_INPUT_LENGTH) {
            return Result.error(Messages.ERROR_INVALID);
        }

        String plain = visibleText(nickname);
        if (plain == null) {
            return Result.error(Messages.ERROR_INVALID);
        }
        plain = plain.strip();

        int length = plain.codePointCount(0, plain.length());
        if (length < minLength) {
            return Result.error(Messages.ERROR_MIN_LENGTH, "min", minLength);
        }
        if (length > maxLength) {
            return Result.error(Messages.ERROR_MAX_LENGTH, "max", maxLength);
        }

        if (!plain.codePoints().allMatch(this::isAllowedCodePoint)
                || (allowedCharacters != null && !allowedCharacters.matcher(plain).matches())) {
            return Result.error(Messages.ERROR_INVALID_CHARACTERS);
        }

        String lower = plain.toLowerCase(Locale.ROOT);
        for (String banned : rules.bannedWords) {
            if (banned != null && !banned.isBlank() && lower.contains(banned.strip().toLowerCase(Locale.ROOT))) {
                return Result.error(Messages.ERROR_BANNED_WORD);
            }
        }
        return new Result(nickname, plain, null, new Object[0]);
    }

    /** Same comparison key the storage uses for uniqueness. */
    @Nonnull
    public static String canonical(@Nonnull String plainName) {
        return Normalizer.normalize(plainName.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedCodePoint(int cp) {
        if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT || MARKUP_CHARS.indexOf(cp) >= 0) {
            return false; // control, bidi/zero-width characters and markup are never allowed
        }
        if (allowedCharacters != null) return true; // the regex decides
        if (cp == ' ' || cp == '_' || cp == '-' || cp == '.' || cp == '!' || cp == '?') return true;
        if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9')) return true;
        if (rules.allowCyrillic && Character.UnicodeBlock.of(cp) == Character.UnicodeBlock.CYRILLIC) return true;
        return rules.allowUnicode && Character.isLetterOrDigit(cp);
    }

    /**
     * Returns the text without formatting tags, or {@code null} if the nickname uses a tag
     * other than {@code <color:#RRGGBB>}, {@code <gradient:#RRGGBB:#RRGGBB...>}, b/bold, i/italic, u/underline.
     * A lone {@code <} stays in the text (and is then rejected as a markup character).
     */
    @Nullable
    private static String visibleText(@Nonnull String nickname) {
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < nickname.length()) {
            char c = nickname.charAt(i);
            int end = c == '<' ? nickname.indexOf('>', i) : -1;
            if (end < 0) {
                text.append(c);
                i++;
                continue;
            }
            if (!isAllowedTag(nickname.substring(i + 1, end))) {
                return null;
            }
            i = end + 1;
        }
        return text.toString();
    }

    private static boolean isAllowedTag(@Nonnull String tag) {
        String name = tag.toLowerCase(Locale.ROOT);
        if (name.startsWith("/")) {
            name = name.substring(1);
            return STYLE_TAGS.contains(name) || name.equals("color") || name.equals("gradient");
        }
        if (STYLE_TAGS.contains(name)) return true;
        String[] parts = name.split(":", -1);
        if (parts[0].equals("color")) {
            return parts.length == 2 && HEX_COLOR.matcher(parts[1]).matches();
        }
        if (parts[0].equals("gradient")) {
            if (parts.length < 3) return false;
            for (int p = 1; p < parts.length; p++) {
                if (!HEX_COLOR.matcher(parts[p]).matches()) return false;
            }
            return true;
        }
        return false;
    }
}
