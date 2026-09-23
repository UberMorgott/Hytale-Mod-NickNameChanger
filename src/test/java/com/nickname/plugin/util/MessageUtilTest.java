package com.nickname.plugin.util;

import com.nickname.plugin.util.MessageUtil.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageUtilTest {

    @Test
    void stylesAreScopedToTheirTags() {
        List<Segment> segments = MessageUtil.segments("<#FD0054><bold>O</bold></#FD0054>rest");
        assertEquals(List.of(
            new Segment("O", "#FD0054", true, false, false),
            new Segment("rest", null, false, false, false)), segments);
    }

    @Test
    void closingColorRevertsToOuterColor() {
        List<Segment> segments = MessageUtil.segments("<red>a<#00FF00>b</#00FF00>c</red>");
        assertEquals("#FF5555", segments.get(0).color());
        assertEquals("#00FF00", segments.get(1).color());
        assertEquals("#FF5555", segments.get(2).color());
    }

    @Test
    void gradientSpansNestedTagsAndMixesWithHex() {
        List<Segment> segments = MessageUtil.segments("<#111111>[</#111111><gradient:#000000:#FFFFFF>a<b>b</b>c</gradient>");
        assertEquals(new Segment("[", "#111111", false, false, false), segments.get(0));
        assertEquals("#000000", segments.get(1).color());
        assertEquals(new Segment("b", "#808080", true, false, false), segments.get(2));
        assertEquals("#FFFFFF", segments.get(3).color());
    }

    @Test
    void threeStopGradient() {
        List<Segment> segments = MessageUtil.segments("<gradient:#FF0000:#00FF00:#0000FF>abc</gradient>");
        assertEquals(List.of("#FF0000", "#00FF00", "#0000FF"), segments.stream().map(Segment::color).toList());
    }

    @Test
    void legacyCodes() {
        List<Segment> segments = MessageUtil.segments("&c[&lAdmin&r] &#123456x");
        assertEquals(new Segment("[", "#FF5555", false, false, false), segments.get(0));
        assertEquals(new Segment("Admin", "#FF5555", true, false, false), segments.get(1));
        assertEquals(new Segment("] ", null, false, false, false), segments.get(2));
        assertEquals(new Segment("x", "#123456", false, false, false), segments.get(3));
    }

    @Test
    void unknownTagsAndLoneBracketsStayText() {
        assertEquals(List.of(new Segment("<3 <papi:x> Tom & Jerry", null, false, false, false)),
            MessageUtil.segments("<3 <papi:x> Tom & Jerry"));
    }

    @Test
    void placeholderFormats() {
        String nick = "<color:#FF5555><b>Bob</b></color>";
        assertEquals("<#FF5555><bold>Bob</bold></#FF5555>", MessageUtil.toMiniMessage(nick));
        assertEquals("&#FF5555&lBob", MessageUtil.toLegacy(nick));
        assertEquals("Bob", MessageUtil.toLegacy("Bob"));
        assertEquals("<#FF5555>Bob</#FF5555>", MessageUtil.toEssentialsPlus(nick));
        assertEquals("Bob", MessageUtil.toEssentialsPlus("<b>Bob</b>"));
    }
}
