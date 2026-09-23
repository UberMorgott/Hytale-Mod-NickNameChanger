package com.nickname.plugin.util;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markup parser for styled messages.
 * Tags: <b>, <i>, <u>, <color:#HEX>, <gradient:#HEX:#HEX>
 */
public final class MessageUtil {

    private static final Pattern COLOR_PATTERN = Pattern.compile("<color:(#[0-9A-Fa-f]{6})>");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[0-9A-Fa-f]{6}):(#[0-9A-Fa-f]{6})>");
    private static final Pattern SHORT_HEX_PATTERN = Pattern.compile("<(#[0-9A-Fa-f]{6})>");

    private MessageUtil() {}

    @Nonnull
    public static Message parse(@Nonnull String input) {
        if (!input.contains("<")) return Message.raw(input);

        boolean bold = input.contains("<b>") || input.contains("<bold>");
        boolean italic = input.contains("<i>") || input.contains("<italic>");
        boolean underline = input.contains("<u>") || input.contains("<underline>");

        // Short hex format: <#XXXXXX> (used by LuckPerms per-character coloring)
        if (SHORT_HEX_PATTERN.matcher(input).find() && !input.contains("<color:") && !input.contains("<gradient:")) {
            return parseShortHex(input, bold, italic, underline);
        }

        Matcher colorMatcher = COLOR_PATTERN.matcher(input);
        String color = colorMatcher.find() ? colorMatcher.group(1) : null;

        Matcher gradMatcher = GRADIENT_PATTERN.matcher(input);
        String grad1 = null, grad2 = null;
        if (gradMatcher.find()) {
            grad1 = gradMatcher.group(1);
            grad2 = gradMatcher.group(2);
        }

        String text = stripTags(input);
        if (text.isEmpty()) return Message.empty();

        if (grad1 != null && grad2 != null) {
            return buildGradient(text, grad1, grad2, bold, italic, underline);
        }
        return buildStyled(text, color, bold, italic, underline);
    }

    @Nonnull
    public static Message parseForUI(@Nonnull String input) {
        // Without color/gradient - use markupEnabled for native tag parsing
        if (!input.contains("<color:") && !input.contains("<gradient:")) {
            Message msg = Message.raw(input);
            msg.getFormattedMessage().markupEnabled = true;
            return msg;
        }

        // With gradient - use special UI gradient builder
        if (input.contains("<gradient:")) {
            return parseGradientForUI(input);
        }

        // With color only - try markupEnabled + color on same message
        Matcher colorMatcher = COLOR_PATTERN.matcher(input);
        if (colorMatcher.find()) {
            String color = colorMatcher.group(1);
            // Remove only color tags, keep style tags for markupEnabled
            String withStyleTags = input.replaceAll("<color:#[0-9A-Fa-f]{6}>", "")
                                        .replaceAll("</color>", "");

            Message msg = Message.raw(withStyleTags);
            msg.getFormattedMessage().markupEnabled = true;
            msg.color(color);
            return msg;
        }

        return parse(input);
    }

    public static boolean hasMarkup(@Nonnull String input) {
        return input.contains("<") && input.contains(">");
    }

    @Nonnull
    public static String stripTags(@Nonnull String input) {
        return input.replaceAll("<[^>]+>", "");
    }

    /**
     * Parses short hex format: {@code <#XXXXXX>text<#YYYYYY>text...}
     * Each {@code <#HEX>} tag starts a new colored segment (used by LuckPerms prefixes).
     */
    @Nonnull
    private static Message parseShortHex(String input, boolean bold, boolean italic, boolean underline) {
        Message result = Message.empty();
        Matcher m = SHORT_HEX_PATTERN.matcher(input);

        String currentColor = null;
        int lastEnd = 0;

        while (m.find()) {
            String before = input.substring(lastEnd, m.start());
            if (!before.isEmpty()) {
                String clean = stripTags(before);
                if (!clean.isEmpty()) {
                    result = result.insert(buildStyled(clean, currentColor, bold, italic, underline));
                }
            }
            currentColor = m.group(1);
            lastEnd = m.end();
        }

        String remaining = input.substring(lastEnd);
        if (!remaining.isEmpty()) {
            String clean = stripTags(remaining);
            if (!clean.isEmpty()) {
                result = result.insert(buildStyled(clean, currentColor, bold, italic, underline));
            }
        }

        return result;
    }

    @Nonnull
    private static Message buildStyled(String text, String color, boolean bold, boolean italic, boolean underline) {
        Message msg = Message.raw(text);
        if (bold) msg.bold(true);
        if (italic) msg.italic(true);
        if (color != null) msg.color(color);
        if (underline) msg.getFormattedMessage().underlined = Boolean.TRUE;
        return msg;
    }

    @Nonnull
    private static Message buildGradient(String text, String c1, String c2, boolean bold, boolean italic, boolean underline) {
        int[] rgb1 = hexToRgb(c1), rgb2 = hexToRgb(c2);
        Message result = Message.empty();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            float r = len > 1 ? (float) i / (len - 1) : 0;
            String hex = String.format("#%02X%02X%02X",
                (int)(rgb1[0] + r * (rgb2[0] - rgb1[0])),
                (int)(rgb1[1] + r * (rgb2[1] - rgb1[1])),
                (int)(rgb1[2] + r * (rgb2[2] - rgb1[2])));

            Message ch = Message.raw(String.valueOf(text.charAt(i)));
            if (bold) ch.bold(true);
            if (italic) ch.italic(true);
            ch.color(hex);
            if (underline) ch.getFormattedMessage().underlined = Boolean.TRUE;
            result = result.insert(ch);
        }
        return result;
    }

    @Nonnull
    private static Message parseGradientForUI(@Nonnull String input) {
        // Gradient in UI: underline does NOT work with per-char coloring (engine limitation)
        // The UI draws a manual underline line instead - see NicknameEditorPage.updatePreview()
        return buildGradient(
            stripTags(input),
            extractGradientColor1(input),
            extractGradientColor2(input),
            input.contains("<b>") || input.contains("<bold>"),
            input.contains("<i>") || input.contains("<italic>"),
            false  // underline handled visually in UI, not through Message API
        );
    }

    private static String extractGradientColor1(String input) {
        Matcher m = GRADIENT_PATTERN.matcher(input);
        return m.find() ? m.group(1) : "#FFFFFF";
    }

    private static String extractGradientColor2(String input) {
        Matcher m = GRADIENT_PATTERN.matcher(input);
        return m.find() ? m.group(2) : "#FFFFFF";
    }

    /**
     * Converts TinyMessage tags to EssentialsPlus ColoredTextParser format.
     * TinyMessage: {@code <color:#FF5555>text</color>}, {@code <gradient:#c1:#c2>text</gradient>}
     * EP format:   {@code <#FF5555>text</#FF5555>},     {@code <gradient:#c1:#c2>text</gradient>}
     */
    @Nonnull
    public static String convertToEPFormat(@Nonnull String tinyMessage) {
        if (tinyMessage.isEmpty()) return tinyMessage;

        String result = tinyMessage;

        // Convert <color:#RRGGBB> to <#RRGGBB>
        Matcher matcher = COLOR_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        result = sb.toString();

        // Replace </color> closing tags with proper </#RRGGBB> using a color stack
        result = replaceColorCloseTags(result);

        // Convert short-form tags to EP long-form
        result = result.replace("<b>", "<bold>").replace("</b>", "</bold>");
        result = result.replace("<i>", "<italic>").replace("</i>", "</italic>");
        result = result.replace("<u>", "<underlined>").replace("</u>", "</underlined>");

        // EP uses <underlined> not <underline>
        result = result.replace("<underline>", "<underlined>").replace("</underline>", "</underlined>");

        // EP doesn't support style tags in {player} placeholder — strip them.
        // While ColoredTextParser itself can parse <bold>/<italic>/<underlined>,
        // they break when embedded inside the chat format's own color tags
        // (e.g. <#AAAAAA>{player}</#AAAAAA> wrapping <bold>Nick</bold>).
        result = result.replaceAll("</?bold>", "");
        result = result.replaceAll("</?italic>", "");
        result = result.replaceAll("</?underlined?>", "");
        result = result.replaceAll("</?b>", "");
        result = result.replaceAll("</?i>", "");
        result = result.replaceAll("</?u>", "");

        // gradient and color tags are compatible with EP format — keep them
        return result;
    }

    private static String replaceColorCloseTags(@Nonnull String input) {
        StringBuilder result = new StringBuilder();
        java.util.Deque<String> colorStack = new java.util.ArrayDeque<>();
        int i = 0;
        while (i < input.length()) {
            if (input.startsWith("</color>", i)) {
                if (!colorStack.isEmpty()) {
                    result.append("</").append(colorStack.pop()).append(">");
                }
                i += "</color>".length();
            } else if (input.charAt(i) == '<' && i + 1 < input.length() && input.charAt(i + 1) == '#') {
                // Opening color tag like <#FF5555>
                int end = input.indexOf('>', i);
                if (end > i) {
                    String tag = input.substring(i + 1, end); // "#FF5555"
                    colorStack.push(tag);
                    result.append(input, i, end + 1);
                    i = end + 1;
                } else {
                    result.append(input.charAt(i));
                    i++;
                }
            } else {
                result.append(input.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    private static int[] hexToRgb(String hex) {
        hex = hex.replace("#", "");
        return new int[] {
            Integer.parseInt(hex.substring(0, 2), 16),
            Integer.parseInt(hex.substring(2, 4), 16),
            Integer.parseInt(hex.substring(4, 6), 16)
        };
    }
}
