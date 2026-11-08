package za.co.neroland.nerocompanion.compat.nerospace;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerospace.api.Hazard;
import za.co.neroland.nerospace.api.NerospacePlanets;
import za.co.neroland.nerospace.api.PlanetId;

/** The <b>only</b> class that names a Nerospace type; loaded only from {@link NerospaceCompat}. Public API only. */
final class NerospaceBridge {

    private NerospaceBridge() {
    }

    static void probe() {
        NerospacePlanets.class.getName();
    }

    static OffWorld environment(ServerLevel level, BlockPos pos) {
        Optional<PlanetId> planet = NerospacePlanets.byDimension(level.dimension());
        double gravity = planet.isPresent() ? NerospacePlanets.traits(planet.get()).defaultGravity() : 1.0D;
        boolean radiation = NerospacePlanets.hazardAt(level, pos) == Hazard.RADIATION;
        return new OffWorld(true, gravity, radiation);
    }
}
