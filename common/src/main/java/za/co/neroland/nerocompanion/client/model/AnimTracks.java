package za.co.neroland.nerocompanion.client.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocompanion.client.CompanionRenderInfo;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.personality.Trait;

/**
 * Client-side animation memory per companion ({@code 019} section 4): the eased pose blends (0 to 1 over 6 ticks,
 * {@code easeOutQuad}, into SLEEP / PERCH / CHARGE and back), the running one-shot impulse, the nap clock and the
 * charge-complete eye flash. Keyed by entity network id; entries nobody drew for 10 s are dropped. Holds no player
 * data. Client only.
 */
public final class AnimTracks {

    /** Ticks a pose blend takes. */
    public static final float BLEND_TICKS = 6.0F;
    /** Ticks the charge-complete flash lasts. */
    public static final float FLASH_TICKS = 10.0F;
    private static final int FORGET_TICKS = 200;

    private static final class Track {
        float lastAge = Float.NaN;
        float sleep;
        float perch;
        float charge;
        int impulse = -1;
        float impulseStart;
        float napStart = Float.NaN;
        float flashUntil = Float.NEGATIVE_INFINITY;
        int lastEnergy = -1;
        boolean wasCharging;
        long seen;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static long clock;

    private AnimTracks() {
    }

    /** One-shot impulse {@code impulse} on {@code entityId}, starting now. */
    public static void impulse(int entityId, int impulse) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (!(entity instanceof CompanionEntity)) {
            return;
        }
        Track track = TRACKS.computeIfAbsent(entityId, id -> new Track());
        track.impulse = impulse;
        track.impulseStart = entity.tickCount;
        track.seen = clock;
    }

    /** Length of an impulse in ticks ({@code 019} 4.4: tricks run 12-40 ticks by trait). */
    public static float duration(int impulse, int trait) {
        if (impulse == CompanionEntity.IMPULSE_TRICK) {
            return switch (Trait.byIndex(trait)) {
                case CURIOUS, LOYAL -> 20.0F;
                case GRUMPY -> 18.0F;
                case SLEEPY -> 40.0F;
                case SHOWOFF -> 32.0F;
                case SHY -> 12.0F;
            };
        }
        return impulse == CompanionEntity.IMPULSE_STOMP ? 6.0F : 10.0F;
    }

    /** {@code easeOutQuad}. */
    public static float ease(float t) {
        float c = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - c) * (1.0F - c);
    }

    /** Fills the blends, impulse, nap and flash fields of {@code info} and advances the track. */
    public static void update(CompanionRenderInfo info) {
        if (!info.companion || info.entityId < 0) {
            info.sleepBlend = 0.0F;
            info.perchBlend = 0.0F;
            info.chargeBlend = 0.0F;
            info.impulse = -1;
            return;
        }
        if (info.preview) {
            info.sleepBlend = 0.0F;
            info.perchBlend = 0.0F;
            info.chargeBlend = 0.0F;
            info.impulse = -1;
            info.napTicks = 0.0F;
            info.chargeFlash = 0.0F;
            info.dangerFlash = false;
            return;
        }
        Track track = TRACKS.computeIfAbsent(info.entityId, id -> new Track());
        track.seen = clock;
        float age = info.ageInTicks;
        boolean sleepTarget = info.sleeping() || info.napping();
        boolean perchTarget = info.perched();
        boolean chargeTarget = info.charging();
        if (Float.isNaN(track.lastAge)) {
            track.sleep = sleepTarget ? 1.0F : 0.0F;
            track.perch = perchTarget ? 1.0F : 0.0F;
            track.charge = chargeTarget ? 1.0F : 0.0F;
        } else {
            float step = Math.max(0.0F, Math.min(3.0F, age - track.lastAge)) / BLEND_TICKS;
            track.sleep = toward(track.sleep, sleepTarget, step);
            track.perch = toward(track.perch, perchTarget, step);
            track.charge = toward(track.charge, chargeTarget, step);
        }
        track.lastAge = age;
        info.sleepBlend = ease(track.sleep);
        info.perchBlend = ease(track.perch);
        info.chargeBlend = ease(track.charge);
        if (track.impulse >= 0) {
            float t = age - track.impulseStart;
            if (t < 0.0F || t > duration(track.impulse, info.trait)) {
                track.impulse = -1;
            }
        }
        info.impulse = track.impulse;
        info.impulseT = track.impulse >= 0 ? age - track.impulseStart : 0.0F;
        if (info.napping()) {
            if (Float.isNaN(track.napStart)) {
                track.napStart = age;
            }
            info.napTicks = age - track.napStart;
        } else {
            track.napStart = Float.NaN;
            info.napTicks = 0.0F;
        }
        if (chargeTarget && track.wasCharging && track.lastEnergy >= 0 && track.lastEnergy < 100
                && info.energyPct >= 100) {
            track.flashUntil = age + FLASH_TICKS;
        }
        track.wasCharging = chargeTarget;
        track.lastEnergy = info.energyPct;
        info.chargeFlash = Math.max(0.0F, track.flashUntil - age);
        info.dangerFlash = za.co.neroland.nerocompanion.scout.client.ScoutClient.flashing(info.entityId);
    }

    private static float toward(float value, boolean target, float step) {
        return target ? Math.min(1.0F, value + step) : Math.max(0.0F, value - step);
    }

    /** Client tick: forget companions nobody has drawn for a while. */
    public static void tick() {
        clock++;
        if (clock % 40 != 0) {
            return;
        }
        for (Iterator<Map.Entry<Integer, Track>> it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
            if (clock - it.next().getValue().seen > FORGET_TICKS) {
                it.remove();
            }
        }
    }

    /** Disconnect. */
    public static void clear() {
        TRACKS.clear();
    }
}
