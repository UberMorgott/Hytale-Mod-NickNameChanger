package com.nickname.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class PluginConfig {

    public static final BuilderCodec<PluginConfig> CODEC = BuilderCodec
            .builder(PluginConfig.class, PluginConfig::new)
            .append(new KeyedCodec<>("PluginName", Codec.STRING), (o, v) -> o.pluginName = v, o -> o.pluginName).add()
            .append(new KeyedCodec<>("Version", Codec.STRING), (o, v) -> o.version = v, o -> o.version).add()
            .append(new KeyedCodec<>("DebugMode", Codec.BOOLEAN), (o, v) -> o.debugMode = v, o -> o.debugMode).add()
            .append(new KeyedCodec<>("ChatFormat", Codec.STRING), (o, v) -> o.chatFormat = v, o -> o.chatFormat).add()
            .append(new KeyedCodec<>("Display", DisplayConfig.CODEC), (o, v) -> o.display = v, o -> o.display).add()
            .append(new KeyedCodec<>("Nicknames", NicknameRules.CODEC), (o, v) -> o.nicknames = v, o -> o.nicknames).add()
            .append(new KeyedCodec<>("Integrations", Integrations.CODEC), (o, v) -> o.integrations = v, o -> o.integrations).add()
            .build();

    public String pluginName = "NicknameChanger";
    public String version = "0.0.18";
    public boolean debugMode = false;
    public String chatFormat = "{prefix}<{username}>{suffix} {message}";
    public DisplayConfig display = new DisplayConfig();
    public NicknameRules nicknames = new NicknameRules();
    public Integrations integrations = new Integrations();

    public static class DisplayConfig {

        public static final BuilderCodec<DisplayConfig> CODEC = BuilderCodec
                .builder(DisplayConfig.class, DisplayConfig::new)
                .append(new KeyedCodec<>("ShowInChat", Codec.BOOLEAN), (o, v) -> o.showInChat = v, o -> o.showInChat).add()
                .append(new KeyedCodec<>("ShowOnNameplate", Codec.BOOLEAN), (o, v) -> o.showOnNameplate = v, o -> o.showOnNameplate).add()
                .append(new KeyedCodec<>("ShowInTabList", Codec.BOOLEAN), (o, v) -> o.showInTabList = v, o -> o.showInTabList).add()
                .append(new KeyedCodec<>("ShowOnMap", Codec.BOOLEAN), (o, v) -> o.showOnMap = v, o -> o.showOnMap).add()
                .build();

        // volatile: changed from the settings UI (world thread), read from chat and network threads
        public volatile boolean showInChat = true;
        public volatile boolean showOnNameplate = true;
        public volatile boolean showInTabList = true;
        public volatile boolean showOnMap = false;
    }

    public static class NicknameRules {

        public static final BuilderCodec<NicknameRules> CODEC = BuilderCodec
                .builder(NicknameRules.class, NicknameRules::new)
                .append(new KeyedCodec<>("MinLength", Codec.INTEGER), (o, v) -> o.minLength = v, o -> o.minLength).add()
                .append(new KeyedCodec<>("MaxLength", Codec.INTEGER), (o, v) -> o.maxLength = v, o -> o.maxLength).add()
                .append(new KeyedCodec<>("AllowCyrillic", Codec.BOOLEAN), (o, v) -> o.allowCyrillic = v, o -> o.allowCyrillic).add()
                .append(new KeyedCodec<>("AllowUnicode", Codec.BOOLEAN), (o, v) -> o.allowUnicode = v, o -> o.allowUnicode).add()
                .append(new KeyedCodec<>("UniqueNicknames", Codec.BOOLEAN), (o, v) -> o.uniqueNicknames = v, o -> o.uniqueNicknames).add()
                .append(new KeyedCodec<>("BannedWords", Codec.STRING_ARRAY), (o, v) -> o.bannedWords = v, o -> o.bannedWords).add()
                .append(new KeyedCodec<>("AllowedCharactersRegex", Codec.STRING), (o, v) -> o.allowedCharactersRegex = v, o -> o.allowedCharactersRegex).add()
                .append(new KeyedCodec<>("BlockRealUsernames", Codec.BOOLEAN), (o, v) -> o.blockRealUsernames = v, o -> o.blockRealUsernames).add()
                .build();

        public int minLength = 2;
        public int maxLength = 32;
        public boolean allowCyrillic = true;
        public boolean allowUnicode = false;
        public boolean uniqueNicknames = true;
        public String[] bannedWords = {"admin", "moderator", "server", "owner"};
        /** Regex the whole visible nickname must match, e.g. {@code ^[A-Za-z0-9_]+$}. Empty: use AllowCyrillic/AllowUnicode. */
        public String allowedCharactersRegex = "";
        /** Reject nicknames equal to the real username of another known player. */
        public boolean blockRealUsernames = true;
    }

    public static class Integrations {

        public static final BuilderCodec<Integrations> CODEC = BuilderCodec
                .builder(Integrations.class, Integrations::new)
                .append(new KeyedCodec<>("Luckperms", LuckPermsConfig.CODEC), (o, v) -> o.luckperms = v, o -> o.luckperms).add()
                .build();

        public LuckPermsConfig luckperms = new LuckPermsConfig();
    }

    public static class LuckPermsConfig {

        public static final BuilderCodec<LuckPermsConfig> CODEC = BuilderCodec
                .builder(LuckPermsConfig.class, LuckPermsConfig::new)
                .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (o, v) -> o.enabled = v, o -> o.enabled).add()
                .append(new KeyedCodec<>("ShowPrefix", Codec.BOOLEAN), (o, v) -> o.showPrefix = v, o -> o.showPrefix).add()
                .append(new KeyedCodec<>("ShowSuffix", Codec.BOOLEAN), (o, v) -> o.showSuffix = v, o -> o.showSuffix).add()
                .build();

        public boolean enabled = true;
        public boolean showPrefix = true;
        public boolean showSuffix = true;
    }
}
