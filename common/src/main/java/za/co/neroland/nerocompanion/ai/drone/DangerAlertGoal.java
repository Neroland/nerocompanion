package za.co.neroland.nerocompanion.ai.drone;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.scout.DangerAlertRules;
import za.co.neroland.nerocompanion.scout.MarkRouting;

/**
 * The Scout Drone's Danger Alert (no goal flags; runs beside movement): every {@value DangerAlertRules#CHECK_TICKS}
 * ticks, if a hostile is within {@value DangerAlertRules#RANGE} blocks of the owner and behind them, send the owner a
 * {@link Payloads.Chatter} with {@link DroneWork#CHATTER_DANGER} (the client chirps and flashes the drone). At most
 * once per {@value DangerAlertRules#COOLDOWN_TICKS} ticks and once per mob (see {@link DangerAlertRules}). Owner only;
 * nothing is broadcast.
 */
public final class DangerAlertGoal extends Goal {

    private final CompanionEntity companion;
    private final DangerAlertRules.Cooldown cooldown = new DangerAlertRules.Cooldown();
    private final DangerAlertRules.Tracker tracker = new DangerAlertRules.Tracker();
    private long nextCheck;

    public DangerAlertGoal(CompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return this.companion.owner() != null && this.companion.ownerHere();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.companion.owner();
        if (owner == null || !(this.companion.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now < this.nextCheck) {
            return;
        }
        this.nextCheck = now + DangerAlertRules.CHECK_TICKS;
        if (owner.isCreative() || DangerAlertRules.inCombat(owner.getLastHurtMobTimestamp(), owner.tickCount)) {
            return;
        }
        Vec3 look = owner.getLookAngle();
        Set<Integer> inRange = new HashSet<>();
        Set<Integer> danger = new HashSet<>();
        double r2 = DangerAlertRules.RANGE * DangerAlertRules.RANGE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(DangerAlertRules.RANGE),
                m -> m.isAlive() && m instanceof Enemy && (!(m instanceof NeutralMob) || m.getTarget() == owner))) {
            double dx = mob.getX() - owner.getX();
            double dy = mob.getY() - owner.getY();
            double dz = mob.getZ() - owner.getZ();
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            inRange.add(mob.getId());
            if (DangerAlertRules.isDanger(look.x, look.z, dx, dy, dz)) {
                danger.add(mob.getId());
            }
        }
        if (!this.tracker.check(inRange, danger, this.cooldown, now)) {
            return;
        }
        if (!za.co.neroland.nerocompanion.privacy.CompanionPrivacy.chatterAllowed(level.getServer(),
                this.companion.ownerId())) {
            return;
        }
        Payloads.Chatter chirp = new Payloads.Chatter(this.companion.getId(), DroneWork.CHATTER_DANGER,
                this.companion.syncedTrait());
        MarkRouting.recipient(this.companion.ownerId(), level.players(), Player::getUUID,
                        p -> !Services.ENTITIES.isFakePlayer(p))
                .ifPresent(p -> Services.NETWORK.sendToPlayer(p, chirp));
    }
}
