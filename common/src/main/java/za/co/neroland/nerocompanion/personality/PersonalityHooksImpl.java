package za.co.neroland.nerocompanion.personality;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerocompanion.bond.BondRules;
import za.co.neroland.nerocompanion.bond.BondService;
import za.co.neroland.nerocompanion.chip.ChipEffects;
import za.co.neroland.nerocompanion.chip.ChipKinds;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.personality.goal.TraitGoals;

/**
 * Personality and bond, plugged into the companion through {@link PersonalityHooks}. Server only. Every call is
 * cheap: {@link #onTick} runs once per 20-tick evaluation, chatter is throttled, bond accrual is a counter.
 */
public final class PersonalityHooksImpl implements PersonalityHooks {

    /** Common init (integration agent: {@code PersonalityHooksImpl.register();} in {@code ModCompanionContent}). */
    public static void register() {
        PersonalityHooks.set(new PersonalityHooksImpl());
    }

    @Override
    public void onTick(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return;
        }
        Mood mood = Mood.of(companion);
        ServerPlayer owner = companion.owner();
        boolean near = owner != null && companion.ownerHere()
                && owner.distanceToSqr(companion) <= BondRules.NEAR_DISTANCE * BondRules.NEAR_DISTANCE;
        this.grant(companion, mood.bond.sample(near, level.getGameTime()));

        if (companion.isSleepingCompanion()) {
            return;
        }
        if (mood.greetedEntity != companion.getId() && near) {
            mood.greetedEntity = companion.getId();
            ChatterService.say(companion, ChatterEvent.DEPLOY);
            return;
        }
        boolean low = companion.energyPercentSynced() <= NeroCompanionConfig.limits().lowBatteryPercent();
        if (low && !mood.lowBattery) {
            mood.lowBattery = true;
            ChatterService.say(companion, ChatterEvent.LOW_BATTERY);
            return;
        }
        if (!low) {
            mood.lowBattery = false;
        }
        CompanionMode mode = companion.effectiveMode();
        if (near && !companion.isWorking() && !companion.isNapping() && (mode == CompanionMode.FOLLOW || mode == CompanionMode.STAY)
                && companion.getRandom().nextDouble() < ChatterCadence.idleChance(ChatterService.volume(companion))) {
            ChatterService.say(companion, ChatterEvent.IDLE);
        }
    }

    @Override
    public InteractionResult onPetted(CompanionEntity companion, Player owner) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        Mood mood = Mood.of(companion);
        long now = level.getGameTime();
        boolean earns = mood.bond.petReady(now);
        if (earns) {
            this.grant(companion, mood.bond.pet(now));
        }
        Trait trait = Trait.byIndex(companion.trait());
        if (trait == Trait.GRUMPY && !mood.pettedOnce) {
            // Grumpy turns its back the first time. It still likes you.
            float yaw = owner.getYRot();
            companion.setYRot(yaw);
            companion.yBodyRot = yaw;
            companion.yHeadRot = yaw;
        }
        mood.pettedOnce = true;
        companion.impulse(CompanionEntity.IMPULSE_PET);
        if (!ChipEffects.has(companion.activeChips(), ChipKinds.SILENT_RUNNING)) {
            // Every pet gets a heart and a soft chime; only an off-cooldown pet earns points.
            level.sendParticles(za.co.neroland.nerocompanion.registry.ModParticles.BOND_HEART.get(), companion.getX(),
                    companion.getY() + companion.getBbHeight() + 0.3D, companion.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
            level.playSound(null, companion.getX(), companion.getY(), companion.getZ(),
                    za.co.neroland.nerocompanion.registry.ModSounds.PET_PURR.get(), SoundSource.NEUTRAL, 0.5F, za.co.neroland.nerocompanion.registry.ModSounds.pitch(companion.getRandom()));
        }
        ChatterService.say(companion, ChatterEvent.PETTED);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void onJobDone(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel level)) {
            return;
        }
        this.grant(companion, Mood.of(companion).bond.job(level.getGameTime()));
        ChatterService.say(companion, ChatterEvent.JOB_DONE);
    }

    @Override
    public List<Goal> idleGoals(CompanionEntity companion) {
        return TraitGoals.create(companion, Trait.byIndex(companion.trait()));
    }

    private void grant(CompanionEntity companion, int points) {
        int level = BondService.grant(companion, points);
        if (level <= 0) {
            return;
        }
        ChatterService.say(companion, ChatterEvent.BOND_UP);
        ServerPlayer owner = companion.owner();
        if (owner != null) {
            // The owner's own companion name; no position.
            owner.sendSystemMessage(Component.translatable("message.nerocompanion.bond_up",
                    companion.getDisplayName(), level), true);
            if (!ChipEffects.has(companion.activeChips(), ChipKinds.SILENT_RUNNING)) {
                companion.level().playSound(null, companion.getX(), companion.getY(), companion.getZ(),
                        za.co.neroland.nerocompanion.registry.ModSounds.BOND_LEVELUP.get(), SoundSource.NEUTRAL, 0.6F, 1.0F);
            }
        }
    }
}
