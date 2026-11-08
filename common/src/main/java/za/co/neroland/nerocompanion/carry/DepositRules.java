package za.co.neroland.nerocompanion.carry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import za.co.neroland.nerocompanion.authority.Decision;

/**
 * Binding and depositing, pure: {@code OwnerAuthority.mayOpen} is the gate both at bind time and at every single
 * deposit (a container that was fine yesterday may be in a claim or locked today). A denied deposit moves nothing
 * and never calls the insert function. "Moved" is counted by item count, never by object identity (an insert may
 * hand back the same, mutated stack).
 */
public final class DepositRules {

    private DepositRules() {
    }

    /**
     * Result of a deposit: what is left per cargo slot, how many items moved, and the denial if any. {@code failed}
     * means the container threw part-way; {@code remaining} then still reflects what had moved before the throw.
     */
    public record Result<S>(List<S> remaining, int moved, boolean denied, String reasonKey, boolean failed) {

        public boolean movedAny() {
            return this.moved > 0;
        }
    }

    /** Whether a container may be bound. */
    public static Decision bind(boolean isContainer, Decision mayOpen) {
        if (!isContainer) {
            return Decision.deny("not_container");
        }
        return mayOpen;
    }

    /**
     * Moves each cargo slot into the container through {@code insert} (returns the leftover) - only if
     * {@code mayOpen} allows it. {@code cargo} entries are handed to {@code insert} as they are, so pass copies; the
     * caller writes {@code remaining} back.
     */
    public static <S> Result<S> deposit(Decision mayOpen, List<S> cargo, UnaryOperator<S> insert,
            ToIntFunction<S> count) {
        if (mayOpen.denied()) {
            return new Result<>(List.copyOf(cargo), 0, true, mayOpen.reasonKey(), false);
        }
        List<S> remaining = new ArrayList<>(cargo.size());
        int moved = 0;
        boolean failed = false;
        for (S stack : cargo) {
            int before = count.applyAsInt(stack);
            if (failed || before <= 0) {
                remaining.add(stack);
                continue;
            }
            S left;
            try {
                left = insert.apply(stack);
            } catch (RuntimeException broken) {
                failed = true;
                remaining.add(stack);
                continue;
            }
            moved += Math.max(0, before - count.applyAsInt(left));
            remaining.add(left);
        }
        return new Result<>(remaining, moved, false, "", failed);
    }
}
