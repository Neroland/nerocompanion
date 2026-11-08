package za.co.neroland.nerocompanion.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.authority.AuthorityRules;
import za.co.neroland.nerocompanion.scheduler.CompanionLimits;

/**
 * NeroCompanion config schema, built on Neroland Core's config framework (file
 * {@code config/nerocompanion.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroCompanionCommon#init()}, before anything else — every other
 * subsystem reads it, including telemetry's opt-out flag.
 *
 * <p>Only the foundation keys live here for now; gameplay keys land with the stages that read
 * them, so the file players see always matches what the mod actually does. Core's config has no
 * sections, so groups use dotted key prefixes:
 * <ul>
 *   <li>(no prefix) — telemetry and other client-local options.</li>
 *   <li><b>privacy</b> — {@code privacy.*}: retention, name visibility and other personal-data controls.
 *       First-class: every key here is documented in {@code PRIVACY.md} and on the wiki's Server-Admin page.</li>
 * </ul>
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

    // --- Companions: caps and throttling (server-authoritative) ----------------

    private static final ConfigValue<Integer> MAX_COMPANIONS_PER_PLAYER = SCHEMA.intRange(
            "maxCompanionsPerPlayer", 4, 1, 64, true,
            "most companions one player may have deployed at once (parked and capsuled ones do not count)");

    private static final ConfigValue<Integer> MAX_ACTIVE_BOTS_PER_REGION = SCHEMA.intRange(
            "maxActiveBotsPerChunkRegion", 16, 1, 512, true,
            "most deployed companions of all players in one 8x8-chunk region; deploying into a full region is refused");

    private static final ConfigValue<Integer> THROTTLE_DISTANCE = SCHEMA.intRange(
            "throttleDistance", 32, 8, 256, true,
            "beyond this many blocks from its owner a companion thinks on every 4th tick only");

    private static final ConfigValue<Integer> SLEEP_DISTANCE = SCHEMA.intRange(
            "sleepDistance", 64, 16, 512, true,
            "beyond this many blocks from its owner an idle companion falls asleep (no AI, no energy drain)");

    // --- Companions: energy (server-authoritative) --------------------------------

    private static final ConfigValue<Integer> LOW_BATTERY_PERCENT = SCHEMA.intRange(
            "energy.lowBatteryPercent", 10, 1, 50, true,
            "at or below this charge a companion heads home to its pad or bay, or sleeps in place if it has none");

    private static final ConfigValue<Integer> RETURN_HOME_RANGE = SCHEMA.intRange(
            "energy.returnHomeRange", 96, 8, 512, true,
            "a low-battery companion only flies or walks home when its home is within this many blocks");

    // --- Companions: authority (server-authoritative) -----------------------------

    private static final ConfigValue<Boolean> PVP_BOTS = SCHEMA.bool(
            "pvpBots", false, true,
            "let combat companions attack players (still needs server PvP and the owner's own permission)");

    private static final ConfigValue<Boolean> MINING_ENABLED = SCHEMA.bool(
            "mining.enabled", true, true, "let mining companions break blocks at all");

    private static final ConfigValue<Integer> MINING_MAX_JOB_SIZE = SCHEMA.intRange(
            "mining.maxJobSize", 9, 1, 16, true, "edge length of the largest cube one mining job may cover");

    private static final ConfigValue<Boolean> COMBAT_ENABLED = SCHEMA.bool(
            "combat.enabled", true, true, "let combat companions attack at all");

    // --- Recovery and death salvage (server-authoritative) -------------------------------------------

    private static final ConfigValue<Integer> LOST_AFTER_MINUTES = SCHEMA.intRange(
            "lostAfterMinutes", 30, 1, 10080, true,
            "a deployed companion that has not been seen for this many minutes while its owner is online (its chunk "
                    + "unloaded) counts as lost and can be reclaimed from a Bay or with /nscomp recover; killed "
                    + "companions are lost at once");

    private static final ConfigValue<Integer> LOST_AFTER_OFFLINE_DAYS = SCHEMA.intRange(
            "lostAfterOfflineDays", 30, 1, 3650, true,
            "a deployed companion that has not been seen for this many days counts as lost even if its owner never "
                    + "comes back (its last-known position is dropped); lost companions are then deleted after "
                    + "privacy.ledgerRetentionDays");

    private static final ConfigValue<Integer> RECLAIM_COST = SCHEMA.intRange(
            "reclaimCost", 0, 0, 1_000_000, true,
            "credits a reclaim costs (0 = free). Only charged when an economy mod provides Neroland Core currency; "
                    + "without one, reclaiming is always free");

    private static final ConfigValue<Boolean> SALVAGE_ENABLED = SCHEMA.bool(
            "salvage.enabled", true, true,
            "let companions gather their owner's dropped items after the owner dies (never with keepInventory)");

    private static final ConfigValue<Boolean> SALVAGE_CARRY_BOTS = SCHEMA.bool(
            "salvage.carryBotsByDefault", true, true,
            "carry bots salvage without a Salvage Chip (false = only companions with the chip)");

    private static final ConfigValue<Integer> SALVAGE_WINDOW_TICKS = SCHEMA.intRange(
            "salvageWindowTicks", 1200, 100, 12000, true,
            "how long after the owner's death (in ticks, 20 per second) companions keep gathering the dropped items");

    private static final ConfigValue<Integer> SALVAGE_HOLD_MINUTES = SCHEMA.intRange(
            "salvageHoldMinutes", 60, 1, 1440, true,
            "how long a companion holds salvaged items for its owner; after that they drop where the companion is "
                    + "(only while the owner is online - an offline owner's items wait for them)");

    // --- Privacy (server-authoritative) -------------------------------------------------------------

    private static final ConfigValue<Integer> LEDGER_RETENTION_DAYS = SCHEMA.intRange(
            "privacy.ledgerRetentionDays", 180, 1, 3650, true,
            "days of inactivity after which a player's companion data is deleted from every store (recovery ledger, "
                    + "capsule vault, salvage hold, incubators, privacy settings); the same bound applies to each "
                    + "capsuled or lost companion's ledger entry, a capsule's stored home and job data, an unclaimed "
                    + "salvage hold and an erasure marker that are not used for that long. See PRIVACY.md");

    private static final ConfigValue<Boolean> ALLOW_NAMES = SCHEMA.bool(
            "privacy.allowNames", true, true,
            "show companion names to other players as nameplates (false = names are only ever shown to their owner, "
                    + "on the HUD, screens and Remote; nameplates are hidden for everyone)");

    private static final ConfigValue<Integer> EXPORT_COOLDOWN_SECONDS = SCHEMA.intRange(
            "privacy.exportCooldownSeconds", 300, 10, 86400, true,
            "seconds a player must wait between two /nscomp export requests");

    // --- HUD companion strip (client-local) -------------------------------------------------------

    private static final ConfigValue<Boolean> HUD_ENABLED = SCHEMA.bool(
            "hud.enabled", true, false, "draw the companion strip (your companions' type, charge, job and name)");

    private static final ConfigValue<String> HUD_ANCHOR = SCHEMA.string(
            "hud.anchor", "TOP_LEFT", false, "where the strip sits: TOP_LEFT, TOP_CENTRE, TOP_RIGHT, MIDDLE_LEFT, "
                    + "CENTRE, MIDDLE_RIGHT, BOTTOM_LEFT, BOTTOM_CENTRE or BOTTOM_RIGHT");

    private static final ConfigValue<Integer> HUD_OFFSET_X = SCHEMA.intRange(
            "hud.offsetX", 0, -2000, 2000, false, "extra horizontal offset of the strip in GUI pixels");

    private static final ConfigValue<Integer> HUD_OFFSET_Y = SCHEMA.intRange(
            "hud.offsetY", 0, -2000, 2000, false, "extra vertical offset of the strip in GUI pixels");

    private static final ConfigValue<Boolean> HUD_SHOW_NAMES = SCHEMA.bool(
            "hud.showNames", true, false, "show names on the strip (false = always the compact form)");

    private static final ConfigValue<Boolean> HUD_COMPACT = SCHEMA.bool(
            "hud.compact", false, false, "force the compact strip (no names)");

    // --- Looks (server-authoritative) ---------------------------------------------------------------

    private static final ConfigValue<String> SEASON = SCHEMA.string(
            "season", "none", true,
            "the server's season for seasonal skins and hats: none, spring, summer, autumn, winter or festive. Never "
                    + "read from the real clock - an admin sets it (e.g. festive unlocks the Party Cone)");

    // --- Debug -----------------------------------------------------------------------------------

    private static final ConfigValue<Integer> DEBUG_MAX_SPAWN = SCHEMA.intRange(
            "debug.maxSpawn", 64, 1, 256, true,
            "most test companions one /nscomp debug spawn may create (operators in creative only)");

    private NeroCompanionConfig() {
    }

    /** Caps, throttle distances and the low-battery rule, read live (hot-reloadable). */
    public static CompanionLimits limits() {
        int throttle = THROTTLE_DISTANCE.get();
        int sleep = Math.max(throttle, SLEEP_DISTANCE.get());
        return new CompanionLimits(MAX_COMPANIONS_PER_PLAYER.get(), MAX_ACTIVE_BOTS_PER_REGION.get(), throttle,
                sleep, LOW_BATTERY_PERCENT.get(), RETURN_HOME_RANGE.get());
    }

    /** The configurable authority switches. */
    public static AuthorityRules authorityRules() {
        return new AuthorityRules(MINING_ENABLED.get(), COMBAT_ENABLED.get(), PVP_BOTS.get());
    }

    /** Edge of the largest mining job cube (Stage 3 mining bot). */
    public static int miningMaxJobSize() {
        return MINING_MAX_JOB_SIZE.get();
    }

    /** Minutes unseen (owner online) before a companion is lost. */
    public static int lostAfterMinutes() {
        return LOST_AFTER_MINUTES.get();
    }

    /** {@code lostAfterOfflineDays} in milliseconds. */
    public static long lostAfterOfflineMillis() {
        return LOST_AFTER_OFFLINE_DAYS.get() * 24L * 60L * 60L * 1000L;
    }

    /** Reclaim cost in credits (0 = free; only charged with a real currency provider). */
    public static int reclaimCost() {
        return RECLAIM_COST.get();
    }

    /** The death-salvage switches and windows. */
    public record Salvage(boolean enabled, boolean carryBotsByDefault, int windowTicks, int holdMinutes) {
    }

    public static Salvage salvage() {
        return new Salvage(SALVAGE_ENABLED.get(), SALVAGE_CARRY_BOTS.get(), SALVAGE_WINDOW_TICKS.get(),
                SALVAGE_HOLD_MINUTES.get());
    }

    /** {@code privacy.ledgerRetentionDays}. */
    public static int ledgerRetentionDays() {
        return LEDGER_RETENTION_DAYS.get();
    }

    /** {@code lostAfterOfflineDays}. */
    public static int lostAfterOfflineDays() {
        return LOST_AFTER_OFFLINE_DAYS.get();
    }

    /** {@code privacy.allowNames}: whether names may be shown to other players at all. */
    public static boolean allowNames() {
        return ALLOW_NAMES.get();
    }

    /** {@code privacy.exportCooldownSeconds} in milliseconds. */
    public static long exportCooldownMillis() {
        return EXPORT_COOLDOWN_SECONDS.get() * 1000L;
    }

    /** {@code privacy.ledgerRetentionDays} in milliseconds. */
    public static long ledgerRetentionMillis() {
        return LEDGER_RETENTION_DAYS.get() * 24L * 60L * 60L * 1000L;
    }

    /** The HUD strip's client options. */
    public record Hud(boolean enabled, String anchor, int offsetX, int offsetY, boolean showNames, boolean compact) {
    }

    public static Hud hud() {
        return new Hud(HUD_ENABLED.get(), HUD_ANCHOR.get(), HUD_OFFSET_X.get(), HUD_OFFSET_Y.get(),
                HUD_SHOW_NAMES.get(), HUD_COMPACT.get());
    }

    /** The {@code season} key, lower-cased and trimmed ({@code none} when blank). */
    public static String season() {
        String value = SEASON.get();
        value = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
        return value.isEmpty() ? "none" : value;
    }

    public static int debugMaxSpawn() {
        return DEBUG_MAX_SPAWN.get();
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
