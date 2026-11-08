package za.co.neroland.nerocompanion.entity.type;

import net.minecraft.resources.Identifier;

/**
 * One problem found while loading a companion type file. Problems are reported, never thrown: a bad field
 * falls back to the chassis default ({@link Severity#FIELD_DEFAULTED}), and a file that cannot describe a
 * companion at all is skipped ({@link Severity#DROPPED}). Carries only resource ids and messages - no player
 * data - so it is safe to log and to show in {@code /nscomp types}.
 */
public record ValidationIssue(Severity severity, Identifier typeId, String field, String message) {

    public enum Severity {
        /** The field was ignored and the built-in default used. */
        FIELD_DEFAULTED,
        /** The whole type file was skipped. */
        DROPPED
    }

    public String describe() {
        return this.typeId + " [" + this.field + "] " + this.message
                + (this.severity == Severity.DROPPED ? " (type skipped)" : " (default used)");
    }
}
