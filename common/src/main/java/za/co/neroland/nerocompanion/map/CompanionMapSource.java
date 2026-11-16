package za.co.neroland.nerocompanion.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import za.co.neroland.nerocompanion.charge.CompanionBayBlockEntity;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.network.ServerActions;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerolandcore.map.MapViewer;

/**
 * What the map provider reads from the world: the viewer's own ledger entries, where a loaded companion stands,
 * and whether a home is a Bay. The server implementation makes the same lookups as the link module's live view
 * (ledger, companion index, entity index, loaded block entity) and never loads a chunk.
 */
public interface CompanionMapSource {

    /** The viewer's own Recovery Ledger entries (every state). */
    List<LedgerEntry> entriesOf(MapViewer viewer);

    /** Where a LIVE entry's companion stands, if it is loaded and still the entry owner's. */
    Optional<HomePos> liveAt(MapViewer viewer, LedgerEntry entry);

    /** Whether this home is a Companion Bay (an unloaded home reads as a Bay, as in the link module). */
    boolean isBay(MapViewer viewer, HomePos home);

    CompanionMapSource SERVER = new CompanionMapSource() {
        @Override
        public List<LedgerEntry> entriesOf(MapViewer viewer) {
            MinecraftServer server = viewer.server();
            return server == null ? List.of() : Recovery.book(server).entriesOf(viewer.id());
        }

        @Override
        public Optional<HomePos> liveAt(MapViewer viewer, LedgerEntry entry) {
            MinecraftServer server = viewer.server();
            if (server == null) return Optional.empty();
            CompanionEntity companion = ServerActions.find(server, entry.companionId());
            UUID owner = entry.owner();
            if (companion == null || companion.isInert() || !owner.equals(companion.ownerId())) return Optional.empty();
            return Optional.of(Recovery.at(companion));
        }

        @Override
        public boolean isBay(MapViewer viewer, HomePos home) {
            MinecraftServer server = viewer.server();
            if (server == null) return false;
            for (ServerLevel level : server.getAllLevels()) {
                if (!home.isIn(level)) continue;
                if (!level.isLoaded(home.pos())) return true;
                BlockEntity block = level.getBlockEntity(home.pos());
                return block instanceof CompanionBayBlockEntity;
            }
            return false;
        }
    };
}
