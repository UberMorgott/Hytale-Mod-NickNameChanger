package com.nickname.plugin.compat;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.nickname.plugin.hooks.PluginDetector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * mini-chat-formatter (lucko, 0.1.x) renders {@code <username>} from the real username at format
 * time. This adapter runs at LATE (after MCF installs its formatter, before MCF's LAST check) and
 * swaps in another {@code MiniChatFormatter} whose format uses {@code <nnc_username>} instead:
 * built with MCF's public constructor, the same LuckPerms / PlaceholderAPI resolvers MCF installs,
 * plus a resolver for the NNC nickname. MCF keeps owning the chat (relational formats, delivery).
 * One adapted formatter is cached per MCF formatter, so /mcf reload is picked up.
 * <p>
 * Uses reflection against MCF's public classes (verified on 0.1.0-beta7), so nothing of MCF is
 * needed at compile time. Other MCF versions are not touched.
 */
public final class MiniChatFormatterCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.get("NicknameChanger");
    private static final String BASE = "me.lucko.minichatformatter.";
    private static final String KYORI = BASE + "lib.kyori.adventure.text.minimessage.";
    private static final String TAG = "nnc_username";
    /** The {@code <username>} tag, not an escaped {@code \<username>}. */
    private static final Pattern USERNAME_TAG = Pattern.compile("(?<!\\\\)<username>");

    private final NicknamePlaceholders placeholders;
    private final Class<?> formatterClass;
    private final Method getFormat;
    private final Constructor<?> formatterConstructor;
    private final Object configFunction;
    private final Map<PlayerChatEvent.Formatter, PlayerChatEvent.Formatter> adapted =
        Collections.synchronizedMap(new WeakHashMap<>());

    // Used by the proxies
    private final Method builderResolver;
    private final Method hookInitIfRequired;
    private final Object luckPermsHook;
    private final Object placeholderApiHook;
    private final Constructor<?> luckPermsResolver;
    private final Constructor<?> placeholderApiResolver;
    private final Method parseFormattedText;
    private final Method contextTarget;
    private final Method chatSender;
    private final Object nicknameResolver;

    private MiniChatFormatterCompat(NicknamePlaceholders placeholders) throws ReflectiveOperationException {
        this.placeholders = placeholders;
        this.formatterClass = Class.forName(BASE + "MiniChatFormatter");
        Class<?> configFunctionClass = Class.forName(BASE + "MiniChatFormatter$TagConfigFunction");
        Class<?> tagResolverClass = Class.forName(KYORI + "tag.resolver.TagResolver");
        Class<?> hookClass = Class.forName(BASE + "hook.Hook");

        this.getFormat = formatterClass.getMethod("getFormat");
        this.formatterConstructor = formatterClass.getConstructor(String.class, HytaleLogger.class, configFunctionClass);
        this.builderResolver = Class.forName(KYORI + "tag.resolver.TagResolver$Builder").getMethod("resolver", tagResolverClass);
        this.hookInitIfRequired = hookClass.getMethod("initIfRequired", String.class, HytaleLogger.class);
        this.luckPermsHook = hookClass.getField("LUCKPERMS").get(null);
        this.placeholderApiHook = hookClass.getField("PLACEHOLDER_API").get(null);
        this.luckPermsResolver = Class.forName(BASE + "hook.luckperms.LuckPermsTagResolver").getConstructors()[0];
        this.placeholderApiResolver = Class.forName(BASE + "hook.placeholderapi.PlaceholderApiTagResolver").getConstructors()[0];
        this.parseFormattedText = Class.forName(BASE + "format.FormatUtil").getMethod("parseFormattedText", String.class);
        this.contextTarget = Class.forName(KYORI + "Context").getMethod("target");
        this.chatSender = Class.forName(BASE + "context.ChatContext").getMethod("sender");

        this.nicknameResolver = proxy(tagResolverClass, (proxy, method, args) -> switch (method.getName()) {
            case "has" -> TAG.equals(args[0]);
            case "resolve" -> args.length == 3 && TAG.equals(args[0]) ? nicknameTag(args[2]) : null;
            default -> objectMethod(proxy, method, args);
        });
        this.configFunction = proxy(configFunctionClass, (proxy, method, args) -> {
            if (!method.getName().equals("apply")) return objectMethod(proxy, method, args);
            installResolvers((String) args[0], args[1]);
            return null;
        });
    }

    /** Returns the adapter, or {@code null} if mini-chat-formatter is absent, another version, or its API differs. */
    @Nullable
    public static MiniChatFormatterCompat create(@Nonnull NicknamePlaceholders placeholders) {
        PluginIdentifier id = PluginDetector.MINI_CHAT_FORMATTER;
        PluginManager pluginManager = PluginManager.get();
        PluginBase plugin = pluginManager != null ? pluginManager.getPlugin(id) : null;
        if (plugin == null) return null;
        String version = plugin.getManifest().getVersion().toString();
        if (!version.startsWith("0.1.")) {
            LOGGER.at(Level.WARNING).log("mini-chat-formatter %s is not supported (0.1.x is); use %%nnc_nickname_mini%% "
                + "(PlaceholderAPI) instead of <username> in its format.", version);
            return null;
        }
        try {
            MiniChatFormatterCompat compat = new MiniChatFormatterCompat(placeholders);
            LOGGER.at(Level.INFO).log("mini-chat-formatter found: <username> in its format shows NNC nicknames.");
            return compat;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not hook into mini-chat-formatter %s; "
                + "use %%nnc_nickname_mini%% (PlaceholderAPI) in its format instead.", version);
            return null;
        }
    }

    /** LATE priority: replace MCF's formatter by the cached adapted one. */
    public void onPlayerChat(@Nonnull PlayerChatEvent event) {
        if (event.isCancelled()) return;
        PlayerChatEvent.Formatter formatter = event.getFormatter();
        if (!formatterClass.isInstance(formatter)) return;
        PlayerChatEvent.Formatter replacement = adapted.computeIfAbsent(formatter, this::adapt);
        if (replacement != formatter) {
            event.setFormatter(replacement);
        }
    }

    private PlayerChatEvent.Formatter adapt(PlayerChatEvent.Formatter original) {
        try {
            String format = (String) getFormat.invoke(original);
            if (!USERNAME_TAG.matcher(format).find()) return original;
            String nicknameFormat = USERNAME_TAG.matcher(format).replaceAll("<" + TAG + ">");
            return (PlayerChatEvent.Formatter) formatterConstructor.newInstance(nicknameFormat, LOGGER, configFunction);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("Could not adapt the mini-chat-formatter format; it shows real names.");
            return original;
        }
    }

    /** What MCF's own constructor installs (LuckPerms, PlaceholderAPI if the format uses them), plus the nickname. */
    private void installResolvers(String format, Object builder) throws ReflectiveOperationException {
        Object luckPerms = hookInitIfRequired.invoke(luckPermsHook, format, LOGGER);
        if (luckPerms != null) {
            builderResolver.invoke(builder, luckPermsResolver.newInstance(luckPerms));
        }
        Object placeholderApi = hookInitIfRequired.invoke(placeholderApiHook, format, LOGGER);
        if (placeholderApi != null) {
            builderResolver.invoke(builder, placeholderApiResolver.newInstance(placeholderApi, LOGGER));
        }
        builderResolver.invoke(builder, nicknameResolver);
    }

    /** {@code <nnc_username>}: nickname (or real name, escaped) in MiniMessage, parsed the way MCF parses prefixes. */
    @Nullable
    private Object nicknameTag(Object parseContext) {
        try {
            PlayerRef sender = (PlayerRef) chatSender.invoke(contextTarget.invoke(parseContext));
            String value = placeholders.resolve(sender.getUuid(), sender.getUsername(), "nickname_mini");
            return parseFormattedText.invoke(null, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Unresolved tag instead of failing the whole chat message
            LOGGER.at(Level.WARNING).withCause(e).log("Could not resolve <%s>", TAG);
            return null;
        }
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "NicknameChanger mini-chat-formatter adapter";
            default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
        };
    }
}
