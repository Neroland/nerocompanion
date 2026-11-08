package za.co.neroland.nerocompanion.link;

import java.util.List;
import java.util.OptionalInt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.alert.AlertText;
import za.co.neroland.nerocompanion.alert.CompanionAlerts;
import za.co.neroland.nerocompanion.bond.BondEvents;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * NeroCompanion's plug into Neroland Core's link API (decision 013), registered last from
 * {@link NeroCompanionCommon#init()} inside its try/catch: module id {@value #MODULE_ID}, schema
 * {@value #SCHEMA_VERSION}, sections {@code roster}, {@code salvage}, {@code bay}, action {@code companion}.
 *
 * <ul>
 *   <li><b>Read</b> - {@link CompanionLinkSnapshots}: the requester's own companions by owner UUID from the Recovery
 *       Ledger (parked and lost included), their Salvage Hold, the Bays they call home. Never a server-wide roster,
 *       never a coordinate.</li>
 *   <li><b>Write</b> - {@link CompanionLinkActions}: {@code set_mode}, {@code recall}, {@code reclaim},
 *       {@code rename}, ownership-checked against the ledger and rate-limited.</li>
 *   <li><b>Live</b> - {@link CompanionLinkEvents}: five notices, each a rich event first and the Core alert second;
 *       no roster deltas.</li>
 * </ul>
 *
 * <p>The module costs one registry entry with no NeroLink bridge installed. A failure here never stops the mod.
 */
public final class CompanionLinkModule {

    public static final String MODULE_ID = NeroCompanionCommon.MOD_ID;
    /** Additive changes keep 1 (decision 013); bump on any change to an existing field's shape. */
    public static final int SCHEMA_VERSION = 1;

    public static final String SECTION_ROSTER = "roster";
    public static final String SECTION_SALVAGE = "salvage";
    public static final String SECTION_BAY = "bay";
    public static final String ACTION_COMPANION = "companion";

    public static final List<String> SECTIONS = List.of(SECTION_ROSTER, SECTION_SALVAGE, SECTION_BAY);
    public static final List<String> ACTIONS = List.of(ACTION_COMPANION);

    /** Bond level-ups below this are not worth a notification (decision 013: levels 2-4). */
    static final int FIRST_NOTIFIED_BOND_LEVEL = 2;

    /** The running server, captured from the server tick; null before the first tick and after shutdown. */
    private static volatile MinecraftServer server;

    private CompanionLinkModule() {
    }

    /** Registers the read, write and live surfaces with Core. Called last from common init. */
    public static void init() {
        LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION, SECTIONS, ACTIONS);
        NeroLinkRegistry.registerSnapshotProvider(new CompanionLinkSnapshots(CompanionLinkModule::view), info);
        NeroLinkRegistry.registerActionHandler(new CompanionLinkActions(CompanionLinkModule::ops), info);
        BondEvents.onLevelUp(CompanionLinkModule::onBondLevel);
    }

    /** Each server tick: remember the running server for the bridge's calls. */
    public static void tick(MinecraftServer running) {
        server = running;
    }

    public static void serverStopped() {
        server = null;
        CompanionLinkEvents.clear();
    }

    @Nullable
    private static CompanionLinkView view() {
        MinecraftServer running = server;
        return running == null ? null : new LiveCompanionLink(running);
    }

    @Nullable
    private static CompanionLinkActions.Ops ops() {
        MinecraftServer running = server;
        return running == null ? null : new LiveCompanionLink(running);
    }

    /** {@code BOND_LEVEL} for levels 2-4, to the companion's stored owner. */
    static void onBondLevel(BondEvents.LevelUp up) {
        if (up == null || up.to() < FIRST_NOTIFIED_BOND_LEVEL || up.to() <= up.from()) {
            return;
        }
        CompanionEntity companion = up.companion();
        if (companion == null || companion.ownerId() == null || companion.companionId() == null
                || !(companion.level() instanceof ServerLevel level)) {
            return;
        }
        CompanionAlerts.raise(level.getServer(), companion.ownerId(), AlertText.Kind.BOND_LEVEL,
                companion.companionId(), companion.typeId(), companion.companionName(), OptionalInt.empty(),
                OptionalInt.of(up.to()));
    }

    private static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }
}
