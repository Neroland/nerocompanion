package za.co.neroland.nerocompanion.link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;

/** Shared test world for the link tests: a real Recovery Ledger and a fake rest of the world. */
final class LinkFixtures {

    static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
    /** Distinctive coordinates, so a leak is easy to spot in the JSON. */
    static final HomePos BAY_A = new HomePos(OVERWORLD, new BlockPos(1234, 71, -5678));
    static final HomePos BAY_B = new HomePos(OVERWORLD, new BlockPos(-4321, 65, 8765));

    private LinkFixtures() {
    }

    static CompanionRecord record(UUID owner, String type, CompanionMode mode, String name, Optional<HomePos> home) {
        return new CompanionRecord(CompanionRecord.SCHEMA, UUID.randomUUID(), owner,
                Identifier.fromNamespaceAndPath("nerocompanion", type), mode, 5_000L, List.of(), 420,
                Optional.ofNullable(name), Optional.empty(), Optional.empty(), 77L, home, new CompoundTag());
    }

    /** A fake world over a real ledger. */
    static final class World implements CompanionLinkView {
        final AtomicLong tickets = new AtomicLong(100L);
        final RecoveryBook book = new RecoveryBook(tickets::incrementAndGet);
        final Map<UUID, Live> live = new HashMap<>();
        final Map<UUID, HomePos> online = new HashMap<>();
        final Map<UUID, SalvageView> salvage = new HashMap<>();
        final Map<HomePos, HomeKind> homes = new HashMap<>();
        final List<UUID> entityIds = new ArrayList<>();

        CompanionRecord deploy(CompanionRecord record, HomePos at) {
            UUID entity = UUID.randomUUID();
            entityIds.add(entity);
            book.live(record, entity, at, 10L, 1L);
            return record;
        }

        CompanionRecord park(CompanionRecord record, HomePos bay) {
            book.parked(record, bay, 10L, 1L);
            return record;
        }

        CompanionRecord lose(CompanionRecord record, HomePos at) {
            UUID entity = UUID.randomUUID();
            entityIds.add(entity);
            book.live(record, entity, at, 10L, 1L);
            book.died(record, entity, at, 20L, 2L);
            return record;
        }

        @Override
        public long nowMillis() {
            return 1_789_000_000_000L;
        }

        @Override
        public List<LedgerEntry> entriesOf(UUID owner) {
            return book.entriesOf(owner);
        }

        @Override
        public Optional<Live> live(LedgerEntry entry) {
            return Optional.ofNullable(live.get(entry.companionId()));
        }

        @Override
        public Optional<HomePos> ownerAt(UUID owner) {
            return Optional.ofNullable(online.get(owner));
        }

        @Override
        public int chargePercent(CompanionRecord record) {
            return 50;
        }

        @Override
        public List<CompanionMode> supportedModes(Identifier type, CompanionMode current) {
            return List.of(CompanionMode.FOLLOW, CompanionMode.STAY, current, CompanionMode.SLEEP).stream()
                    .distinct().toList();
        }

        @Override
        public HomeKind homeKind(HomePos home) {
            return homes.getOrDefault(home, HomeKind.BAY);
        }

        @Override
        public Optional<SalvageView> salvage(UUID owner) {
            return Optional.ofNullable(salvage.get(owner));
        }
    }
}
