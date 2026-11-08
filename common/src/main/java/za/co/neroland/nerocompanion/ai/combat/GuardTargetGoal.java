package za.co.neroland.nerocompanion.ai.combat;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.personality.ChatterEvent;
import za.co.neroland.nerocompanion.personality.ChatterService;

/**
 * Target selector for the Combat Bot (a {@code target()} work goal, so it sits in the target selector). Scans the
 * guard area every {@link GuardRules#SCAN_INTERVAL} ticks through {@link GuardArea#findTarget}; keeps a target only
 * while it is alive, inside the leash, and still allowed by {@link OwnerAuthority#mayAttack} (re-checked on the same
 * cadence, and again by {@link GuardMeleeGoal} before every hit). While a target is held the bot is WORKING (active
 * drain, HUD busy, client guard loop) and says a "threat spotted" line on acquiring it.
 */
public final class GuardTargetGoal extends TargetGoal {

    private final CompanionEntity bot;
    private int nextScan;
    private int nextRecheck;

    public GuardTargetGoal(CompanionEntity bot, CompanionType type) {
        super(bot, false);
        this.bot = bot;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.bot.tickCount < this.nextScan) {
            return false;
        }
        // A chassis with no attack (a guard-protocol drone) never fights, so it does not pay for the scan either.
        if (this.bot.typeDef() != null && this.bot.typeDef().attackDamage() <= 0.0D) {
            this.nextScan = this.bot.tickCount + GuardRules.SCAN_INTERVAL * 4;
            return false;
        }
        this.nextScan = this.bot.tickCount + GuardRules.SCAN_INTERVAL;
        LivingEntity found = GuardArea.findTarget(this.bot);
        if (found == null) {
            return false;
        }
        this.targetMob = found;
        return true;
    }

    @Override
    public void start() {
        this.bot.setTarget(this.targetMob);
        this.nextRecheck = this.bot.tickCount + GuardRules.SCAN_INTERVAL;
        super.start();
        // Engaged: WORKING drives the active drain (003), the HUD "busy" and the client's guard loop (010 4.3).
        this.bot.setWorking(true);
        ChatterService.say(this.bot, ChatterEvent.THREAT_SPOTTED);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.bot.getTarget();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (!GuardArea.withinLeash(this.bot, target)) {
            return false;
        }
        if (this.bot.tickCount >= this.nextRecheck) {
            this.nextRecheck = this.bot.tickCount + GuardRules.SCAN_INTERVAL;
            if (OwnerAuthority.mayAttack(this.bot, target).denied()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void stop() {
        super.stop();
        this.bot.setWorking(false);
        this.nextScan = this.bot.tickCount + GuardRules.SCAN_INTERVAL;
    }
}
