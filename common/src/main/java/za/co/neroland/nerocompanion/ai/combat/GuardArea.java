package za.co.neroland.nerocompanion.ai.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.authority.OwnerAuthority;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * Where a Combat Bot guards and whom it would fight there. Server only. The anchor is the owner in GUARD and the
 * stay point in STAY; the owner is always the stored one (resolved by UUID), never a nearby player.
 */
public final class GuardArea {

    /** Per bot: entity id -> game tick until which a target given up as unreachable is skipped. Server thread. */
    private static final Map<CompanionEntity, Map<Integer, Long>> IGNORED = new WeakHashMap<>();

    private GuardArea() {
    }

    /** The guard anchor, or null when there is none (owner offline or elsewhere, in GUARD). */
    @Nullable
    public static Vec3 anchor(CompanionEntity bot) {
        if (bot.effectiveMode() == CompanionMode.STAY) {
            return bot.stayAnchor();
        }
        ServerPlayer owner = bot.owner();
        if (owner == null || owner.level() != bot.level()) {
            return null;
        }
        return owner.position();
    }

    public static int rangeBonus(CompanionEntity bot) {
        return ChipEffects.rangeBonus(bot.activeChips());
    }

    /** Both the bot and {@code target} are inside the leash around the anchor. */
    public static boolean withinLeash(CompanionEntity bot, Entity target) {
        Vec3 anchor = anchor(bot);
        if (anchor == null) {
            return false;
        }
        int bonus = rangeBonus(bot);
        return GuardRules.withinLeash(bot.position().distanceToSqr(anchor), bonus)
                && GuardRules.withinLeash(target.position().distanceToSqr(anchor), bonus);
    }

    /** Remembers {@code target} as unreachable for {@link GuardRules#IGNORE_TICKS}. */
    public static void giveUp(CompanionEntity bot, Entity target) {
        long until = bot.level().getGameTime() + GuardRules.IGNORE_TICKS;
        IGNORED.computeIfAbsent(bot, key -> new HashMap<>()).put(target.getId(), until);
    }

    private static boolean ignored(CompanionEntity bot, Entity entity, long now) {
        Map<Integer, Long> map = IGNORED.get(bot);
        if (map == null) {
            return false;
        }
        map.values().removeIf(until -> until <= now);
        Long until = map.get(entity.getId());
        return until != null && until > now;
    }

    /** {@code entity} hurt or is going after the owner or the bot (the aggressor rule). */
    private static boolean aggressor(LivingEntity entity, ServerPlayer owner, CompanionEntity bot) {
        if (entity == owner.getLastHurtByMob() || entity == bot.getLastHurtByMob()) {
            return true;
        }
        if (entity instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            return target == owner || target == bot;
        }
        return false;
    }

    /**
     * Scans the leash area once (callers run this on a {@link GuardRules#SCAN_INTERVAL} cadence) and returns the
     * target {@link GuardRules#select} picks, each candidate checked with {@link OwnerAuthority#mayAttack}.
     * Non-aggressors count only inside the guard radius.
     */
    @Nullable
    public static LivingEntity findTarget(CompanionEntity bot) {
        ServerPlayer owner = bot.owner();
        Vec3 anchor = anchor(bot);
        if (owner == null || owner.level() != bot.level() || anchor == null) {
            return null;
        }
        int bonus = rangeBonus(bot);
        double radius = GuardRules.guardRadius(bonus);
        double radiusSq = radius * radius;
        double leash = GuardRules.leashRadius(bonus);
        double leashSq = leash * leash;
        AABB box = new AABB(anchor.x - leash, anchor.y - leash, anchor.z - leash,
                anchor.x + leash, anchor.y + leash, anchor.z + leash);
        long now = bot.level().getGameTime();
        List<LivingEntity> seen = bot.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != bot && e != owner && e.isAlive() && !e.isSpectator() && !e.isInLava()
                        && e.position().distanceToSqr(anchor) <= leashSq);
        List<GuardRules.Candidate<LivingEntity>> candidates = new ArrayList<>(seen.size());
        for (LivingEntity entity : seen) {
            if (ignored(bot, entity, now)) {
                continue;
            }
            GuardRules.Kind kind = GuardRules.kindOf(entity instanceof Enemy, entity instanceof NeutralMob,
                    entity instanceof Player);
            boolean inRadius = entity.position().distanceToSqr(anchor) <= radiusSq;
            candidates.add(new GuardRules.Candidate<>(entity, kind, aggressor(entity, owner, bot), inRadius,
                    bot.distanceToSqr(entity)));
        }
        return GuardRules.select(candidates, entity -> OwnerAuthority.mayAttack(bot, entity)).orElse(null);
    }
}
