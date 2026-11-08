package za.co.neroland.nerocompanion.personality.goal;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.bond.BondLevels;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.personality.ChatterEvent;
import za.co.neroland.nerocompanion.personality.ChatterService;

/**
 * Base for the trait idle goals (priority 7, FOLLOW and STAY). Cadence-based: {@link #canUse} only does real work
 * every {@link #interval()} ticks (a counter decrement otherwise), never scans per tick, and never runs while the
 * companion is perched, asleep, working, or away from its owner. In STAY a goal may look and emote but not walk
 * away from the stay point ({@link #mayMove()}). Every activity is time-boxed by {@link #duration}.
 */
public abstract class TraitIdleGoal extends Goal {

    protected final CompanionEntity companion;
    private int countdown;
    /** Ticks the current activity has run. */
    protected int ticks;
    /** Ticks the current activity may run; set by {@link #begin()}. */
    protected int duration;
    /** Degrees per tick while spinning (0 = not spinning); used by tricks. */
    private float spinStep;
    private int spinTicks;
    /** Game time before which no trick/flourish plays (shared by every trick branch of this goal). */
    private long nextTrickAt = Long.MIN_VALUE;

    /** Minimum game ticks between two tricks of one companion (30 s). */
    protected static final int TRICK_GAP = 600;

    protected TraitIdleGoal(CompanionEntity companion, EnumSet<Goal.Flag> flags) {
        this.companion = companion;
        this.setFlags(flags);
        this.countdown = 20 + companion.getRandom().nextInt(40);
    }

    /** Ticks between two real checks of {@link #begin()}. */
    protected int interval() {
        return 40;
    }

    /** Decide whether to start an activity now and set it up (fields, {@link #duration}). Return false to idle. */
    protected abstract boolean begin();

    /** One tick of the running activity. */
    protected void step() {
    }

    /** Called when the activity ends (also when preempted). */
    protected void end() {
    }

    @Override
    public final boolean canUse() {
        if (--this.countdown > 0) {
            return false;
        }
        this.countdown = Math.max(1, this.interval());
        if (!this.calm()) {
            return false;
        }
        this.ticks = 0;
        this.duration = 0;
        return this.begin();
    }

    @Override
    public boolean canContinueToUse() {
        return this.ticks < this.duration && this.calm();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public final void tick() {
        this.ticks++;
        if (this.spinTicks > 0) {
            this.spinTicks--;
            this.turn(this.companion.getYRot() + this.spinStep);
        }
        this.step();
    }

    @Override
    public final void stop() {
        this.spinTicks = 0;
        this.end();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Owner here, awake, not perched, not working, in FOLLOW or STAY. */
    protected boolean calm() {
        CompanionMode mode = this.companion.mode();
        return (mode == CompanionMode.FOLLOW || mode == CompanionMode.STAY) && this.companion.ownerHere()
                && !this.companion.isPerched() && !this.companion.isSleepingCompanion()
                && !this.companion.isWorking();
    }

    /** Walking is allowed in FOLLOW only (STAY holds its point). */
    protected boolean mayMove() {
        return this.companion.mode() == CompanionMode.FOLLOW;
    }

    @Nullable
    protected ServerPlayer owner() {
        return this.companion.owner();
    }

    protected int bond() {
        return BondLevels.level(this.companion.bondPoints());
    }

    protected boolean chance(float p) {
        return this.companion.getRandom().nextFloat() < p;
    }

    protected void moveTo(Vec3 target, double speed) {
        if (this.mayMove()) {
            this.companion.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    protected void lookAt(Vec3 point) {
        this.companion.getLookControl().setLookAt(point.x, point.y, point.z);
    }

    protected void turn(float yaw) {
        this.companion.setYRot(yaw);
        this.companion.yBodyRot = yaw;
        this.companion.yHeadRot = yaw;
    }

    /** Spin {@code degrees} over {@code ticks} ticks (tricks). */
    protected void spin(float degrees, int ticks) {
        this.spinTicks = Math.max(1, ticks);
        this.spinStep = degrees / this.spinTicks;
    }

    /** Whether a trick may start now (the {@link #TRICK_GAP} since the last one has passed). */
    protected boolean trickReady() {
        long now = this.companion.level().getGameTime();
        return this.nextTrickAt == Long.MIN_VALUE || now >= this.nextTrickAt || now < this.nextTrickAt - TRICK_GAP;
    }

    /** Records that a trick starts now. */
    protected void trickPlayed() {
        this.nextTrickAt = this.companion.level().getGameTime() + TRICK_GAP;
        this.companion.impulse(CompanionEntity.IMPULSE_TRICK);
    }

    protected void say(ChatterEvent event) {
        ChatterService.say(this.companion, event);
    }

    /** Whether the owner is looking at this companion (a narrow cone). */
    protected boolean ownerLooksAtMe() {
        ServerPlayer owner = this.owner();
        if (owner == null) {
            return false;
        }
        Vec3 view = owner.getViewVector(1.0F).normalize();
        Vec3 to = this.companion.getEyePosition().subtract(owner.getEyePosition());
        double length = to.length();
        if (length < 1.0E-3D || length > 12.0D) {
            return false;
        }
        return view.dot(to.scale(1.0D / length)) > 0.97D;
    }

    /** The nearest player other than the owner within {@code radius}, or null (spectators ignored). */
    @Nullable
    protected ServerPlayer strangerWithin(double radius) {
        if (!(this.companion.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer nearest = null;
        double best = radius * radius;
        for (ServerPlayer player : level.players()) {
            // Invisible players are not noticed (no free invisibility detector), like vanilla targeting.
            if (player.isSpectator() || player.isInvisible() || this.companion.isOwnedBy(player)) {
                continue;
            }
            double d = player.distanceToSqr(this.companion);
            if (d <= best) {
                best = d;
                nearest = player;
            }
        }
        return nearest;
    }

    /** A point {@code distance} blocks from the owner along {@code yawDegrees} (horizontal), at the owner's feet. */
    protected static Vec3 aroundOwner(ServerPlayer owner, float yawDegrees, double distance) {
        float r = yawDegrees * Mth.DEG_TO_RAD;
        return new Vec3(owner.getX() - Mth.sin(r) * distance, owner.getY(), owner.getZ() + Mth.cos(r) * distance);
    }
}
