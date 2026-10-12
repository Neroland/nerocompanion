package za.co.neroland.nerocompanion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.economy.CompanionEconomy;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.progression.QuestHooks;
import za.co.neroland.nerocompanion.telemetry.NeroCompanionTelemetry;

/**
 * Loader-agnostic entry point for NeroCompanion. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. This is a barebones skeleton — no content is registered yet;
 * add shared blocks, items and systems here and reach loader-specific
 * behaviour through a platform seam.
 */
public final class NeroCompanionCommon {

    public static final String MOD_ID = "nerocompanion";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroCompanion");

    private NeroCompanionCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroCompanion] common init");

        // 0. Platform seam, resolved here during construction and never lazily on a tick path — a
        //    late ServiceLoader read can throw ServiceConfigurationError out of gameplay code.
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroCompanionConfig.init();

        // 2. Anonymous, NeroCompanion-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default (opt-out via
        //    telemetryEnabled=false); stays inert while the build's DSN is the placeholder
        //    (see NeroCompanionTelemetry's PLACEHOLDER_DSN guard).
        NeroCompanionTelemetry.init();

        // 3. Optional NeroEconomy bridge for cosmetic purchases and price display. Touches NeroEconomy only when it is loaded
        //    and new enough; claims must happen here, before the first server starts.
        CompanionEconomy.init();

        // 4. Quest hook listener (Core progression events only; no quest-mod dependency). Each loader feeds
        //    QuestHooks.tick from the server tick so the listener can find the online player to notify.
        QuestHooks.init();
    }
}
