package za.co.neroland.nerocompanion.ai.mining;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.authority.Decision;
import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.carry.CargoOps;
import za.co.neroland.nerocompanion.carry.CarryRules;
import za.co.neroland.nerocompanion.carry.DepositRules;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.HomePos;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.types.CarryBotEntity;
import za.co.neroland.nerocompanion.entity.types.MiningBotEntity;
import za.co.neroland.nerocompanion.mining.Backoff;
import za.co.neroland.nerocompanion.mining.DropRouter;
import za.co.neroland.nerocompanion.mining.Drill;
import za.co.neroland.nerocompanion.mining.MineArea;
import za.co.neroland.nerocompanion.mining.MineJob;
import za.co.neroland.nerocompanion.mining.MineRules;
import za.co.neroland.nerocompanion.mining.StuckTracker;

/**
 * Works a {@link MineJob}: scan (10-tick cadence, bounded budget) for the next block, walk within reach, crack it
 * for as long as an iron drill would take, re-ask {@code OwnerAuthority.mayBreak} right before it breaks, pay
 * {@link MineRules#FE_PER_BLOCK}, and route the drops through {@link DropRouter}. One block in progress at a time.
 *
 * <ul>
 *   <li><b>Owner range</b>: work needs the owner in this level within {@code sleepDistance}; beyond it the job pauses
 *       (one message) and resumes when they return.</li>
 *   <li><b>Stuck</b>: progress-based ({@link StuckTracker}); an unreachable block is skipped within ~20-60 ticks,
 *       and after {@link StuckTracker#GIVE_UP_AFTER} in a row the bot tells its owner and goes back to FOLLOW with
 *       the job kept.</li>
 *   <li><b>Full cargo</b>: unloads to a carry bot, the bound container (only if usable and with room), or the
 *       owner; a stage that refused, was full or unreachable is backed off for {@link Backoff#TICKS}. Nowhere to
 *       unload: one message, back to FOLLOW, job kept.</li>
 *   <li><b>Done / stopped</b>: back to FOLLOW, out of the pit (teleport beside the owner when there is no path up).</li>
 * </ul>
 */
public class MineJobGoal extends Goal {

    private static final int SCAN_INTERVAL = 10;
    private static final int REPATH_INTERVAL = 10;
    private static final double BREAK_REACH = 3.5D;
    private static final double HANDOFF_RANGE = 4.0D;
    private static final double UNLOAD_CARRY_RANGE = 24.0D;
    private static final double UNLOAD_RANGE = 48.0D;
    private static final int BATCH = 16;

    private final CompanionEntity companion;
    private final Backoff backoff = new Backoff();
    private final StuckTracker stuck = new StuckTracker();
    private BlockPos target;
    private BlockState targetState;
    private MineArea targetArea;
    private int breakTotal;
    private int breakElapsed;
    private int repathIn;
    private int nextScanTick;
    private boolean idle;
    private boolean farWarned;
    private Vec3 lastPos = Vec3.ZERO;
    private Optional<DropRouter.Stage> unloading = Optional.empty();

    public MineJobGoal(CompanionEntity companion, CompanionType type) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private Optional<MineJob> job() {
        return this.companion.job() instanceof MineJob job ? Optional.of(job) : Optional.empty();
    }

    private boolean ownerInRange() {
        ServerPlayer owner = this.companion.owner();
        boolean here = owner != null && owner.level() == this.companion.level();
        return MineRules.ownerInRange(here, here ? this.companion.distanceToSqr(owner) : Double.MAX_VALUE,
                NeroCompanionConfig.limits().sleepDistance());
    }

    @Override
    public boolean canUse() {
        if (!(this.companion instanceof MiningBotEntity) || !(this.companion.level() instanceof ServerLevel level)
                || this.companion.isPerched() || this.companion.tickCount < this.nextScanTick) {
            return false;
        }
        this.nextScanTick = this.companion.tickCount + SCAN_INTERVAL;
        Optional<MineJob> job = this.job();
        if (job.isEmpty() || !job.get().dimension().equals(level.dimension().identifier())
                || this.companion.energy().stored() < MineRules.FE_PER_BLOCK) {
            return false;
        }
        if (!this.ownerInRange()) {
            if (!this.farWarned && this.companion.owner() != null) {
                this.farWarned = true;
                CargoOps.tell(this.companion, "message.nerocompanion.mine.paused",
                        this.companion.getDisplayName(), Component.translatable("authority.nerocompanion.owner_far"));
            }
            return false;
        }
        this.farWarned = false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.idle && this.job().isPresent() && !this.companion.isPerched() && this.ownerInRange();
    }

    @Override
    public void start() {
        this.idle = false;
        this.dropTarget();
        this.unloading = Optional.empty();
        this.companion.setWorking(true);
    }

    @Override
    public void stop() {
        this.dropTarget();
        this.unloading = Optional.empty();
        this.companion.getNavigation().stop();
        this.companion.setWorking(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(this.companion.level() instanceof ServerLevel level) || this.job().isEmpty()) {
            this.idle = true;
            return;
        }
        MineJob job = this.job().get();
        if (this.target != null && !job.area().equals(this.targetArea)) {
            this.dropTarget(); // re-marked mid-crack: the old block is not part of the new job
        }
        SimpleContainer cargo = this.companion.cargo();
        if (this.unloading.isPresent() || !CargoOps.hasFreeSlot(cargo)) {
            this.unload(level, cargo);
            return;
        }
        if (this.target == null) {
            if (this.companion.tickCount < this.nextScanTick) {
                return;
            }
            this.nextScanTick = this.companion.tickCount + SCAN_INTERVAL;
            this.scan(level);
            return;
        }
        this.work(level);
    }

    private void dropTarget() {
        this.target = null;
        this.targetState = null;
        this.targetArea = null;
        if (this.companion instanceof MiningBotEntity miner) {
            miner.clearCrack();
        }
    }

    // --- scanning -------------------------------------------------------------------------------------

    private void scan(ServerLevel level) {
        MineJob job = this.job().orElseThrow();
        boolean filter = Drill.oreFilter(this.companion.chips());
        MineRules.Step step = MineRules.scan(job, pos -> Drill.survey(level, pos, filter),
                pos -> OwnerAuthority.mayBreak(this.companion, level, pos), MineRules.SCAN_BUDGET);
        switch (step.kind()) {
            case TARGET -> {
                this.companion.setJob(step.job());
                this.beginBlock(level, step.job());
            }
            case CONTINUE -> this.companion.setJob(step.job());
            case DONE -> this.finish(step.job());
            case PAUSE -> {
                this.companion.setJob(step.job());
                this.idle = true;
            }
            case STOP -> this.abort(step.reasonKey());
        }
    }

    private void beginBlock(ServerLevel level, MineJob job) {
        BlockPos pos = job.current();
        this.target = pos.immutable();
        this.targetState = level.getBlockState(pos);
        this.targetArea = job.area();
        this.breakTotal = Drill.breakTicks(level, pos, this.targetState, this.companion.chips());
        this.breakElapsed = 0;
        this.repathIn = 0;
        this.stuck.reset();
        this.lastPos = this.companion.position();
    }

    // --- breaking -------------------------------------------------------------------------------------

    private void work(ServerLevel level) {
        BlockPos pos = this.target;
        Vec3 centre = Vec3.atCenterOf(pos);
        this.companion.getLookControl().setLookAt(centre.x, centre.y, centre.z);
        double distance = this.companion.getEyePosition().distanceTo(centre);
        if (distance > BREAK_REACH) {
            if (--this.repathIn <= 0) {
                this.repathIn = this.adjustedTickDelay(REPATH_INTERVAL);
                boolean path = this.companion.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(),
                        pos.getZ() + 0.5D, 1.0D);
                Vec3 now = this.companion.position();
                boolean unreachable = this.stuck.check(path, distance, now.distanceTo(this.lastPos));
                this.lastPos = now;
                if (unreachable) {
                    this.unreachableTarget();
                }
            }
            return;
        }
        this.companion.getNavigation().stop();
        BlockState state = level.getBlockState(pos);
        if (!state.equals(this.targetState)) {
            this.passTarget(); // gone or swapped (piston, player): look again next scan
            return;
        }
        this.breakElapsed++;
        MiningBotEntity miner = (MiningBotEntity) this.companion;
        if (this.breakElapsed < this.breakTotal) {
            miner.showCrack(pos, MineRules.crackStage(this.breakElapsed, this.breakTotal));
            return;
        }
        miner.clearCrack();
        // Per block, right before breaking: the owner may have left, lost build rights, or a claim appeared.
        Decision decision = OwnerAuthority.mayBreak(this.companion, level, pos);
        switch (MineRules.react(decision)) {
            case SKIP -> this.skipTarget();
            case PAUSE -> {
                this.dropTarget();
                this.idle = true;
            }
            case STOP -> this.abort(decision.reasonKey());
            case BREAK -> this.breakBlock(level, pos, state);
        }
    }

    private void unreachableTarget() {
        if (this.stuck.unreachable()) {
            // Many in a row: the area is out of reach from here. Keep the cursor, tell the owner, stop burning FE.
            this.dropTarget();
            CargoOps.tell(this.companion, "message.nerocompanion.mine.cant_reach", this.companion.getDisplayName());
            this.leaveMine(false);
            return;
        }
        this.skipTarget();
    }

    private void breakBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!this.companion.energy().tryPay(MineRules.FE_PER_BLOCK)) {
            this.dropTarget();
            this.idle = true;
            return;
        }
        ItemStack tool = Drill.toolFor(state);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, this.companion, tool);
        level.destroyBlock(pos, false, this.companion);
        state.spawnAfterBreak(level, pos, tool, true);
        Map<DropRouter.Stage, DropRouter.Sink<ItemStack>> sinks = this.sinks(level);
        for (ItemStack drop : drops) {
            ItemStack left = DropRouter.route(drop.copy(), sinks, ItemStack::isEmpty);
            if (!left.isEmpty()) {
                Block.popResource(level, this.companion.blockPosition(), left);
            }
        }
        this.stuck.reached();
        MineJob next = this.job().orElseThrow().broke();
        this.companion.setJob(next);
        this.dropTarget();
        if (next.mined() % BATCH == 0) {
            this.companion.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.MINE);
        }
    }

    private void skipTarget() {
        this.job().ifPresent(job -> this.companion.setJob(job.skip()));
        this.dropTarget();
    }

    private void passTarget() {
        this.job().ifPresent(job -> this.companion.setJob(job.pass()));
        this.dropTarget();
    }

    private void finish(MineJob job) {
        this.companion.setJob(null);
        this.companion.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.MINE);
        CargoOps.tell(this.companion, "message.nerocompanion.mine.done", this.companion.getDisplayName(),
                job.mined(), job.skipped());
        this.leaveMine(true);
    }

    private void abort(String reasonKey) {
        this.companion.setJob(null);
        CargoOps.tell(this.companion, "message.nerocompanion.mine.stopped", this.companion.getDisplayName(),
                Component.translatable(reasonKey));
        this.leaveMine(true);
    }

    /**
     * Back to FOLLOW (idle drain, the follow goal takes over) and, when {@code climbOut}, out of the pit: if there is
     * no path to the owner, or the bot is below them and more than 8 blocks away, it moves to a safe spot beside them.
     */
    private void leaveMine(boolean climbOut) {
        this.dropTarget();
        this.idle = true;
        ServerPlayer owner = this.companion.owner();
        if (owner == null) {
            return;
        }
        if (this.companion.mode() == CompanionMode.MINE) {
            this.companion.trySetMode(CompanionMode.FOLLOW, owner, false);
        }
        if (climbOut && owner.level() == this.companion.level()) {
            boolean noPath = this.companion.getNavigation().createPath(owner, 0) == null;
            boolean below = this.companion.getY() < owner.getY() - 2.0D && this.companion.distanceTo(owner) > 8.0F;
            if (noPath || below) {
                this.companion.relocateNear(owner.blockPosition(), 1);
            }
        }
    }

    // --- drops and unloading --------------------------------------------------------------------------

    /** Hand-off sinks for one break: only things within arm's reach; the caller drops leftovers at the bot. */
    private Map<DropRouter.Stage, DropRouter.Sink<ItemStack>> sinks(ServerLevel level) {
        Map<DropRouter.Stage, DropRouter.Sink<ItemStack>> sinks = new EnumMap<>(DropRouter.Stage.class);
        SimpleContainer cargo = this.companion.cargo();
        sinks.put(DropRouter.Stage.OWN_CARGO, sink(() -> true, cargo::addItem));
        CarryBotEntity carry = CargoOps.nearbyCarryBot(this.companion, level, HANDOFF_RANGE);
        if (carry != null) {
            sinks.put(DropRouter.Stage.CARRY_BOT, sink(() -> true, stack -> carry.cargo().addItem(stack)));
        }
        Optional<HomePos> bound = this.companion.boundContainer();
        long now = level.getGameTime();
        if (bound.isPresent() && !this.backoff.blocked(DropRouter.Stage.BOUND_CONTAINER, now)) {
            Container container = CargoOps.containerAt(level, bound.get());
            if (container != null && CargoOps.inReach(this.companion, bound.get().pos())) {
                HomePos at = bound.get();
                sinks.put(DropRouter.Stage.BOUND_CONTAINER, sink(
                        () -> CargoOps.mayUse(this.companion, level, at).allowed(),
                        stack -> CargoOps.insert(container, stack)));
            }
        }
        ServerPlayer owner = this.companion.owner();
        if (owner != null && owner.level() == level && owner.distanceToSqr(this.companion) <= HANDOFF_RANGE
                * HANDOFF_RANGE) {
            sinks.put(DropRouter.Stage.OWNER, sink(() -> true, stack -> CargoOps.give(owner, stack)));
        }
        return sinks;
    }

    private static DropRouter.Sink<ItemStack> sink(BooleanSupplier available, UnaryOperator<ItemStack> offer) {
        return new DropRouter.Sink<>() {
            private Boolean cached;

            @Override
            public boolean available() {
                if (this.cached == null) {
                    this.cached = available.getAsBoolean();
                }
                return this.cached;
            }

            @Override
            public ItemStack offer(ItemStack stack) {
                return offer.apply(stack);
            }
        };
    }

    private void unload(ServerLevel level, SimpleContainer cargo) {
        if (this.target != null) {
            this.dropTarget();
        }
        long now = level.getGameTime();
        if (this.unloading.isEmpty()) {
            if (this.companion.tickCount < this.nextScanTick) {
                return;
            }
            this.nextScanTick = this.companion.tickCount + SCAN_INTERVAL;
            this.unloading = DropRouter.unloadTarget(this.reachableStages(level, cargo, now));
            this.repathIn = 0;
            this.stuck.reset();
            this.lastPos = this.companion.position();
            if (this.unloading.isEmpty()) {
                CargoOps.tell(this.companion, "message.nerocompanion.mine.cargo_full",
                        this.companion.getDisplayName());
                this.leaveMine(false);
            }
            return;
        }
        DropRouter.Stage stage = this.unloading.get();
        Vec3 at = this.unloadPoint(level, stage);
        if (at == null) {
            this.giveUpStage(stage, now);
            return;
        }
        double distance = this.companion.position().distanceTo(at);
        if (distance > CarryRules.REACH) {
            if (--this.repathIn <= 0) {
                this.repathIn = this.adjustedTickDelay(REPATH_INTERVAL);
                boolean path = this.companion.getNavigation().moveTo(at.x, at.y, at.z, 1.0D);
                Vec3 pos = this.companion.position();
                boolean unreachable = this.stuck.check(path, distance, pos.distanceTo(this.lastPos));
                this.lastPos = pos;
                if (unreachable) {
                    this.giveUpStage(stage, now);
                }
            }
            return;
        }
        this.companion.getNavigation().stop();
        int before = CargoOps.itemCount(cargo);
        switch (stage) {
            case CARRY_BOT -> {
                CarryBotEntity carry = CargoOps.nearbyCarryBot(this.companion, level, HANDOFF_RANGE);
                if (carry != null) {
                    CargoOps.handOver(cargo, carry.cargo());
                }
            }
            case BOUND_CONTAINER -> {
                DepositRules.Result<ItemStack> result = CargoOps.depositAll(this.companion, level,
                        this.companion.boundContainer().orElseThrow());
                if (result.denied()) {
                    CargoOps.tell(this.companion, "message.nerocompanion.carry.deposit_denied",
                            this.companion.getDisplayName(), Component.translatable(result.reasonKey()));
                } else if (!result.movedAny()) {
                    CargoOps.tell(this.companion, "message.nerocompanion.carry.container_full",
                            this.companion.getDisplayName());
                }
            }
            case OWNER -> {
                ServerPlayer owner = this.companion.owner();
                if (owner != null) {
                    CargoOps.handTo(cargo, owner);
                }
            }
            default -> {
            }
        }
        this.unloading = Optional.empty();
        if (CargoOps.itemCount(cargo) < before) {
            this.backoff.clear(stage);
            this.companion.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.MINE);
        } else {
            this.backoff.fail(stage, now); // refused, full or gone: the next pass falls through to the next stage
        }
    }

    private void giveUpStage(DropRouter.Stage stage, long now) {
        this.backoff.fail(stage, now);
        this.unloading = Optional.empty();
        this.companion.getNavigation().stop();
    }

    private EnumSet<DropRouter.Stage> reachableStages(ServerLevel level, SimpleContainer cargo, long now) {
        EnumSet<DropRouter.Stage> reachable = EnumSet.noneOf(DropRouter.Stage.class);
        if (!this.backoff.blocked(DropRouter.Stage.CARRY_BOT, now)
                && CargoOps.nearbyCarryBot(this.companion, level, UNLOAD_CARRY_RANGE) != null) {
            reachable.add(DropRouter.Stage.CARRY_BOT);
        }
        Optional<HomePos> bound = this.companion.boundContainer();
        if (bound.isPresent() && !this.backoff.blocked(DropRouter.Stage.BOUND_CONTAINER, now)) {
            Container container = CargoOps.containerAt(level, bound.get());
            if (container != null && this.companion.position().distanceToSqr(Vec3.atCenterOf(bound.get().pos()))
                    <= UNLOAD_RANGE * UNLOAD_RANGE) {
                if (CargoOps.mayUse(this.companion, level, bound.get()).allowed()
                        && CargoOps.hasRoomFor(container, cargo)) {
                    reachable.add(DropRouter.Stage.BOUND_CONTAINER);
                } else {
                    this.backoff.fail(DropRouter.Stage.BOUND_CONTAINER, now);
                }
            }
        }
        ServerPlayer owner = this.companion.owner();
        if (!this.backoff.blocked(DropRouter.Stage.OWNER, now) && owner != null && owner.level() == level
                && owner.distanceToSqr(this.companion) <= UNLOAD_RANGE * UNLOAD_RANGE) {
            reachable.add(DropRouter.Stage.OWNER);
        }
        return reachable;
    }

    private Vec3 unloadPoint(ServerLevel level, DropRouter.Stage stage) {
        switch (stage) {
            case CARRY_BOT: {
                CarryBotEntity carry = CargoOps.nearbyCarryBot(this.companion, level, UNLOAD_CARRY_RANGE);
                return carry == null ? null : carry.position();
            }
            case BOUND_CONTAINER: {
                Optional<HomePos> bound = this.companion.boundContainer();
                return bound.isPresent() && CargoOps.containerAt(level, bound.get()) != null
                        ? Vec3.atCenterOf(bound.get().pos()) : null;
            }
            case OWNER: {
                ServerPlayer owner = this.companion.owner();
                return owner != null && owner.level() == level ? owner.position() : null;
            }
            default:
                return null;
        }
    }
}
