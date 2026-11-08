package za.co.neroland.nerocompanion.alert;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The text of a companion alert (pure). An alert body names the companion's type and its (already filtered,
 * capped) name - nothing else: never a position, a dimension, a UUID or another player's name. Core stores the body
 * as plain text for NeroLink, so it is English (the source language).
 */
public final class AlertText {

    /** The four alerts Stage 4 raises, and the bond level-up (levels 2-4) Stage 8 adds. */
    public enum Kind {
        LOW_BATTERY("low_battery"),
        LOST("lost"),
        RECOVERED("recovered"),
        SALVAGE_HELD("salvage_held"),
        BOND_LEVEL("bond_level");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }
    }

    private AlertText() {
    }

    /** "Mining Bot" for {@code nerocompanion:mining_bot} (the path, title-cased; no namespace). */
    public static String typeName(String typePath) {
        String path = typePath == null ? "" : typePath;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.length() == 0 ? "Companion" : out.toString();
    }

    /** "your Carry Bot Rusty" / "your Carry Bot". */
    static String subject(String typeName, Optional<String> name) {
        return name.filter(n -> !n.isBlank()).map(n -> "Your " + typeName + " " + n).orElse("Your " + typeName);
    }

    /** The alert body. */
    public static String body(Kind kind, String typeName, Optional<String> name) {
        String who = subject(typeName, name);
        return switch (kind) {
            case LOW_BATTERY -> who + " is low on battery.";
            case LOST -> who + " is lost. Reclaim it from a Companion Bay or with /nscomp recover.";
            case RECOVERED -> who + " is back.";
            case SALVAGE_HELD -> who + " picked up your things after you fell. Talk to it to get them back.";
            case BOND_LEVEL -> who + " trusts you a little more: your bond grew.";
        };
    }

    /** A stable alert id per companion and kind (a re-raise replaces the previous one). Not a player id. */
    public static String id(Kind kind, UUID companionId) {
        return "nerocompanion:" + kind.id() + ":" + Long.toHexString(companionId.getMostSignificantBits()
                ^ companionId.getLeastSignificantBits());
    }

    /** Severity name for Core's {@code LinkAlert.Severity}. */
    public static String severity(Kind kind) {
        return switch (kind) {
            case LOW_BATTERY, LOST -> "WARN";
            case RECOVERED, SALVAGE_HELD, BOND_LEVEL -> "INFO";
        };
    }
}
