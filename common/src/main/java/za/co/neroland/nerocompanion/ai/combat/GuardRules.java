package za.co.neroland.nerocompanion.ai.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * The Combat Bot's numbers and target choice, pure (no game classes) so they are unit-tested headless.
 *
 * <p>Who may be attacked is decided in two layers, both of which must allow:
 * <ol>
 *   <li>{@link #eligible}: the bot's own appetite. Hostile mobs ({@code Enemy}) inside the guard radius by default;
 *       anything else - a neutral mob ({@code NeutralMob}, even one that is also an {@code Enemy}: zombified
 *       piglins, endermen), an animal, another player's companion, a player - only when it is an
 *       <em>aggressor</em>: it is the owner's or the bot's last attacker, or a mob currently targeting either.
 *       Aggressors are taken anywhere inside the leash, not only the guard radius. A guard bot never starts a fight
 *       with a peaceful animal, a calm neutral mob or a passer-by.</li>
 *   <li>{@code OwnerAuthority.mayAttack}: the owner's authority - never the owner, the owner's team,
 *       any companion or tamed animal of anyone, villagers, golems, {@code #nerocompanion:protected_mobs}; players
 *       only with {@code pvpBots}, server PvP and the owner's own permission; never in a claim the owner could not
 *       act in. It is checked when a target is chosen and again before every hit.</li>
 * </ol>
 */
public final class GuardRules {

    /** Guard radius in blocks around the anchor (the owner in GUARD, the stay point in STAY), before range chips. */
    public static final int BASE_GUARD_RADIUS = 12;

    /** The bot gives up a chase and returns once it is farther than radius + this from the anchor. */
    public static final int LEASH_SLACK = 8;

    /** FE per melee hit ({@code 003}). */
    public static final long FE_PER_HIT = 150L;

    /** Never spend below this percent of capacity (the low-battery line, {@code 003}). */
    public static final int RESERVE_PERCENT = 10;

    /** Ticks between target scans (cached in between). */
    public static final int SCAN_INTERVAL = 10;

    /** Highest melee tier. */
    public static final int MAX_MELEE_TIER = 2;

    /** Fragility: max health of the shipped chassis ({@code combat_bot.json}); low on purpose. */
    public static final double FRAGILE_MAX_HEALTH = 12.0D;

    /**
     * Fragility: health restored per second, only while docked on a powered Charging Pad (not the Bay, not the
     * Crank). Potions, beacons and other {@code heal} sources still work: they cost the owner something, which is
     * the point of "no free regeneration".
     */
    public static final float HEAL_PER_SECOND_CHARGING = 1.0F;

    /** Fragility: health restored per second otherwise (no natural regeneration). */
    public static final float HEAL_PER_SECOND_UNCHARGED = 0.0F;

    private GuardRules() {
    }

    /** What a candidate is, from the bot's point of view. */
    public enum Kind {
        /** A hostile mob ({@code Enemy}). */
        HOSTILE,
        /** A player. */
        PLAYER,
        /** Anything else alive (animals, neutral mobs, other mods' creatures). */
        OTHER
    }

    /** Ticks a chase may go without getting closer before the target is given up as unreachable. */
    public static final int GIVE_UP_TICKS = 60;

    /** Ticks an unreachable target is skipped by later scans. */
    public static final int IGNORE_TICKS = 100;

    /** A chase counts as progress only when it gets at least this much closer (squared blocks). */
    public static final double PROGRESS_SQ = 1.0D;

    /**
     * One candidate seen in a scan. {@code inGuardRadius}: inside the guard radius (the scan itself covers the
     * leash, so aggressors a little further out are seen too).
     */
    public record Candidate<T>(T entity, Kind kind, boolean aggressor, boolean inGuardRadius, double distanceSq) {
    }

    /**
     * What an entity is for the appetite. A neutral mob is never {@link Kind#HOSTILE}, even when it is also an
     * {@code Enemy}: it is only fought as an aggressor, like vanilla wolves and golems treat it.
     */
    public static Kind kindOf(boolean enemy, boolean neutral, boolean player) {
        if (player) {
            return Kind.PLAYER;
        }
        if (neutral) {
            return Kind.OTHER;
        }
        return enemy ? Kind.HOSTILE : Kind.OTHER;
    }

    /** The bot's own appetite (layer 1): aggressors anywhere in the leash, hostiles inside the guard radius. */
    public static boolean eligible(Kind kind, boolean aggressor, boolean inGuardRadius) {
        return aggressor || (kind == Kind.HOSTILE && inGuardRadius);
    }

    /**
     * Whether the client should play the GUARD loop (010 section 4.3): while engaged (WORKING) in GUARD, and for a
     * post guard in STAY too.
     */
    public static boolean playsGuardLoop(CompanionMode syncedMode, boolean working) {
        return working && (syncedMode == CompanionMode.GUARD || syncedMode == CompanionMode.STAY);
    }

    /**
     * The target to take: among eligible candidates the owner's authority allows, aggressors first, then the
     * nearest. {@code authority} is asked only for candidates that pass {@link #eligible}, nearest first among the
     * same priority, and stops at the first allowed one.
     */
    public static <T> Optional<T> select(List<Candidate<T>> candidates, Function<T, Decision> authority) {
        Candidate<T> best = null;
        List<Candidate<T>> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> {
            if (a.aggressor() != b.aggressor()) {
                return a.aggressor() ? -1 : 1;
            }
            return Double.compare(a.distanceSq(), b.distanceSq());
        });
        for (Candidate<T> candidate : ordered) {
            if (!eligible(candidate.kind(), candidate.aggressor(), candidate.inGuardRadius())) {
                continue;
            }
            Decision decision = authority.apply(candidate.entity());
            if (decision != null && decision.allowed()) {
                best = candidate;
                break;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.entity());
    }

    /** Guard radius after range chips. */
    public static int guardRadius(int rangeBonus) {
        return BASE_GUARD_RADIUS + Math.max(0, rangeBonus);
    }

    /** Leash radius: guard radius + {@link #LEASH_SLACK}. */
    public static int leashRadius(int rangeBonus) {
        return guardRadius(rangeBonus) + LEASH_SLACK;
    }

    /** Whether a point {@code distanceSq} from the anchor is inside the leash. */
    public static boolean withinLeash(double distanceSq, int rangeBonus) {
        double leash = leashRadius(rangeBonus);
        return distanceSq <= leash * leash;
    }

    /** FE held back: {@code reservePercent} of capacity, rounded up. */
    public static long reserve(long capacity) {
        return (Math.max(0L, capacity) * RESERVE_PERCENT + 99L) / 100L;
    }

    /** Whether one hit can be paid without dipping below the reserve. */
    public static boolean canAffordHit(long stored, long capacity) {
        return stored - FE_PER_HIT >= reserve(capacity);
    }

    /**
     * The melee tier the chips give. Decision 017 defines no combat-tier chip, so this is tier I for every chassis
     * until one exists; tier II is a seam ({@link #damage}, {@link #cooldownTicks}).
     */
    public static int meleeTier() {
        return 1;
    }

    /** Damage of one hit: the chassis' {@code attack_damage} at tier I, x1.5 at tier II. */
    public static float damage(double attackDamage, int tier) {
        double base = Math.max(0.0D, attackDamage);
        return (float) (clampTier(tier) >= 2 ? base * 1.5D : base);
    }

    /** Ticks between hits: 20 at tier I, 15 at tier II. */
    public static int cooldownTicks(int tier) {
        return clampTier(tier) >= 2 ? 15 : 20;
    }

    private static int clampTier(int tier) {
        return Math.max(1, Math.min(MAX_MELEE_TIER, tier));
    }

    /** Health to restore this second: only while charging (no natural regeneration). */
    public static float healPerSecond(boolean charging) {
        return charging ? HEAL_PER_SECOND_CHARGING : HEAL_PER_SECOND_UNCHARGED;
    }
}
