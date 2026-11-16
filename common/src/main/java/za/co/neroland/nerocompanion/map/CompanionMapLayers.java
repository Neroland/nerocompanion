package za.co.neroland.nerocompanion.map;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerolandcore.map.MapLayerRegistry;

/**
 * NeroCompanion's map layers for any Neroland map consumer (Neroland Core's map API). Both are owner-only:
 * {@code nerocompanion:companions} (the viewer's companions out in the world, and the last-known spot of lost
 * ones) and {@code nerocompanion:home_bays} (the Bays the viewer's companions are parked in or call home). The
 * Salvage Hold has no position and is not a layer. Nothing new is stored.
 */
public final class CompanionMapLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger("NeroCompanion/Map");

    public static final Identifier COMPANIONS = id("companions");
    public static final Identifier HOME_BAYS = id("home_bays");
    static final Identifier ICON_COMPANION = id("companion");
    static final Identifier ICON_LOST = id("companion_lost");
    static final Identifier ICON_BAY = id("home_bay");

    private CompanionMapLayers() {
    }

    /** Registers the provider with Core. Never throws. Called once from {@code NeroCompanionCommon.init()}. */
    public static void register() {
        try {
            MapLayerRegistry.register(new CompanionMapLayerProvider(CompanionMapSource.SERVER));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("NeroCompanion map layers not registered ({})", e.getClass().getSimpleName());
        }
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, path);
    }
}
