package za.co.neroland.nerocompanion.chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.type.ChipCategory;

/**
 * The one chip rule book (pure): what may be inserted where and what may be removed. The server runs it for every
 * {@code ChipEdit} and for chips arriving in a record; the client never decides. Slots are compacted (no holes):
 * an insert goes into the next free slot, a removal shifts later chips left.
 */
public final class ChipValidation {

    /** Why an edit was refused ({@link #OK} = accepted). Each maps to a reason lang key on the screens. */
    public enum Outcome {
        OK("ok"),
        NOT_A_CHIP("empty_hand"),
        BAD_SLOT("chip_slot_empty"),
        FULL("chip_full"),
        WRONG_CATEGORY("wrong_category"),
        DUPLICATE("duplicate"),
        EXCLUSIVE("exclusive"),
        BAD_TIER("bad_tier"),
        SLOT_EMPTY("chip_slot_empty");

        private final String reason;

        Outcome(String reason) {
            this.reason = reason;
        }

        /** {@code gui.nerocompanion.reason.<reason>}. */
        public String reasonKey() {
            return "gui.nerocompanion.reason." + this.reason;
        }
    }

    /** An outcome plus, for {@link Outcome#EXCLUSIVE}, the installed kind it clashes with. */
    public record Result(Outcome outcome, Optional<Identifier> clash) {
        public static final Result OK = new Result(Outcome.OK, Optional.empty());

        static Result of(Outcome outcome) {
            return new Result(outcome, Optional.empty());
        }

        public boolean ok() {
            return this.outcome == Outcome.OK;
        }
    }

    /** What a chassis offers: slot count and accepted categories. */
    public record Chassis(int slots, Set<ChipCategory> categories) {
        public Chassis {
            categories = Set.copyOf(categories);
            slots = Math.max(0, slots);
        }
    }

    private ChipValidation() {
    }

    /**
     * May {@code kind} at {@code tier} go into {@code slot} ({@code -1} = the first free slot)?
     *
     * @param kind null when the held item is not a chip
     */
    public static Result insert(List<ChipStack> installed, Chassis chassis, int slot, ChipKind kind, int tier,
            ChipRules.Exclusions exclusions) {
        if (kind == null) {
            return Result.of(Outcome.NOT_A_CHIP);
        }
        if (slot < -1 || slot >= chassis.slots()) {
            return Result.of(Outcome.BAD_SLOT);
        }
        if (installed.size() >= chassis.slots()) {
            return Result.of(Outcome.FULL);
        }
        if (!chassis.categories().contains(kind.category())) {
            return Result.of(Outcome.WRONG_CATEGORY);
        }
        List<Identifier> kinds = new ArrayList<>();
        for (ChipStack chip : installed) {
            if (chip.chip().equals(kind.id())) {
                return Result.of(Outcome.DUPLICATE);
            }
            kinds.add(chip.chip());
        }
        Optional<Identifier> clash = exclusions.clashWith(kind.id(), kinds);
        if (clash.isPresent()) {
            return new Result(Outcome.EXCLUSIVE, clash);
        }
        if (tier < 1 || tier > kind.maxTier()) {
            return Result.of(Outcome.BAD_TIER);
        }
        return Result.OK;
    }

    /** May the chip in {@code slot} be taken out? */
    public static Result remove(List<ChipStack> installed, int slot) {
        return slot < 0 || slot >= installed.size() ? Result.of(Outcome.SLOT_EMPTY) : Result.OK;
    }

    /** {@code installed} with the chip appended (callers validated first). */
    public static List<ChipStack> withInserted(List<ChipStack> installed, ChipStack chip) {
        List<ChipStack> next = new ArrayList<>(installed);
        next.add(chip);
        return List.copyOf(next);
    }

    /** {@code installed} without the chip at {@code slot}; later chips shift left. */
    public static List<ChipStack> withRemoved(List<ChipStack> installed, int slot) {
        List<ChipStack> next = new ArrayList<>(installed);
        next.remove(slot);
        return List.copyOf(next);
    }

    /**
     * Splits a record's chips into those that are valid together on {@code chassis} (in order) and those that are
     * not (unknown kind, wrong category, duplicate, clash, over the slot count). Nothing is ever silently lost:
     * the caller keeps or returns the rejected ones.
     */
    public static Split sanitize(List<ChipStack> chips, Chassis chassis, ChipRules.Exclusions exclusions,
            Function<Identifier, Optional<ChipKind>> lookup) {
        List<ChipStack> kept = new ArrayList<>();
        List<ChipStack> rejected = new ArrayList<>();
        for (ChipStack chip : chips) {
            ChipKind kind = lookup.apply(chip.chip()).orElse(null);
            if (kind != null && insert(kept, chassis, -1, kind, chip.tier(), exclusions).ok()) {
                kept.add(chip);
            } else {
                rejected.add(chip);
            }
        }
        return new Split(List.copyOf(kept), List.copyOf(rejected));
    }

    public record Split(List<ChipStack> kept, List<ChipStack> rejected) {
    }
}
