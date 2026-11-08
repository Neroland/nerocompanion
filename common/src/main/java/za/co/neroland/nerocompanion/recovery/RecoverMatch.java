package za.co.neroland.nerocompanion.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which of a player's lost companions {@code /nscomp recover [name]} means (pure). An empty query picks the only one
 * (or all, when there are several - the command then lists them). Otherwise an exact name match wins
 * (case-insensitive), then a type match ({@code mining_bot} or {@code mining bot}).
 */
public final class RecoverMatch {

    private RecoverMatch() {
    }

    public static List<LedgerEntry> select(List<LedgerEntry> lost, String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return lost;
        }
        List<LedgerEntry> byName = new ArrayList<>();
        for (LedgerEntry entry : lost) {
            if (entry.snapshot().name().map(n -> n.toLowerCase(Locale.ROOT).equals(q)).orElse(false)) {
                byName.add(entry);
            }
        }
        if (!byName.isEmpty()) {
            return byName;
        }
        String type = q.replace(' ', '_');
        List<LedgerEntry> byType = new ArrayList<>();
        for (LedgerEntry entry : lost) {
            if (entry.snapshot().type().getPath().equals(type)) {
                byType.add(entry);
            }
        }
        return byType;
    }
}
