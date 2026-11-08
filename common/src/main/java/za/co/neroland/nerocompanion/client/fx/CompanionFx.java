package za.co.neroland.nerocompanion.client.fx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.client.ClientHooks;
import za.co.neroland.nerocompanion.client.model.AnimTracks;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.FlyingCompanion;
import za.co.neroland.nerocompanion.entity.types.MiningBotEntity;
import za.co.neroland.nerocompanion.entity.types.RepairBotEntity;
import za.co.neroland.nerocompanion.entity.types.ScoutDroneEntity;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.personality.Trait;
import za.co.neroland.nerocompanion.registry.ModParticles;
import za.co.neroland.nerocompanion.registry.ModSounds;

/**
 * The client effects pump ({@code 010} sections 5.3 and 7, {@code 019} section 4): per nearby companion, the
 * particles of its pose (charge sparks every 4 ticks, sleep z's every 50, a scout ping every 40, welder sparks every
 * 10), the one-shot sounds of a change it can see (charge start crackle, charge complete, low battery, a bot's
 * servo on starting work) and the three loops (drone hover, charging hum, drill). Chatter lines chirp in the trait's
 * voice and bob the companion ({@code EMOTE} impulse). Everything here is local to this client, derived from synched
 * entity data only; no state survives a disconnect. Client only.
 */
public final class CompanionFx {

    /** Companions further than this from the camera get no effects. */
    private static final double RANGE = 32.0D;
    /** Loops only within this distance, and only this many of each kind at once. */
    private static final double LOOP_RANGE = 12.0D;
    private static final int MAX_LOOPS = 3;

    private static final class Seen {
        byte flags;
        int energy = -1;
        LoopSound hover;
        LoopSound hum;
        LoopSound drill;
        long stamp;
    }

    private static final Map<Integer, Seen> SEEN = new HashMap<>();
    private static long ticks;

    private CompanionFx() {
    }

    public static void register() {
        ClientHooks.onTick(CompanionFx::tick);
        ClientHooks.onChatter(CompanionFx::onChatter);
    }

    private static void onChatter(Payloads.Chatter chatter) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(chatter.entityId());
        if (!(entity instanceof CompanionEntity)) {
            return;
        }
        AnimTracks.impulse(chatter.entityId(), CompanionEntity.IMPULSE_EMOTE);
        if (za.co.neroland.nerocompanion.ai.drone.DroneWork.CHATTER_DANGER.equals(chatter.key())) {
            return; // the Scout's alarm has its own sound (ScoutClient)
        }
        level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), ModSounds.chirp(Trait.byIndex(chatter.trait())),
                SoundSource.NEUTRAL, 0.6F, ModSounds.pitch(level.getRandom()), false);
    }

    private static void tick() {
        ticks++;
        AnimTracks.tick();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            SEEN.clear();
            return;
        }
        Vec3 eye = player.getEyePosition();
        int[] loops = new int[3];
        for (Seen seen : SEEN.values()) {
            loops[0] += live(seen.hover) ? 1 : 0;
            loops[1] += live(seen.hum) ? 1 : 0;
            loops[2] += live(seen.drill) ? 1 : 0;
        }
        RandomSource random = level.getRandom();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof CompanionEntity companion) || entity.isInvisible()) {
                continue;
            }
            double distSq = entity.distanceToSqr(eye);
            if (distSq > RANGE * RANGE) {
                continue;
            }
            Seen seen = SEEN.computeIfAbsent(entity.getId(), id -> new Seen());
            seen.stamp = ticks;
            step(level, companion, seen, random, distSq <= LOOP_RANGE * LOOP_RANGE, loops);
        }
        if (ticks % 40 == 0) {
            for (Iterator<Map.Entry<Integer, Seen>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
                Seen seen = it.next().getValue();
                if (ticks - seen.stamp > 40) {
                    it.remove();
                }
            }
        }
    }

    private static boolean live(LoopSound sound) {
        return sound != null && !sound.done();
    }

    private static void step(ClientLevel level, CompanionEntity companion, Seen seen, RandomSource random,
            boolean loopRange, int[] loops) {
        byte flags = companion.syncedFlags();
        boolean working = (flags & CompanionEntity.FLAG_WORKING) != 0;
        boolean sleeping = (flags & CompanionEntity.FLAG_SLEEPING) != 0;
        boolean perched = (flags & CompanionEntity.FLAG_PERCHED) != 0;
        CompanionMode mode = companion.syncedMode();
        boolean charging = working && mode == CompanionMode.SLEEP;
        boolean wasCharging = (seen.flags & CompanionEntity.FLAG_WORKING) != 0
                && (seen.flags & CompanionEntity.FLAG_SLEEPING) != 0;
        boolean wasWorking = (seen.flags & CompanionEntity.FLAG_WORKING) != 0;
        int energy = companion.energyPercentSynced();
        double x = companion.getX();
        double y = companion.getY();
        double z = companion.getZ();
        float h = companion.getBbHeight();
        float w = companion.getBbWidth();
        long age = companion.tickCount;

        if (seen.energy >= 0) {
            if (charging && wasCharging && seen.energy < 100 && energy >= 100) {
                play(level, companion, ModSounds.CHARGE_FULL.get(), 0.7F);
            }
            if (seen.energy > 10 && energy <= 10 && energy > 0) {
                play(level, companion, ModSounds.LOW_BATTERY.get(), 0.8F);
            }
            if (charging && !wasCharging) {
                play(level, companion, ModSounds.CHARGE_SPARK.get(), 0.5F);
            }
            if (working && !wasWorking && !charging && !(companion instanceof FlyingCompanion)) {
                play(level, companion, ModSounds.BOT_SERVO.get(), 0.4F);
            }
        }
        seen.flags = flags;
        seen.energy = energy;

        if (charging && age % 4 == 0) {
            level.addParticle(ModParticles.CHARGE_SPARK.get(), x + (random.nextDouble() - 0.5D) * w,
                    y + random.nextDouble() * 0.2D, z + (random.nextDouble() - 0.5D) * w, 0.0D, 0.05D, 0.0D);
        } else if (sleeping && !charging && age % 50 == 0) {
            level.addParticle(ModParticles.SLEEP_Z.get(), x, y + h + 0.1D, z, 0.0D, 0.0D, 0.0D);
        }
        if (working && mode == CompanionMode.SCOUT && companion instanceof ScoutDroneEntity && age % 40 == 0) {
            Vec3 lens = front(companion, 0.4D);
            level.addParticle(ModParticles.SCOUT_PING.get(), lens.x, y + h * 0.5D, lens.z, 0.0D, 0.0D, 0.0D);
            play(level, companion, ModSounds.SCOUT_PING.get(), 0.3F);
        }
        if (working && mode == CompanionMode.REPAIR && companion instanceof RepairBotEntity && age % 10 == 0) {
            Vec3 welder = front(companion, 0.35D);
            level.addParticle(ModParticles.CHARGE_SPARK.get(), welder.x, y + h * 0.75D, welder.z,
                    (random.nextDouble() - 0.5D) * 0.05D, 0.04D, (random.nextDouble() - 0.5D) * 0.05D);
            if (age % 20 == 0) {
                play(level, companion, ModSounds.REPAIR_WELD.get(), 0.35F);
            }
        }

        if (!loopRange) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (companion instanceof FlyingCompanion && !sleeping && !perched && !live(seen.hover) && loops[0] < MAX_LOOPS) {
            seen.hover = new LoopSound(ModSounds.DRONE_HOVER.get(), companion, 0.25F, e -> hovering(e));
            mc.getSoundManager().play(seen.hover);
            loops[0]++;
        }
        if (charging && !live(seen.hum) && loops[1] < MAX_LOOPS) {
            seen.hum = new LoopSound(ModSounds.CHARGE_HUM.get(), companion, 0.3F, e -> chargingNow(e));
            mc.getSoundManager().play(seen.hum);
            loops[1]++;
        }
        if (working && mode == CompanionMode.MINE && companion instanceof MiningBotEntity && !live(seen.drill)
                && loops[2] < MAX_LOOPS) {
            seen.drill = new LoopSound(ModSounds.MINE_DRILL.get(), companion, 0.35F, e -> drilling(e));
            mc.getSoundManager().play(seen.drill);
            loops[2]++;
        }
    }

    private static boolean hovering(Entity e) {
        return e instanceof CompanionEntity c
                && (c.syncedFlags() & (CompanionEntity.FLAG_SLEEPING | CompanionEntity.FLAG_PERCHED)) == 0;
    }

    private static boolean chargingNow(Entity e) {
        return e instanceof CompanionEntity c && (c.syncedFlags() & CompanionEntity.FLAG_WORKING) != 0
                && c.syncedMode() == CompanionMode.SLEEP;
    }

    private static boolean drilling(Entity e) {
        return e instanceof CompanionEntity c && (c.syncedFlags() & CompanionEntity.FLAG_WORKING) != 0
                && c.syncedMode() == CompanionMode.MINE;
    }

    private static Vec3 front(Entity entity, double distance) {
        float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(entity.getX() - Mth.sin(yaw) * distance, entity.getY(), entity.getZ() + Mth.cos(yaw) * distance);
    }

    private static void play(ClientLevel level, Entity entity, SoundEvent sound, float volume) {
        if (entity.isSilent()) {
            return;
        }
        level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.NEUTRAL, volume,
                ModSounds.pitch(level.getRandom()), false);
    }

    /** Disconnect. */
    public static void clear() {
        SEEN.clear();
        AnimTracks.clear();
    }
}
