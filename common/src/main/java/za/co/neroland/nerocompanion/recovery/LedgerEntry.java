package za.co.neroland.nerocompanion.recovery;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;

/**
 * One companion in the Recovery Ledger: who owns it, where it is (as a state), and a snapshot of everything it is
 * <em>without</em> cargo or job (see {@link ReclaimRecords#forLedger}).
 *
 * @param ticket       the ticket of the Capsule that currently holds it (CAPSULE), else the last one minted; 0 = none
 * @param entity       the entity UUID while LIVE (and the last one while LOST)
 * @param bay          the Bay holding it (PARKED)
 * @param lastKnown    last-known dimension + block position (the snapshot also keeps home and bound container)
 * @param lastSeenTick game time the companion last reported (or changed state)
 * @param clockFrom    game time the lost clock counts from (last seen, or the owner's last login)
 * @param lostAtTick   game time it became LOST (0 otherwise)
 * @param touchedAt    wall-clock millis of the last write (retention)
 * @param snapshot     the record to restore from
 */
public record LedgerEntry(
        UUID companionId,
        UUID owner,
        State state,
        long ticket,
        Optional<UUID> entity,
        Optional<HomePos> bay,
        Optional<HomePos> lastKnown,
        long lastSeenTick,
        long clockFrom,
        long lostAtTick,
        long touchedAt,
        CompanionRecord snapshot) {

    /** Where a companion is. */
    public enum State {
        /** Out in the world as {@code entity} (loaded or not). */
        LIVE,
        /** In a Capsule item (the one carrying {@code ticket}). */
        CAPSULE,
        /** In the Bay at {@code bay}. */
        PARKED,
        /** Gone: killed, or unloaded and unseen too long. Reclaimable. */
        LOST;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        static final Codec<State> CODEC = Codec.STRING.comapFlatMap(value -> {
            for (State state : values()) {
                if (state.id().equals(value)) {
                    return DataResult.success(state);
                }
            }
            return DataResult.error(() -> "unknown ledger state " + value);
        }, State::id);
    }

    public static final Codec<LedgerEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(LedgerEntry::companionId),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(LedgerEntry::owner),
            State.CODEC.fieldOf("state").forGetter(LedgerEntry::state),
            Codec.LONG.optionalFieldOf("ticket", 0L).forGetter(LedgerEntry::ticket),
            UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(LedgerEntry::entity),
            HomePos.CODEC.optionalFieldOf("bay").forGetter(LedgerEntry::bay),
            HomePos.CODEC.optionalFieldOf("at").forGetter(LedgerEntry::lastKnown),
            Codec.LONG.optionalFieldOf("seen", 0L).forGetter(LedgerEntry::lastSeenTick),
            Codec.LONG.optionalFieldOf("clock", 0L).forGetter(LedgerEntry::clockFrom),
            Codec.LONG.optionalFieldOf("lost_at", 0L).forGetter(LedgerEntry::lostAtTick),
            Codec.LONG.optionalFieldOf("touched", 0L).forGetter(LedgerEntry::touchedAt),
            CompanionRecord.CODEC.fieldOf("record").forGetter(LedgerEntry::snapshot)
    ).apply(i, LedgerEntry::new));

    public boolean is(State expected) {
        return this.state == expected;
    }

    /** Whether this entry is LIVE as {@code entityId}. */
    public boolean liveAs(UUID entityId) {
        return this.state == State.LIVE && this.entity.isPresent() && this.entity.get().equals(entityId);
    }
}
