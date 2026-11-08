package za.co.neroland.nerocompanion.scout.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocompanion.ai.drone.DroneWork;
import za.co.neroland.nerocompanion.client.ClientHooks;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.scout.ScoutMarkStore;

/**
 * The drones' client side (WP-A). Client only.
 *
 * <ul>
 *   <li><b>Scout marks</b>: the owner's {@code ScoutMarks} batches go into a {@link ScoutMarkStore} (TTL, cap); every
 *       {@value #MARK_PARTICLE_TICKS} ticks each live mark is outlined with a few particles on its block edges - glow
 *       for ore, crimson for hostiles. Particles are depth-tested, so a buried ore shows only where its block is
 *       visible; a through-wall outline needs a level-render hook (requested from integration).</li>
 *   <li><b>Danger flash</b>: a {@code Chatter} with the danger key flashes the drone for 20 ticks (spark burst at 4 Hz)
 *       and chirps once.</li>
 *   <li>The hover bob moved into {@code client.model.CompanionModel} with the real models; {@link #flashing}
 *       also turns the lens red there.</li>
 * </ul>
 */
public final class ScoutClient {

    private static final int MARK_PARTICLE_TICKS = 5;
    private static final int FLASH_TICKS = 20;

    private static final ScoutMarkStore STORE = new ScoutMarkStore();
    private static final Map<Integer, Long> FLASHES = new HashMap<>();
    private static long clientTicks;
    private static ClientLevel lastLevel;

    private ScoutClient() {
    }

    public static void register() {
        ClientHooks.onScoutMarks(payload -> STORE.receive(payload.marks(), payload.ttlTicks(), clientTicks));
        ClientHooks.onChatter(ScoutClient::onChatter);
        ClientHooks.onTick(ScoutClient::tick);
    }

    private static void onChatter(Payloads.Chatter chatter) {
        if (!DroneWork.CHATTER_DANGER.equals(chatter.key())) {
            return;
        }
        FLASHES.put(chatter.entityId(), clientTicks + FLASH_TICKS);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity drone = level.getEntity(chatter.entityId());
        if (drone != null) {
            level.playLocalSound(drone.getX(), drone.getY(), drone.getZ(), za.co.neroland.nerocompanion.registry.ModSounds.DANGER_ALERT.get(),
                    SoundSource.NEUTRAL, 0.9F, 1.0F, false);
        }
    }

    private static void tick() {
        clientTicks++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            STORE.clear();
            FLASHES.clear();
        }
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        if (clientTicks % MARK_PARTICLE_TICKS == 0) {
            for (Payloads.Mark mark : STORE.active(clientTicks)) {
                outline(level, random, mark.pos(),
                        mark.kind() == Payloads.Mark.ORE ? ParticleTypes.GLOW : ParticleTypes.CRIMSON_SPORE);
            }
        }
        for (Iterator<Map.Entry<Integer, Long>> it = FLASHES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> flash = it.next();
            if (clientTicks >= flash.getValue()) {
                it.remove();
                continue;
            }
            Entity drone = level.getEntity(flash.getKey());
            if (drone != null && (flash.getValue() - clientTicks) % 5 == 0) {
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, drone.getX() + random.nextGaussian() * 0.2D,
                            drone.getY() + drone.getBbHeight() * 0.5D + random.nextGaussian() * 0.2D,
                            drone.getZ() + random.nextGaussian() * 0.2D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    /** Three particles on random points of the block's twelve edges. */
    private static void outline(ClientLevel level, RandomSource random, BlockPos pos, ParticleOptions particle) {
        for (int i = 0; i < 3; i++) {
            double[] p = {random.nextDouble(), random.nextDouble(), random.nextDouble()};
            int axis = random.nextInt(3);
            for (int a = 0; a < 3; a++) {
                if (a != axis) {
                    p[a] = random.nextBoolean() ? -0.02D : 1.02D;
                }
            }
            level.addParticle(particle, pos.getX() + p[0], pos.getY() + p[1], pos.getZ() + p[2], 0.0D, 0.0D, 0.0D);
        }
    }

    /** Whether the Scout's danger alert is flashing on {@code entityId} (the lens turns red, 019 section 4.3). */
    public static boolean flashing(int entityId) {
        return FLASHES.containsKey(entityId);
    }

}
