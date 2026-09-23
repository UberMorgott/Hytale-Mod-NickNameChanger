package com.nickname.plugin.validation;

import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.i18n.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NicknameValidatorTest {

    private static NicknameValidator validator(String regex) {
        PluginConfig.NicknameRules rules = new PluginConfig.NicknameRules();
        rules.allowedCharactersRegex = regex;
        rules.maxLength = 8;
        return new NicknameValidator(rules, message -> fail(message));
    }

    @Test
    void acceptsFormattedNickname() {
        NicknameValidator.Result result = validator("").validate("<gradient:#FF0000:#0000FF><b>Morg</b></gradient>");
        assertTrue(result.isValid());
        assertEquals("Morg", result.plain());
        assertEquals("<gradient:#FF0000:#0000FF><b>Morg</b></gradient>", result.nickname());
    }

    @Test
    void lengthCountsVisibleCodePoints() {
        assertTrue(validator("").validate("<color:#FF0000>Abcdefgh</color>").isValid());
        assertEquals(Messages.ERROR_MAX_LENGTH, validator("").validate("Abcdefghi").errorKey());
        assertEquals(Messages.ERROR_MIN_LENGTH, validator("").validate("<b>A</b>").errorKey());
    }

    @Test
    void rejectsUnknownTagsAndMarkupCharacters() {
        assertEquals(Messages.ERROR_INVALID, validator("").validate("<red>Bob</red>").errorKey());
        assertEquals(Messages.ERROR_INVALID, validator("").validate("<color:red>Bob</color>").errorKey());
        assertEquals(Messages.ERROR_INVALID_CHARACTERS, validator("").validate("Bo<b").errorKey());
        assertEquals(Messages.ERROR_INVALID_CHARACTERS, validator("").validate("&cBob").errorKey());
        assertEquals(Messages.ERROR_INVALID_CHARACTERS, validator("").validate("Bo#$b").errorKey());
    }

    @Test
    void regexReplacesDefaultCharacterPolicy() {
        assertTrue(validator("^[A-Za-z0-9_]+$").validate("Bob_1").isValid());
        assertEquals(Messages.ERROR_INVALID_CHARACTERS, validator("^[A-Za-z0-9_]+$").validate("Bob 1").errorKey());
        // markup characters stay forbidden even if the regex allows them
        assertEquals(Messages.ERROR_INVALID_CHARACTERS, validator(".*").validate("B%b%").errorKey());
    }

    @Test
    void bannedWordsAreCaseInsensitiveAndIgnoreFormatting() {
        assertEquals(Messages.ERROR_BANNED_WORD, validator("").validate("<b>AdM</b>in").errorKey());
    }

    @Test
    void canonicalIgnoresCase() {
        assertEquals(NicknameValidator.canonical("Bob"), NicknameValidator.canonical(" bOB "));
    }
}
