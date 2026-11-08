package za.co.neroland.nerocompanion.chip;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import za.co.neroland.nerocompanion.entity.ChipStack;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * What installed chips do to a companion's numbers (pure). Magnitudes are the energy balance's (efficiency
 * -15/25/35 %, capacity +25/50/75 %), not Core's {@code UpgradeModifiers} (which has no tiers). Bond, skins and
 * hats never change any of these.
 */
public final class ChipEffects {

    private static final double[] EFFICIENCY = {0.15D, 0.25D, 0.35D};
    private static final double[] CAPACITY = {0.25D, 0.50D, 0.75D};

    private ChipEffects() {
    }

    /** The installed tier of {@code kind}, or 0. */
    public static int tierOf(List<ChipStack> chips, ChipKind kind) {
        for (ChipStack chip : chips) {
            if (chip.chip().equals(kind.id())) {
                return Math.max(0, Math.min(kind.maxTier(), chip.tier()));
            }
        }
        return 0;
    }

    public static boolean has(List<ChipStack> chips, ChipKind kind) {
        return tierOf(chips, kind) > 0;
    }

    /** Movement and work speed multiplier (+10 % per tier). */
    public static double speedMultiplier(List<ChipStack> chips) {
        return 1.0D + 0.10D * tierOf(chips, ChipKinds.SPEED);
    }

    /** Energy drain multiplier (efficiency). */
    public static double drainMultiplier(List<ChipStack> chips) {
        int tier = tierOf(chips, ChipKinds.EFFICIENCY);
        return tier == 0 ? 1.0D : 1.0D - EFFICIENCY[tier - 1];
    }

    /** Battery capacity multiplier. */
    public static double capacityMultiplier(List<ChipStack> chips) {
        int tier = tierOf(chips, ChipKinds.CAPACITY);
        return tier == 0 ? 1.0D : 1.0D + CAPACITY[tier - 1];
    }

    /** Extra scan / work range in blocks. */
    public static int rangeBonus(List<ChipStack> chips) {
        return 2 * tierOf(chips, ChipKinds.RANGE);
    }

    public static double armourBonus(List<ChipStack> chips) {
        return 2.0D * tierOf(chips, ChipKinds.ARMOUR);
    }

    public static double healthBonus(List<ChipStack> chips) {
        return 4.0D * tierOf(chips, ChipKinds.VITALITY);
    }

    /** Cargo slots the chips add to the chassis' own {@code cargo_slots} (cargo rack: +9). */
    public static int cargoSlots(List<ChipStack> chips) {
        return 9 * tierOf(chips, ChipKinds.CARGO_RACK);
    }

    /** Applies the drain multiplier to a per-second drain, rounding to the nearest FE (never below 0). */
    public static long drain(long perSecond, List<ChipStack> chips) {
        return Math.max(0L, Math.round(perSecond * drainMultiplier(chips)));
    }

    /** Applies the capacity multiplier to a chassis capacity. */
    public static long capacity(long base, List<ChipStack> chips) {
        return Math.max(1L, Math.round(base * capacityMultiplier(chips)));
    }

    /** Modes the chips add on top of the chassis' own. */
    public static Set<CompanionMode> unlockedModes(List<ChipStack> chips) {
        EnumSet<CompanionMode> modes = EnumSet.noneOf(CompanionMode.class);
        for (ChipStack chip : chips) {
            ChipKinds.byId(chip.chip()).flatMap(ChipKind::unlocks).ifPresent(modes::add);
        }
        return modes;
    }

    /** The capability chip that unlocks {@code mode}, if any. */
    public static java.util.Optional<ChipKind> unlockerOf(CompanionMode mode) {
        for (ChipKind kind : ChipKinds.all()) {
            if (kind.unlocks().filter(mode::equals).isPresent()) {
                return java.util.Optional.of(kind);
            }
        }
        return java.util.Optional.empty();
    }
}
