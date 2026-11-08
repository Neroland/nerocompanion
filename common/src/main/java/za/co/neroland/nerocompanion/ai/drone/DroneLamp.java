package za.co.neroland.nerocompanion.ai.drone;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.job.CompanionJob;
import za.co.neroland.nerocompanion.scout.LampModel;

/**
 * The Lamp Chip on a drone (server): one {@code minecraft:light} at level {@value #LEVEL} at the block the drone
 * occupies, only into air, placed through {@link OwnerAuthority#mayPlace}, moved with hysteresis (see
 * {@link LampModel}: at most once per 10 ticks, after 10 stable ticks or a 2-block jump), placed and removed with
 * {@link #FLAGS} (client update only, no neighbour or shape updates - a light block has nothing to notify). Costs
 * {@value #DRAIN_PER_SECOND} FE/s while lit (less with an Efficiency chip), paid up front each second; a drone that
 * cannot pay stays dark until it holds a full second's cost.
 *
 * <p>Removal paths, all idempotent: the lamp goal's {@code stop()} (chip removed, goal uninstalled); the entity's
 * {@code serverTick} when the drone sleeps; {@code onLeaveWorld} (recall, parking, death, discard); the entity's
 * {@code onRemoval} on 1.21.4+ (every reason, including chunk unload) and {@code removeAfterChangingDimensions} on
 * every line. On 1.21.1 a chunk unload has no hook: the lit spot is in the saved {@link LampJob}, and the re-created
 * drone treats it as stale. A lit spot whose chunk is <b>not loaded</b> is never forgotten: it becomes a stale spot,
 * persisted in the job and removed by {@link #serverTick} (every {@value #STALE_RETRY_TICKS} ticks, any mode, never
 * loading a chunk) once its chunk is loaded.
 */
public final class DroneLamp {

    public static final int LEVEL = 12;
    public static final long DRAIN_PER_SECOND = 5L;
    /** Client update + known shape: no neighbour updates, no neighbour shape updates. */
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final int STALE_RETRY_TICKS = 200;

    private final LampModel<BlockPos> model = new LampModel<>();
    @Nullable
    private Identifier litDimension;
    private final List<LampJob.Spot> stale = new ArrayList<>();
    private boolean restored;
    private boolean unpaid;
    private long nextPayment = Long.MIN_VALUE;
    private long nextStaleCheck = Long.MIN_VALUE;
    @Nullable
    private ServerLevel worldLevel;
    @Nullable
    private CompanionEntity worldOwner;
    @Nullable
    private LampModel.World<BlockPos> world;

    /** The light state this lamp places. */
    public static BlockState lightState() {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL);
    }

    public static boolean isLampLight(BlockState state) {
        return state.is(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) == LEVEL;
    }

    /** Whether the drone has a Lamp Chip installed. */
    public static boolean installed(CompanionEntity companion) {
        return ChipEffects.has(companion.activeChips(), ChipKinds.LAMP);
    }

    public Optional<BlockPos> lit() {
        return this.model.lit();
    }

    /** Server, every lamp-goal tick: moves, places or removes the light. */
    public void tick(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return;
        }
        this.restore(companion);
        long now = level.getGameTime();
        CompanionMode mode = companion.effectiveMode();
        BlockPos want = null;
        if (installed(companion) && mode != CompanionMode.SLEEP && !companion.isSleepingCompanion()
                && !companion.energy().isEmpty()) {
            want = companion.blockPosition();
        }
        long cost = ChipEffects.drain(DRAIN_PER_SECOND, companion.activeChips());
        if (want != null && now >= this.nextPayment) {
            this.nextPayment = now + 20L;
            if (this.unpaid) {
                this.unpaid = companion.energy().stored() < cost;
            }
            if (!this.unpaid && this.model.lit().isPresent() && !companion.energy().tryPay(cost)) {
                this.unpaid = true;
            }
        }
        if (this.unpaid) {
            want = null;
        }
        if (this.model.update(want, now, this.world(level, companion))) {
            this.litDimension = this.model.lit().isPresent() ? dimensionOf(level) : null;
            this.persist(companion);
        }
    }

    /**
     * Server, every entity tick in any mode (the entity calls it): turns the light off while the drone sleeps (the
     * lamp goal is not installed then) and retries stale spots every {@value #STALE_RETRY_TICKS} ticks.
     */
    public void serverTick(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.model.lit().isPresent()
                && (companion.effectiveMode() == CompanionMode.SLEEP || companion.isSleepingCompanion())) {
            this.clear(companion);
        }
        if (level.getGameTime() >= this.nextStaleCheck) {
            this.nextStaleCheck = level.getGameTime() + STALE_RETRY_TICKS;
            this.restore(companion);
            this.cleanStale(level, companion);
        }
    }

    /** Server: removes the light (if still ours; queued as stale if its chunk is unloaded) and forgets it. */
    public void clear(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return;
        }
        this.restore(companion);
        boolean changed;
        if (this.litDimension != null && !this.litDimension.equals(dimensionOf(level))) {
            this.model.lit().ifPresent(pos -> this.addStale(new LampJob.Spot(this.litDimension, pos)));
            this.model.remember(null);
            changed = true;
        } else {
            changed = this.model.clear(this.world(level, companion));
        }
        this.litDimension = null;
        if (changed) {
            this.persist(companion);
        }
    }

    /** Reads a saved lamp position once: anything remembered from before is treated as stale and cleaned up. */
    private void restore(CompanionEntity companion) {
        if (this.restored) {
            return;
        }
        this.restored = true;
        if (companion.job() instanceof LampJob job) {
            for (LampJob.Spot spot : job.spots()) {
                this.addStale(spot);
            }
        }
    }

    private void addStale(LampJob.Spot spot) {
        if (!this.stale.contains(spot) && this.stale.size() < LampJob.MAX_SPOTS - 1) {
            this.stale.add(spot);
        }
    }

    /** Removes stale lights whose chunk is loaded; keeps the rest for a later try. Never loads a chunk. */
    private void cleanStale(ServerLevel level, CompanionEntity companion) {
        if (this.stale.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (LampJob.Spot spot : List.copyOf(this.stale)) {
            ServerLevel target = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, spot.dimension()));
            if (target == null) {
                this.stale.remove(spot);
                changed = true;
            } else if (target.isLoaded(spot.pos())) {
                if (isLampLight(target.getBlockState(spot.pos()))) {
                    target.setBlock(spot.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
                }
                this.stale.remove(spot);
                changed = true;
            }
        }
        if (changed) {
            this.persist(companion);
        }
    }

    /** Writes the lit + stale spots into the job, never replacing another package's job. */
    private void persist(CompanionEntity companion) {
        CompanionJob current = companion.job();
        if (!current.isNone() && !(current instanceof LampJob)) {
            return;
        }
        List<LampJob.Spot> spots = new ArrayList<>(this.stale);
        if (this.litDimension != null) {
            this.model.lit().ifPresent(pos -> spots.add(0, new LampJob.Spot(this.litDimension, pos)));
        }
        companion.setJob(spots.isEmpty() ? null : new LampJob(spots));
    }

    private static Identifier dimensionOf(Level level) {
        return level.dimension().identifier();
    }

    /** One world view per lamp, rebuilt only when the drone's level changes. */
    private LampModel.World<BlockPos> world(ServerLevel level, CompanionEntity companion) {
        LampModel.World<BlockPos> current = this.world;
        if (current != null && this.worldLevel == level && this.worldOwner == companion) {
            return current;
        }
        this.worldLevel = level;
        this.worldOwner = companion;
        current = new LampModel.World<>() {
            @Override
            public boolean isLoaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public boolean isAir(BlockPos pos) {
                return level.getBlockState(pos).isAir();
            }

            @Override
            public boolean isOurLight(BlockPos pos) {
                return isLampLight(level.getBlockState(pos));
            }

            @Override
            public boolean mayPlace(BlockPos pos) {
                return OwnerAuthority.mayPlace(companion, level, pos, lightState()).allowed();
            }

            @Override
            public boolean place(BlockPos pos) {
                return level.setBlock(pos, lightState(), FLAGS);
            }

            @Override
            public void remove(BlockPos pos) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }

            @Override
            public int distance(BlockPos a, BlockPos b) {
                return Math.max(Math.abs(a.getX() - b.getX()),
                        Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
            }

            @Override
            public void orphan(BlockPos pos) {
                addStale(new LampJob.Spot(dimensionOf(level), pos));
            }
        };
        this.world = current;
        return current;
    }
}
