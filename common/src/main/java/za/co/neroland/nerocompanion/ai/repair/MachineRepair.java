package za.co.neroland.nerocompanion.ai.repair;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * Seam for repairing machines (a sibling mod's damaged block entity). <b>A no-op in 0.5.0</b>: no Neroland mod
 * exposes machine wear yet, so {@link #NONE} is the only implementation and the Repair Bot never calls into the
 * world through it. A later compat facade can {@link #set} an implementation that checks
 * {@code OwnerAuthority.mayOpen} for the position before touching anything.
 */
public interface MachineRepair {

    /** The 0.5.0 behaviour: nothing to repair, nothing spent. */
    MachineRepair NONE = new MachineRepair() {
    };

    /**
     * Repairs up to {@code maxPoints} of wear on the machine at {@code pos} as {@code bot}.
     *
     * @return points actually restored (the caller pays {@link RepairMath#FE_PER_POINT} each); 0 by default
     */
    default int repair(CompanionEntity bot, ServerLevel level, BlockPos pos, int maxPoints) {
        return 0;
    }

    static MachineRepair get() {
        return Holder.current;
    }

    /** Installs an implementation; {@code null} restores {@link #NONE}. */
    static void set(MachineRepair repair) {
        Holder.current = repair == null ? NONE : repair;
    }

    /** Holder for the installed implementation. */
    final class Holder {
        private static volatile MachineRepair current = NONE;

        private Holder() {
        }
    }
}
