package za.co.neroland.nerocompanion.ai.combat;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.energy.CompanionEnergy;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.types.CombatBotEntity;
import za.co.neroland.nerocompanion.personality.ChatterEvent;
import za.co.neroland.nerocompanion.personality.ChatterService;

/**
 * Closes on the target {@link GuardTargetGoal} chose and strikes in melee. Before <em>every</em> hit the owner's
 * authority is asked again ({@link OwnerAuthority#mayAttack}); a denial drops the target. A hit costs
 * {@link GuardRules#FE_PER_HIT} FE, is paid only once the damage is known to be non-zero, and is not made if paying
 * would dip below the reserve. Each hit broadcasts the lunge event ({@link CombatBotEntity#EVENT_LUNGE}); a kill
 * counts as a job done and says a "target down" line. Gives up once the bot or the target leaves the leash (guard
 * radius + {@link GuardRules#LEASH_SLACK}), when the target stands in lava, or after {@link GuardRules#GIVE_UP_TICKS}
 * without getting closer (then the target is skipped for {@link GuardRules#IGNORE_TICKS}); the follow / stay goal
 * brings it back. Tier I melee (no tier chip in 0.5.0); no ranged attack (no ranged chip in 0.5.0).
 */
public final class GuardMeleeGoal extends Goal {

    private static final int REPATH_TICKS = 10;

    private final CompanionEntity bot;
    private final ChaseProgress progress = new ChaseProgress();
    private int repathIn;
    private int cooldown;

    public GuardMeleeGoal(CompanionEntity bot, CompanionType type) {
        this.bot = bot;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean engageable(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.bot.level()
                && !target.isInLava() && GuardArea.withinLeash(this.bot, target);
    }

    private float damage() {
        CompanionType type = this.bot.typeDef();
        return GuardRules.damage(type == null ? 0.0D : type.attackDamage(), GuardRules.meleeTier());
    }

    @Override
    public boolean canUse() {
        // A chassis with no attack (a guard-protocol drone) never spends on 0-damage hits.
        if (this.damage() <= 0.0F) {
            return false;
        }
        return this.engageable(this.bot.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return this.engageable(this.bot.getTarget());
    }

    @Override
    public void start() {
        this.repathIn = 0;
        this.progress.reset();
    }

    @Override
    public void stop() {
        this.bot.getNavigation().stop();
        LivingEntity target = this.bot.getTarget();
        if (target != null && !this.engageable(target)) {
            this.bot.setTarget(null);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.bot.getTarget();
        if (target == null) {
            return;
        }
        this.bot.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double reach = this.bot.getBbWidth() * 2.0D + target.getBbWidth() * 0.5D;
        double distanceSq = this.bot.distanceToSqr(target);
        if (distanceSq > reach * reach) {
            if (this.progress.update(distanceSq)) {
                GuardArea.giveUp(this.bot, target);
                this.bot.setTarget(null);
                this.bot.getNavigation().stop();
                return;
            }
            if (--this.repathIn <= 0) {
                this.repathIn = this.adjustedTickDelay(REPATH_TICKS);
                this.bot.getNavigation().moveTo(target, 1.2D);
            }
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }
        if (distanceSq <= reach * reach) {
            this.strike(target);
        }
    }

    private void strike(LivingEntity target) {
        if (!(this.bot.level() instanceof ServerLevel server)) {
            return;
        }
        if (OwnerAuthority.mayAttack(this.bot, target).denied()) {
            this.bot.setTarget(null);
            return;
        }
        float damage = this.damage();
        if (damage <= 0.0F) {
            return;
        }
        CompanionEnergy energy = this.bot.energy();
        if (!GuardRules.canAffordHit(energy.stored(), energy.capacity()) || !energy.tryPay(GuardRules.FE_PER_HIT)) {
            return;
        }
        this.cooldown = GuardRules.cooldownTicks(GuardRules.meleeTier());
        this.progress.reset();
        server.broadcastEntityEvent(this.bot, CombatBotEntity.EVENT_LUNGE);
        server.playSound(null, this.bot.getX(), this.bot.getY(), this.bot.getZ(), za.co.neroland.nerocompanion.registry.ModSounds.COMBAT_LUNGE.get(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, za.co.neroland.nerocompanion.registry.ModSounds.pitch(this.bot.getRandom()));
        boolean hurt = target.hurtServer(server, this.bot.damageSources().mobAttack(this.bot), damage);
        if (hurt && !target.isAlive()) {
            this.bot.jobDone(za.co.neroland.nerocompanion.progression.CompanionProgress.Work.GUARD);
            ChatterService.say(this.bot, ChatterEvent.TARGET_DOWN);
            this.bot.setTarget(null);
        }
    }
}
