package za.co.neroland.nerocompanion.link;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;

/**
 * What the link sections read from the world (the live server in {@link LiveCompanionLink}, a fake in tests). Every
 * method is asked about one owner's own companions; positions come in so the sections can compute a coarse
 * {@code areaHint} and never go out.
 */
public interface CompanionLinkView {

    /** What a home block is. */
    enum HomeKind {
        BAY("bay"),
        PAD("pad"),
        NONE("none");

        private final String id;

        HomeKind(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    /** A loaded companion entity's current state (fresher than the ledger snapshot). */
    record Live(CompanionMode mode, int charge, HomePos at, Optional<String> name, int bondPoints,
            Optional<Identifier> skin, Optional<Identifier> hat, Optional<HomePos> home,
            List<CompanionMode> supportedModes) {

        public Live {
            supportedModes = supportedModes == null ? List.of() : List.copyOf(supportedModes);
        }
    }

    /** The owner's Salvage Hold: how many items and when it expires (wall-clock millis). */
    record SalvageView(int count, long expiresAtMillis) {
    }

    long nowMillis();

    /** The ledger entries of {@code owner} (every state). */
    List<LedgerEntry> entriesOf(UUID owner);

    /** The loaded entity of a LIVE entry, if it is loaded and still {@code entry.owner()}'s. */
    Optional<Live> live(LedgerEntry entry);

    /** Where the online owner stands; empty when offline. Used for {@code near_you} only. */
    Optional<HomePos> ownerAt(UUID owner);

    /** Charge in percent of a stored record. */
    int chargePercent(CompanionRecord record);

    /** The modes this chassis supports, in mode order (stored rows; a loaded entity's {@link Live} adds chip modes). */
    List<CompanionMode> supportedModes(Identifier type, CompanionMode current);

    HomeKind homeKind(HomePos home);

    Optional<SalvageView> salvage(UUID owner);
}
