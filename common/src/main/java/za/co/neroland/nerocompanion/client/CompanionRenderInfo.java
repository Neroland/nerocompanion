package za.co.neroland.nerocompanion.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.types.CombatBotEntity;

/**
 * Per-frame companion facts for the model, render layers and pose hooks, the same on every line: on 1.21.2+ it
 * rides in {@code CompanionRenderer.State}, on 1.21.1 the renderer and model fill one per frame. Only synched,
 * non-personal data (no owner, no name, no position beyond the entity's own). The animation inputs of {@code 019}
 * section 4 - the mode, energy, flags, blends and impulses - are filled here and by
 * {@link za.co.neroland.nerocompanion.client.model.AnimTracks}. Client only.
 */
public final class CompanionRenderInfo {

    /** The entity's network id (key for client-side emote / chatter / animation state), or -1. */
    public int entityId = -1;
    /** False when the rendered mob is not a companion (then every other field is default). */
    public boolean companion;
    /** The companion type path ({@code personal_drone}); the entity type path for a non-companion. */
    public String type = "";
    public int trait;
    public int bondLevel;
    /** {@code CompanionEntity.FLAG_*} bits. */
    public byte flags;
    public float ageInTicks;
    /** The synched (effective) mode. */
    public CompanionMode mode = CompanionMode.FOLLOW;
    public int energyPct = 100;
    /** Synched skin id ({@code ""} = standard) and hat id ({@code ""} = none). */
    public String skin = "";
    public String hat = "";
    /** Walk cycle and head look (radians) as vanilla hands them to models. */
    public float walkPos;
    public float walkSpeed;
    public float headYaw;
    public float headPitch;
    /** A combat bot with a target (vanilla's synched aggressive flag). */
    public boolean aggressive;
    /** Ticks left of a combat lunge. */
    public int lungeTicks;
    /** Eased pose blends 0..1 ({@code 019} section 4.2), from {@code AnimTracks}. */
    public float sleepBlend;
    public float perchBlend;
    public float chargeBlend;
    /** The running one-shot impulse ({@code CompanionEntity.IMPULSE_*}) or -1, and ticks since it started. */
    public int impulse = -1;
    public float impulseT;
    /** Ticks of the OK_GREEN eye flash left after a charge completed. */
    public float chargeFlash;
    /** Ticks the current nap has lasted (eyes_low turns eyes_closed after 10). */
    public float napTicks;
    /** The Scout's danger alert is flashing. */
    public boolean dangerFlash;
    /** Drawn in the Skin Studio preview: healthy, awake, idle. */
    public boolean preview;

    public boolean perched() {
        return (this.flags & CompanionEntity.FLAG_PERCHED) != 0;
    }

    public boolean sleeping() {
        return (this.flags & CompanionEntity.FLAG_SLEEPING) != 0;
    }

    public boolean working() {
        return (this.flags & CompanionEntity.FLAG_WORKING) != 0;
    }

    public boolean napping() {
        return (this.flags & CompanionEntity.FLAG_NAPPING) != 0;
    }

    /** {@code CHARGE} pose ({@code 019} 4.2): asleep and working = charging on a Pad, Crank or in a Bay. */
    public boolean charging() {
        return this.working() && this.mode == CompanionMode.SLEEP;
    }

    /** Refills this from {@code entity}. */
    public void fill(Entity entity, float ageInTicks) {
        this.entityId = entity.getId();
        this.ageInTicks = ageInTicks;
        this.type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        this.preview = false;
        if (entity instanceof CompanionEntity companionEntity) {
            this.companion = true;
            this.trait = companionEntity.syncedTrait();
            this.bondLevel = companionEntity.syncedBondLevel();
            this.flags = companionEntity.syncedFlags();
            this.mode = companionEntity.syncedMode();
            this.energyPct = companionEntity.energyPercentSynced();
            this.skin = companionEntity.syncedSkin();
            this.hat = companionEntity.syncedHat();
            this.aggressive = companionEntity.isAggressive();
            this.lungeTicks = companionEntity instanceof CombatBotEntity bot ? bot.lungeTicks() : 0;
            LookPreview.apply(this);
        } else {
            this.companion = false;
            this.trait = 0;
            this.bondLevel = 0;
            this.flags = 0;
            this.mode = CompanionMode.FOLLOW;
            this.energyPct = 100;
            this.skin = "";
            this.hat = "";
            this.aggressive = false;
            this.lungeTicks = 0;
        }
        za.co.neroland.nerocompanion.client.model.AnimTracks.update(this);
    }

    /** Copies the animation facts (the 1.21.1 model fills its own copy from the entity). */
    public void copyFrom(CompanionRenderInfo o) {
        this.entityId = o.entityId;
        this.companion = o.companion;
        this.type = o.type;
        this.trait = o.trait;
        this.bondLevel = o.bondLevel;
        this.flags = o.flags;
        this.ageInTicks = o.ageInTicks;
        this.mode = o.mode;
        this.energyPct = o.energyPct;
        this.skin = o.skin;
        this.hat = o.hat;
        this.walkPos = o.walkPos;
        this.walkSpeed = o.walkSpeed;
        this.headYaw = o.headYaw;
        this.headPitch = o.headPitch;
        this.aggressive = o.aggressive;
        this.lungeTicks = o.lungeTicks;
        this.sleepBlend = o.sleepBlend;
        this.perchBlend = o.perchBlend;
        this.chargeBlend = o.chargeBlend;
        this.impulse = o.impulse;
        this.impulseT = o.impulseT;
        this.chargeFlash = o.chargeFlash;
        this.napTicks = o.napTicks;
        this.dangerFlash = o.dangerFlash;
        this.preview = o.preview;
    }
}
