package com.nickname.plugin.display;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.serverplayerlist.ServerPlayerListModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.nickname.plugin.config.PluginConfig;
import com.nickname.plugin.storage.NicknameStorage;
import com.nickname.plugin.util.MessageUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The single place that shows nicknames outside of chat: nameplate, tab list and world map.
 * <p>
 * The player's real username ({@link PlayerRef#getUsername()}) is never changed, so commands and
 * other plugins keep resolving players by their real name. Instead:
 * <ul>
 *   <li>tab list: every outgoing {@link AddToServerPlayerList} packet (join, world change,
 *       unhide, spectate, lives, our own refreshes) gets the nickname for the entry's UUID;</li>
 *   <li>map: the server's "playerIcons" marker provider is wrapped and the marker label replaced;</li>
 *   <li>nameplate: the Nameplate / DisplayNameComponent of the entity is set.</li>
 * </ul>
 */
public final class NicknameDisplay {

    private static final String PLAYER_ICONS_PROVIDER = "playerIcons";

    private final NicknameStorage storage;
    private final PluginConfig config;
    @Nullable
    private PacketFilter tabListFilter;

    public NicknameDisplay(@Nonnull NicknameStorage storage, @Nonnull PluginConfig config) {
        this.storage = storage;
        this.config = config;
    }

    public void start() {
        tabListFilter = PacketAdapters.registerOutbound((PacketWatcher) this::renameTabListEntries);
        for (World world : Universe.get().getWorlds().values()) {
            installMapMarkers(world);
        }
    }

    public void stop() {
        if (tabListFilter != null) {
            PacketAdapters.deregisterOutbound(tabListFilter);
            tabListFilter = null;
        }
        for (World world : Universe.get().getWorlds().values()) {
            world.getWorldMapManager().getMarkerProviders().computeIfPresent(PLAYER_ICONS_PROVIDER,
                (key, provider) -> provider instanceof NicknameMarkerProvider wrapper ? wrapper.delegate : provider);
        }
    }

    public void onAddWorld(@Nonnull AddWorldEvent event) {
        installMapMarkers(event.getWorld());
    }

    /** Plain nickname shown on nameplate / tab / map, or {@code null} if the player has none. */
    @Nullable
    private String plainNickname(@Nonnull UUID uuid) {
        String nickname = storage.getNickname(uuid);
        return nickname != null ? MessageUtil.stripTags(nickname) : null;
    }

    /**
     * Sets the nameplate to the nickname (if nameplates are enabled) or the real name.
     * Must run on the player's world thread.
     */
    public void applyNameplate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        if (!ref.isValid()) return;
        String nickname = config.display.showOnNameplate ? plainNickname(playerRef.getUuid()) : null;
        String name = nickname != null ? nickname : playerRef.getUsername();

        Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
        nameplate.setText(name);
        // Put, never remove: removing DisplayNameComponent clears the nameplate to "" (NameplateRefChangeSystem).
        // Plain text only: nameplates can't render per-character gradients.
        store.putComponent(ref, DisplayNameComponent.getComponentType(), new DisplayNameComponent(Message.raw(name)));
    }

    /** Re-sends this player's tab list entry to everyone allowed to see it (respects vanish). */
    public void refreshTabList(@Nonnull PlayerRef playerRef) {
        ServerPlayerListModule.get().broadcastListEntry(playerRef);
    }

    /** Nameplate + tab list after the player's nickname changed. Must run on the player's world thread. */
    public void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        applyNameplate(ref, store, playerRef);
        refreshTabList(playerRef);
    }

    /** Re-applies display settings for every online player that has a nickname (each on its own world thread). */
    public void refreshAll() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (!storage.hasNickname(playerRef.getUuid())) continue;
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            if (world == null) continue;
            world.execute(() -> refresh(ref, store, playerRef));
        }
    }

    // --- Tab list ---

    private void renameTabListEntries(PacketHandler handler, Packet packet) {
        if (!(packet instanceof AddToServerPlayerList list) || !config.display.showInTabList) return;
        // The same packet object may go to several viewers; the rename only depends on the entry's UUID.
        for (ServerPlayerListPlayer entry : list.players) {
            String nickname = plainNickname(entry.uuid);
            if (nickname != null) {
                entry.username = nickname;
            }
        }
    }

    // --- World map ---

    private void installMapMarkers(@Nonnull World world) {
        world.getWorldMapManager().getMarkerProviders().computeIfPresent(PLAYER_ICONS_PROVIDER,
            (key, provider) -> provider instanceof NicknameMarkerProvider ? provider : new NicknameMarkerProvider(provider));
    }

    private MapMarker renameMarker(MapMarker marker) {
        if (marker.components == null) return marker;
        for (MapMarkerComponent component : marker.components) {
            if (component instanceof PlayerMarkerComponent playerMarker) {
                String nickname = plainNickname(playerMarker.playerId);
                if (nickname != null) {
                    // Markers are built fresh for every update; only the label changes
                    marker.name = Message.raw(nickname).getFormattedMessage();
                }
                break;
            }
        }
        return marker;
    }

    /** Wraps the server's player marker provider and replaces player labels with nicknames. */
    private final class NicknameMarkerProvider implements WorldMapManager.MarkerProvider {
        private final WorldMapManager.MarkerProvider delegate;

        NicknameMarkerProvider(WorldMapManager.MarkerProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
            if (!config.display.showOnMap) {
                delegate.update(world, player, collector);
                return;
            }
            delegate.update(world, player, new MarkersCollector() {
                @Override
                public void add(MapMarker marker) {
                    collector.add(renameMarker(marker));
                }

                @Override
                public void addIgnoreViewDistance(MapMarker marker) {
                    collector.addIgnoreViewDistance(renameMarker(marker));
                }

                @Override
                @SuppressWarnings("deprecation")
                public Predicate<PlayerRef> getPlayerMapFilter() {
                    return collector.getPlayerMapFilter();
                }

                @Override
                public boolean isInViewDistance(double x, double z) {
                    return collector.isInViewDistance(x, z);
                }
            });
        }
    }
}
