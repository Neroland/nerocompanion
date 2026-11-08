package za.co.neroland.nerocompanion.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.RecoveryBook;

/**
 * The Recovery Ledger (overworld SavedData, through Core's {@link SavedDataRecovery} guard): one entry per companion
 * - owner UUID, state (LIVE / CAPSULE / PARKED / LOST), ticket, entity UUID, Bay, last-known dimension + position,
 * last-seen tick and a record snapshot without cargo (its home, bound container and mining area stay), plus owner-less
 * erasure tombstones. The logic is {@link RecoveryBook}; this class only persists
 * it (every book write marks the store dirty).
 *
 * <p>Player data: the owner's own. Shown only to them and in their own export, erased by owner UUID (with a
 * {@code backupNow}), purged by {@code privacy.ledgerRetentionDays}: all of an inactive owner's entries, and single
 * CAPSULE/LOST entries and tombstones not used for that long (the retention sweep refreshes the backup too).
 */
public final class RecoveryLedger extends SavedData {

    /** A tombstone: companion id and erasure time, nothing else. */
    private record Tomb(UUID id, long at) {
        static final Codec<Tomb> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Tomb::id),
                Codec.LONG.optionalFieldOf("at", 0L).forGetter(Tomb::at)
        ).apply(i, Tomb::new));
    }

    private static final Codec<RecoveryLedger> CODEC = RecordCodecBuilder.create(i -> i.group(
            LedgerEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(ledger -> ledger.book.all()),
            Tomb.CODEC.listOf().optionalFieldOf("tombstones", List.of()).forGetter(ledger -> ledger.book.tombstones()
                    .entrySet().stream().map(e -> new Tomb(e.getKey(), e.getValue())).toList())
    ).apply(i, RecoveryLedger::new));

    public static final SavedDataType<RecoveryLedger> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "recovery_ledger"), RecoveryLedger::new, CODEC);
    public static final String RECOVERY_NAME = "nerocompanion:recovery_ledger";

    private final RecoveryBook book;

    public RecoveryLedger() {
        this(List.of(), List.of());
    }

    private RecoveryLedger(List<LedgerEntry> entries, List<Tomb> tombs) {
        Map<UUID, Long> tombstones = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        tombs.forEach(t -> tombstones.put(t.id(), t.at() > 0L ? t.at() : now));
        this.book = new RecoveryBook(entries, tombstones);
        this.book.onChange(this::setDirty);
    }

    public static RecoveryLedger get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, RecoveryLedger::new, RECOVERY_NAME);
    }

    /** The ledger's book for {@code server}. */
    public static RecoveryBook book(MinecraftServer server) {
        return get(server).book;
    }

    public RecoveryBook bookView() {
        return this.book;
    }
}
