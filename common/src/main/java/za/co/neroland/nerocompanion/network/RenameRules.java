package za.co.neroland.nerocompanion.network;

import java.util.Optional;

import za.co.neroland.nerocompanion.entity.CompanionRecord;

/**
 * The rename rule (pure): strip formatting codes and control characters, cap at 24 characters, then ask the
 * server's text filter. The filter result is applied by the caller on the server thread once the (possibly
 * asynchronous) filter answers; this class only decides.
 */
public final class RenameRules {

    /** What happens to a rename. */
    public enum Outcome {
        RENAMED("gui.nerocompanion.rename.ok"),
        CLEARED("gui.nerocompanion.rename.cleared"),
        SAME("gui.nerocompanion.reason.same_name"),
        FILTERED("gui.nerocompanion.reason.filtered");

        private final String key;

        Outcome(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }
    }

    public record Result(Outcome outcome, Optional<String> name) {
    }

    /** A filter verdict for one string: whether anything in it was filtered. */
    @FunctionalInterface
    public interface Verdict {
        boolean filtered(String text);
    }

    private RenameRules() {
    }

    /** Removes {@code §} formatting codes (and a dangling {@code §}), then trims, drops control chars and caps. */
    public static String clean(String raw) {
        String text = raw == null ? "" : raw;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            out.append(c);
        }
        return CompanionRecord.capName(out.toString());
    }

    /** Decides a rename of a companion currently named {@code current} to {@code requested}. */
    public static Result decide(Optional<String> current, String requested, Verdict filter) {
        String cleaned = clean(requested);
        if (cleaned.isEmpty()) {
            return current.isEmpty() ? new Result(Outcome.SAME, Optional.empty())
                    : new Result(Outcome.CLEARED, Optional.empty());
        }
        if (current.isPresent() && current.get().equals(cleaned)) {
            return new Result(Outcome.SAME, current);
        }
        if (filter.filtered(cleaned)) {
            return new Result(Outcome.FILTERED, Optional.empty());
        }
        return new Result(Outcome.RENAMED, Optional.of(cleaned));
    }
}
