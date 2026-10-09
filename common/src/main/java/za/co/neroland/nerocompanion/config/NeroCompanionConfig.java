package za.co.neroland.nerocompanion.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * NeroCompanion config schema, built on Neroland Core's config framework (file
 * {@code config/nerocompanion.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroCompanionCommon#init()}, before anything else — every other
 * subsystem reads it, including telemetry's opt-out flag.
 *
 * <p>Only the foundation keys live here for now; gameplay keys land with the stages that read
 * them, so the file players see always matches what the mod actually does.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative —
 * anonymous crash reporting is a per-client opt-out that a server must never force on or off.
 */
public final class NeroCompanionConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroCompanionCommon.MOD_ID, "NeroCompanion configuration.");

    // --- Crash telemetry (client-local opt-out) -----------------------------

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroCompanion-only crash reports (Sentry, EU servers) - ON by default. Sends the "
                    + "stack trace, mod/MC/loader/OS/Java versions and your other installed mods' ids "
                    + "and versions; never your IP, username, UUID, world data or chat; file paths are "
                    + "scrubbed of your account name. Set false to opt out of all of it (takes effect "
                    + "on restart). See PRIVACY.md");

    private NeroCompanionConfig() {
    }

    /**
     * Whether anonymous NeroCompanion-only crash reporting is on (default true, opt-out). Read once at
     * bootstrap by {@code NeroCompanionTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
