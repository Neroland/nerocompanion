package za.co.neroland.nerocompanion.ai.pet;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.fetch.FetchTargets;
import za.co.neroland.nerocompanion.registry.ModItems;

/**
 * Fetch: the pet runs to a Fetch Ball <b>its owner</b> threw ({@link FetchRules#mayFetch} against the thrower UUID
 * {@link FetchTargets} recorded), picks it up, brings it back and hands it to the owner (inventory, else at their
 * feet), then counts a finished job ({@link CompanionEntity#jobDone()}, which feeds the bond through the personality
 * hooks). Balls thrown by anyone else are ignored. The carried ball is the companion's {@link FetchJob}, so it is
 * never lost to an unload, recall or parking. Scans for balls every {@value FetchRules#SCAN_INTERVAL} ticks.
 * Comfort, never combat: it moves and carries a toy, nothing else.
 */
public class PetFetchGoal extends Goal {

    private final CompanionEntity pet;
    @Nullable
    private ItemEntity ball;
    private long seekStarted;
    private int nextScan;
    private int nextPath;

    public PetFetchGoal(CompanionEntity pet, CompanionType type) {
        this.pet = pet;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean carrying() {
        return this.pet.job() instanceof FetchJob;
    }

    @Override
    public boolean canUse() {
        if (this.pet.isPerched()) {
            return false;
        }
        ServerPlayer owner = this.pet.owner();
        if (owner == null || owner.level() != this.pet.level()) {
            return false;
        }
        if (this.carrying()) {
            return FetchRules.withinReturn(this.pet.distanceToSqr(owner));
        }
        if (!this.pet.job().isNone() || this.pet.tickCount < this.nextScan) {
            return false;
        }
        this.nextScan = this.pet.tickCount + FetchRules.SCAN_INTERVAL;
        this.ball = findBall(this.pet, owner.getUUID());
        return this.ball != null;
    }

    /**
     * The nearest landed Fetch Ball within reach of {@code pet} that {@code owner} threw, or null. Server only; one
     * bounded entity query (call it on a cadence, not every tick).
     */
    @Nullable
    public static ItemEntity findBall(CompanionEntity pet, UUID owner) {
        if (!(pet.level() instanceof ServerLevel level)) {
            return null;
        }
        long now = level.getGameTime();
        Item ballItem = ModItems.FETCH_BALL.get();
        AABB box = pet.getBoundingBox().inflate(FetchRules.SCAN_RADIUS, FetchRules.SCAN_HEIGHT, FetchRules.SCAN_RADIUS);
        List<ItemEntity> balls = level.getEntitiesOfClass(ItemEntity.class, box, item -> item.isAlive()
                && item.getItem().is(ballItem) && isOwnersBall(item, owner, now));
        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity item : balls) {
            double d = pet.distanceToSqr(item);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        return nearest;
    }

    private static boolean isOwnersBall(ItemEntity item, UUID owner, long now) {
        return FetchRules.mayFetch(owner, FetchTargets.get(item.getUUID(), now)
                .map(FetchTargets.Target::thrower).orElse(null));
    }

    @Override
    public boolean canContinueToUse() {
        ServerPlayer owner = this.pet.owner();
        if (this.pet.isPerched() || owner == null || owner.level() != this.pet.level()) {
            return false;
        }
        if (this.carrying()) {
            return FetchRules.withinReturn(this.pet.distanceToSqr(owner));
        }
        return this.ball != null && this.ball.isAlive()
                && !FetchRules.seekTimedOut(this.seekStarted, this.pet.level().getGameTime());
    }

    @Override
    public void start() {
        this.pet.setWorking(true);
        this.seekStarted = this.pet.level().getGameTime();
        this.nextPath = 0;
    }

    @Override
    public void stop() {
        this.pet.setWorking(false);
        this.pet.getNavigation().stop();
        this.ball = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.pet.owner();
        if (owner == null) {
            return;
        }
        if (this.carrying()) {
            this.pet.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (FetchRules.withinDelivery(this.pet.distanceToSqr(owner))) {
                this.deliver(owner);
            } else if (--this.nextPath <= 0) {
                this.nextPath = FetchRules.SCAN_INTERVAL;
                this.pet.getNavigation().moveTo(owner, 1.2D);
            }
            return;
        }
        ItemEntity target = this.ball;
        if (target == null || !target.isAlive()) {
            return;
        }
        this.pet.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (FetchRules.withinPickup(this.pet.distanceToSqr(target))) {
            this.pickUp(target, owner.getUUID());
        } else if (--this.nextPath <= 0) {
            this.nextPath = FetchRules.SCAN_INTERVAL;
            this.pet.getNavigation().moveTo(target, 1.3D);
        }
    }

    private void pickUp(ItemEntity target, UUID owner) {
        if (!this.pet.job().isNone() || !isOwnersBall(target, owner, this.pet.level().getGameTime())) {
            this.ball = null;
            return;
        }
        ItemStack stack = target.getItem().copy();
        FetchTargets.remove(target.getUUID());
        target.discard();
        this.ball = null;
        this.pet.setJob(new FetchJob(stack));
        this.nextPath = 0;
        this.pet.level().playSound(null, this.pet.getX(), this.pet.getY(), this.pet.getZ(), za.co.neroland.nerocompanion.registry.ModSounds.FETCH_CATCH.get(),
                SoundSource.NEUTRAL, 0.6F, za.co.neroland.nerocompanion.registry.ModSounds.pitch(this.pet.getRandom()));
    }

    private void deliver(ServerPlayer owner) {
        if (handBack(this.pet, owner)) {
            this.pet.getNavigation().stop();
        }
    }

    /**
     * Hands a carried ball to {@code owner} (inventory, else at their feet) and counts a finished job. Also used
     * from the shoulder: a pet that perched while carrying gives the ball back instead of keeping it.
     *
     * @return false when the pet carries nothing
     */
    public static boolean handBack(CompanionEntity pet, ServerPlayer owner) {
        if (!(pet.job() instanceof FetchJob job)) {
            return false;
        }
        ItemStack stack = job.carried().copy();
        pet.setJob(null);
        // Into the inventory, or at the owner's feet when it is full.
        //? if >=26.3 {
        /*owner.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        owner.getInventory().placeItemBackInInventory(stack);
        //?}
        pet.jobDone();
        return true;
    }
}
