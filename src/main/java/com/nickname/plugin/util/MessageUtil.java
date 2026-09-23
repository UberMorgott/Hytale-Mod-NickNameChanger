package com.nickname.plugin.util;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markup used by nicknames, the chat format and LuckPerms prefixes/suffixes.
 * <p>
 * Supported (a MiniMessage subset plus legacy codes):
 * {@code <#RRGGBB>}, {@code <color:#RRGGBB|name>}, named colors ({@code <red>}, {@code <gold>}, ...),
 * {@code <b>/<bold>}, {@code <i>/<italic>}, {@code <u>/<underline>/<underlined>},
 * {@code <gradient:c1:c2[:c3...]>}, {@code <reset>}, matching closing tags, and
 * {@code &0-&f}, {@code &#RRGGBB}, {@code &l}, {@code &o}, {@code &n}, {@code &r} (also with {@code §}).
 * Styles are scoped: a closing tag ends only its own style. Unknown tags are kept as text.
 */
public final class MessageUtil {

    /** A run of text with one resolved style. */
    public record Segment(String text, @Nullable String color, boolean bold, boolean italic, boolean underline) {}

    private static final Pattern COLOR_PATTERN = Pattern.compile("<color:(#[0-9A-Fa-f]{6})>");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
        Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000AA"), Map.entry("dark_green", "#00AA00"),
        Map.entry("dark_aqua", "#00AAAA"), Map.entry("dark_red", "#AA0000"), Map.entry("dark_purple", "#AA00AA"),
        Map.entry("gold", "#FFAA00"), Map.entry("gray", "#AAAAAA"), Map.entry("grey", "#AAAAAA"),
        Map.entry("dark_gray", "#555555"), Map.entry("dark_grey", "#555555"), Map.entry("blue", "#5555FF"),
        Map.entry("green", "#55FF55"), Map.entry("aqua", "#55FFFF"), Map.entry("red", "#FF5555"),
        Map.entry("light_purple", "#FF55FF"), Map.entry("yellow", "#FFFF55"), Map.entry("white", "#FFFFFF"));
    /** Legacy color codes 0-f, in order. */
    private static final String[] LEGACY_COLORS = {
        "#000000", "#0000AA", "#00AA00", "#00AAAA", "#AA0000", "#AA00AA", "#FFAA00", "#AAAAAA",
        "#555555", "#5555FF", "#55FF55", "#55FFFF", "#FF5555", "#FF55FF", "#FFFF55", "#FFFFFF"};

    private MessageUtil() {}

    // --- Public API ---

    @Nonnull
    public static Message parse(@Nonnull String input) {
        return parse(input, null);
    }

    /** Parses markup; text without an explicit color gets {@code defaultColor} (may be null). */
    @Nonnull
    public static Message parse(@Nonnull String input, @Nullable String defaultColor) {
        return toMessage(segments(input), defaultColor);
    }

    /** Literal text (never parsed as markup) colored with a gradient between two hex colors. */
    @Nonnull
    public static Message gradient(@Nonnull String text, @Nonnull String fromHex, @Nonnull String toHex) {
        Gradient gradient = new Gradient(List.of(fromHex, toHex));
        gradient.length = text.codePointCount(0, text.length());
        List<Segment> segments = new ArrayList<>();
        int index = 0;
        for (int cp : text.codePoints().toArray()) {
            segments.add(new Segment(new String(Character.toChars(cp)), gradient.colorAt(index++), false, false, false));
        }
        return toMessage(segments, null);
    }

    @Nonnull
    public static Message parseForUI(@Nonnull String input) {
        // Without color/gradient - use markupEnabled for native tag parsing
        if (!input.contains("<color:") && !input.contains("<gradient:")) {
            Message msg = Message.raw(input);
            msg.getFormattedMessage().markupEnabled = true;
            return msg;
        }

        // With gradient: per-character colors. Underline does not work with per-char coloring
        // in the UI (engine limitation); the page draws it separately.
        if (input.contains("<gradient:")) {
            List<Segment> segments = new ArrayList<>();
            for (Segment segment : segments(input)) {
                segments.add(new Segment(segment.text(), segment.color(), segment.bold(), segment.italic(), false));
            }
            return toMessage(segments, null);
        }

        // With color only - markupEnabled for the style tags + color on the same message
        Matcher colorMatcher = COLOR_PATTERN.matcher(input);
        if (colorMatcher.find()) {
            String withStyleTags = input.replaceAll("<color:#[0-9A-Fa-f]{6}>", "").replace("</color>", "");
            Message msg = Message.raw(withStyleTags);
            msg.getFormattedMessage().markupEnabled = true;
            msg.color(colorMatcher.group(1));
            return msg;
        }

        return parse(input);
    }

    public static boolean hasMarkup(@Nonnull String input) {
        return input.contains("<") && input.contains(">");
    }

    /** Visible text without any tags (used for nameplate, tab list and map). */
    @Nonnull
    public static String stripTags(@Nonnull String input) {
        return input.replaceAll("<[^>]+>", "");
    }

    /**
     * Same styling as MiniMessage / EssentialsPlus markup ({@code <#RRGGBB>}, {@code <bold>}, ...),
     * for chat plugins that read nicknames through PlaceholderAPI.
     */
    @Nonnull
    public static String toMiniMessage(@Nonnull String input) {
        StringBuilder out = new StringBuilder();
        for (Segment s : segments(input)) {
            if (s.color() != null) out.append('<').append(s.color()).append('>');
            if (s.bold()) out.append("<bold>");
            if (s.italic()) out.append("<italic>");
            if (s.underline()) out.append("<underlined>");
            out.append(s.text().replace("<", "\\<"));
            if (s.underline()) out.append("</underlined>");
            if (s.italic()) out.append("</italic>");
            if (s.bold()) out.append("</bold>");
            if (s.color() != null) out.append("</").append(s.color()).append('>');
        }
        return out.toString();
    }

    /**
     * Colors only, as EssentialsPlus markup ({@code <#RRGGBB>text</#RRGGBB>}), for EssentialsPlus'
     * own nickname. Styles are left out: EP breaks them inside its chat format's color tags.
     */
    @Nonnull
    public static String toEssentialsPlus(@Nonnull String input) {
        StringBuilder out = new StringBuilder();
        for (Segment s : segments(input)) {
            if (s.color() == null) {
                out.append(s.text());
            } else {
                out.append('<').append(s.color()).append('>').append(s.text()).append("</").append(s.color()).append('>');
            }
        }
        return out.toString();
    }
    /** Same styling as legacy codes ({@code &#RRGGBB}, {@code &l}, {@code &o}, {@code &n}). */
    @Nonnull
    public static String toLegacy(@Nonnull String input) {
        StringBuilder out = new StringBuilder();
        for (Segment s : segments(input)) {
            boolean styled = s.bold() || s.italic() || s.underline();
            if (s.color() != null) {
                out.append('&').append(s.color());
            } else if (styled || out.length() > 0) {
                out.append("&r");
            }
            if (s.bold()) out.append("&l");
            if (s.italic()) out.append("&o");
            if (s.underline()) out.append("&n");
            out.append(s.text());
        }
        return out.toString();
    }

    // --- Parser ---

    /** Resolved style at one point of the input. Immutable; each opening tag pushes a new one. */
    private record Style(String closes, @Nullable String color, @Nullable Gradient gradient,
                         boolean bold, boolean italic, boolean underline) {
        static final Style PLAIN = new Style("", null, null, false, false, false);

        Style withColor(String closes, String color) {
            return new Style(closes, color, null, bold, italic, underline);
        }

        Style withGradient(Gradient gradient) {
            return new Style("gradient", null, gradient, bold, italic, underline);
        }

        Style withBold() {
            return new Style("bold", color, gradient, true, italic, underline);
        }

        Style withItalic() {
            return new Style("italic", color, gradient, bold, true, underline);
        }

        Style withUnderline() {
            return new Style("underline", color, gradient, bold, italic, true);
        }
    }

    private static final class Gradient {
        final List<int[]> stops = new ArrayList<>();
        int length;

        Gradient(List<String> hexColors) {
            for (String hex : hexColors) {
                stops.add(new int[]{
                    Integer.parseInt(hex.substring(1, 3), 16),
                    Integer.parseInt(hex.substring(3, 5), 16),
                    Integer.parseInt(hex.substring(5, 7), 16)});
            }
        }

        String colorAt(int index) {
            float t = length > 1 ? (float) index / (length - 1) : 0f;
            float scaled = t * (stops.size() - 1);
            int k = Math.min((int) scaled, stops.size() - 2);
            float local = scaled - k;
            int[] a = stops.get(k), b = stops.get(k + 1);
            return String.format("#%02X%02X%02X",
                Math.round(a[0] + local * (b[0] - a[0])),
                Math.round(a[1] + local * (b[1] - a[1])),
                Math.round(a[2] + local * (b[2] - a[2])));
        }
    }

    private record Piece(String text, Style style) {}

    /** Splits markup into styled text runs; gradients are expanded to one run per character. */
    @Nonnull
    public static List<Segment> segments(@Nonnull String input) {
        List<Piece> pieces = new ArrayList<>();
        Deque<Style> stack = new ArrayDeque<>();
        stack.push(Style.PLAIN);
        StringBuilder text = new StringBuilder();

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '<') {
                int end = input.indexOf('>', i);
                if (end > i && applyTag(input.substring(i + 1, end), stack, pieces, text)) {
                    i = end + 1;
                    continue;
                }
            } else if ((c == '&' || c == '§') && i + 1 < input.length()) {
                int consumed = applyLegacy(input, i + 1, stack, pieces, text);
                if (consumed > 0) {
                    i += 1 + consumed;
                    continue;
                }
            }
            text.append(c);
            i++;
        }
        flush(pieces, text, stack.peek());
        return expand(pieces);
    }

    private static void flush(List<Piece> pieces, StringBuilder text, Style style) {
        if (text.length() > 0) {
            pieces.add(new Piece(text.toString(), style));
            text.setLength(0);
        }
    }

    /** Handles one tag; returns false if it is not a known tag (then it stays as text). */
    private static boolean applyTag(String rawTag, Deque<Style> stack, List<Piece> pieces, StringBuilder text) {
        String tag = rawTag.trim().toLowerCase(Locale.ROOT);
        if (tag.equals("reset")) {
            flush(pieces, text, stack.peek());
            stack.clear();
            stack.push(Style.PLAIN);
            return true;
        }
        if (tag.startsWith("/")) {
            String closes = closingName(tag.substring(1));
            if (closes == null) return false;
            flush(pieces, text, stack.peek());
            // Pop up to and including the innermost style this tag closes; ignore unmatched closes
            if (stack.stream().anyMatch(s -> s.closes().equals(closes))) {
                while (stack.size() > 1) {
                    if (stack.pop().closes().equals(closes)) break;
                }
            }
            return true;
        }

        Style current = stack.peek();
        Style next = null;
        switch (tag) {
            case "b", "bold" -> next = current.withBold();
            case "i", "italic", "em" -> next = current.withItalic();
            case "u", "underline", "underlined" -> next = current.withUnderline();
            default -> {
                String color = colorValue(tag);
                if (color != null) {
                    next = current.withColor("color", color);
                } else if (tag.startsWith("color:") || tag.startsWith("colour:") || tag.startsWith("c:")) {
                    color = colorValue(tag.substring(tag.indexOf(':') + 1));
                    if (color != null) next = current.withColor("color", color);
                } else if (tag.startsWith("gradient:")) {
                    List<String> colors = new ArrayList<>();
                    for (String part : tag.substring("gradient:".length()).split(":")) {
                        String value = colorValue(part);
                        if (value == null) return false;
                        colors.add(value);
                    }
                    if (colors.size() >= 2) next = current.withGradient(new Gradient(colors));
                }
            }
        }
        if (next == null) return false;
        flush(pieces, text, current);
        stack.push(next);
        return true;
    }

    @Nullable
    private static String closingName(String name) {
        return switch (name) {
            case "b", "bold" -> "bold";
            case "i", "italic", "em" -> "italic";
            case "u", "underline", "underlined" -> "underline";
            case "gradient" -> "gradient";
            case "color", "colour", "c" -> "color";
            default -> colorValue(name) != null ? "color" : null;
        };
    }

    @Nullable
    private static String colorValue(String value) {
        if (HEX.matcher(value).matches()) return value.toUpperCase(Locale.ROOT);
        return NAMED_COLORS.get(value);
    }

    /** Legacy {@code &x} / {@code &#RRGGBB} code after the '&'; returns the number of chars consumed (0 = not a code). */
    private static int applyLegacy(String input, int at, Deque<Style> stack, List<Piece> pieces, StringBuilder text) {
        char code = Character.toLowerCase(input.charAt(at));
        String color = null;
        int consumed = 1;
        if (code == '#' && at + 7 <= input.length() && HEX.matcher(input.substring(at, at + 7)).matches()) {
            color = input.substring(at, at + 7).toUpperCase(Locale.ROOT);
            consumed = 7;
        } else if (Character.digit(code, 16) >= 0) {
            color = LEGACY_COLORS[Character.digit(code, 16)];
        } else if ("lonr".indexOf(code) < 0) {
            return 0;
        }

        flush(pieces, text, stack.peek());
        if (color != null || code == 'r') {
            // Like Minecraft: a color code (or &r) resets formatting
            stack.clear();
            stack.push(Style.PLAIN);
            if (color != null) stack.push(Style.PLAIN.withColor("legacy", color));
        } else {
            Style current = stack.peek();
            stack.push(code == 'l' ? current.withBold() : code == 'o' ? current.withItalic() : current.withUnderline());
        }
        return consumed;
    }

    /** Resolves gradients into per-character colors. */
    private static List<Segment> expand(List<Piece> pieces) {
        for (Piece piece : pieces) {
            if (piece.style().gradient() != null) {
                piece.style().gradient().length += piece.text().codePointCount(0, piece.text().length());
            }
        }
        Map<Gradient, Integer> positions = new IdentityHashMap<>();
        List<Segment> segments = new ArrayList<>();
        for (Piece piece : pieces) {
            Style style = piece.style();
            Gradient gradient = style.gradient();
            if (gradient == null) {
                segments.add(new Segment(piece.text(), style.color(), style.bold(), style.italic(), style.underline()));
                continue;
            }
            int index = positions.getOrDefault(gradient, 0);
            for (int cp : piece.text().codePoints().toArray()) {
                segments.add(new Segment(new String(Character.toChars(cp)), gradient.colorAt(index++),
                    style.bold(), style.italic(), style.underline()));
            }
            positions.put(gradient, index);
        }
        return segments;
    }

    @Nonnull
    private static Message toMessage(List<Segment> segments, @Nullable String defaultColor) {
        Message result = Message.empty();
        for (Segment s : segments) {
            Message part = Message.raw(s.text());
            String color = s.color() != null ? s.color() : defaultColor;
            if (color != null) part.color(color);
            if (s.bold()) part.bold(true);
            if (s.italic()) part.italic(true);
            if (s.underline()) part.getFormattedMessage().underlined = Boolean.TRUE;
            result = result.insert(part);
        }
        return result;
    }
}
