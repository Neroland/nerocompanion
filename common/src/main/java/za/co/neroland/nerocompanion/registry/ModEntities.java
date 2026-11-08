package za.co.neroland.nerocompanion.registry;

import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import za.co.neroland.nerolandcore.entity.EntityRegistrationSupport;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.FlyingCompanion;
import za.co.neroland.nerocompanion.entity.Locomotion;
import za.co.neroland.nerocompanion.entity.types.CarryBotEntity;
import za.co.neroland.nerocompanion.entity.types.CombatBotEntity;
import za.co.neroland.nerocompanion.entity.types.MiningBotEntity;
import za.co.neroland.nerocompanion.entity.types.PersonalDroneEntity;
import za.co.neroland.nerocompanion.entity.types.RepairBotEntity;
import za.co.neroland.nerocompanion.entity.types.ScoutDroneEntity;
import za.co.neroland.nerocompanion.entity.types.ScraplingEntity;
import za.co.neroland.nerocompanion.fetch.FetchBallEntity;
import za.co.neroland.nerocompanion.platform.Services;

/**
 * Companion entity types: one {@code EntityType} per companion type id, all
 * {@link MobCategory#MISC}, never spawned naturally (no spawn placement), spawn eggs creative only.
 *
 * <p>Two-phase like the creatures mod: {@link #init()} registers the types, {@link #registerEntitySupport()}
 * declares their default attributes through Core's {@link EntityRegistrationSupport} (Fabric applies both
 * eagerly, so the second must follow the first).
 */
public final class ModEntities {

    public static final RegistrationProvider<EntityType<?>> ENTITY_TYPES =
            Services.REGISTRATION.provider(Registries.ENTITY_TYPE, NeroCompanionCommon.MOD_ID);

    // --- the seven companion types (hitbox and eye height per the visual-language sizes) -------------
    public static final RegistryEntry<EntityType<PersonalDroneEntity>> PERSONAL_DRONE = ENTITY_TYPES.register(
            "personal_drone", key -> EntityType.Builder.<PersonalDroneEntity>of(PersonalDroneEntity::new,
                    MobCategory.MISC).sized(0.50F, 0.50F).eyeHeight(0.30F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<EntityType<ScoutDroneEntity>> SCOUT_DRONE = ENTITY_TYPES.register(
            "scout_drone", key -> EntityType.Builder.<ScoutDroneEntity>of(ScoutDroneEntity::new,
                    MobCategory.MISC).sized(0.60F, 0.40F).eyeHeight(0.25F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<EntityType<MiningBotEntity>> MINING_BOT = ENTITY_TYPES.register(
            "mining_bot", key -> EntityType.Builder.<MiningBotEntity>of(MiningBotEntity::new,
                    MobCategory.MISC).sized(0.90F, 0.90F).eyeHeight(0.60F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<EntityType<CarryBotEntity>> CARRY_BOT = ENTITY_TYPES.register(
            "carry_bot", key -> EntityType.Builder.<CarryBotEntity>of(CarryBotEntity::new,
                    MobCategory.MISC).sized(0.90F, 1.20F).eyeHeight(0.95F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<EntityType<RepairBotEntity>> REPAIR_BOT = ENTITY_TYPES.register(
            "repair_bot", key -> EntityType.Builder.<RepairBotEntity>of(RepairBotEntity::new,
                    MobCategory.MISC).sized(0.70F, 1.00F).eyeHeight(0.80F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<EntityType<CombatBotEntity>> COMBAT_BOT = ENTITY_TYPES.register(
            "combat_bot", key -> EntityType.Builder.<CombatBotEntity>of(CombatBotEntity::new,
                    MobCategory.MISC).sized(0.80F, 1.40F).eyeHeight(1.15F).clientTrackingRange(8).build(key));
    public static final RegistryEntry<EntityType<ScraplingEntity>> SCRAPLING = ENTITY_TYPES.register(
            "scrapling", key -> EntityType.Builder.<ScraplingEntity>of(ScraplingEntity::new,
                    MobCategory.MISC).sized(0.55F, 0.50F).eyeHeight(0.35F).clientTrackingRange(8).build(key));

    /** A thrown Fetch Ball (lands as the ball item; see {@code FetchTargets}). */
    public static final RegistryEntry<EntityType<FetchBallEntity>> FETCH_BALL = ENTITY_TYPES.register("fetch_ball",
            key -> EntityType.Builder.<FetchBallEntity>of(FetchBallEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(key));

    /** Companion entity id -> locomotion, for type-file validation. Every companion entity is listed here. */
    private static final Map<Identifier, Locomotion> LOCOMOTION = Map.of(
            id("personal_drone"), Locomotion.FLYING,
            id("scout_drone"), Locomotion.FLYING,
            id("mining_bot"), Locomotion.GROUND,
            id("carry_bot"), Locomotion.GROUND,
            id("repair_bot"), Locomotion.GROUND,
            id("combat_bot"), Locomotion.GROUND,
            id("scrapling"), Locomotion.GROUND);

    /** Companion entity id -> its registry entry, for deploying a record. */
    private static final Map<Identifier, RegistryEntry<? extends EntityType<? extends CompanionEntity>>> BY_ID =
            Map.of(id("personal_drone"), PERSONAL_DRONE, id("scout_drone"), SCOUT_DRONE,
                    id("mining_bot"), MINING_BOT, id("carry_bot"), CARRY_BOT, id("repair_bot"), REPAIR_BOT,
                    id("combat_bot"), COMBAT_BOT, id("scrapling"), SCRAPLING);

    /** The seven companion type ids, in creative-tab order. */
    public static final java.util.List<Identifier> COMPANION_TYPES = java.util.List.of(id("personal_drone"),
            id("scout_drone"), id("mining_bot"), id("carry_bot"), id("repair_bot"), id("combat_bot"),
            id("scrapling"));

    private ModEntities() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, path);
    }

    /** Forces this class to initialise (and its types to register on Fabric). */
    public static void init() {
    }

    /** Declares default attributes. Must run after {@link #init()}. */
    public static void registerEntitySupport() {
        EntityRegistrationSupport entities = EntityRegistrationSupport.get(NeroCompanionCommon.MOD_ID);
        entities.registerAttributes(PERSONAL_DRONE, FlyingCompanion::createAttributes);
        entities.registerAttributes(SCOUT_DRONE, FlyingCompanion::createAttributes);
        entities.registerAttributes(MINING_BOT, MiningBotEntity::createAttributes);
        entities.registerAttributes(CARRY_BOT, CarryBotEntity::createAttributes);
        entities.registerAttributes(REPAIR_BOT, RepairBotEntity::createAttributes);
        entities.registerAttributes(COMBAT_BOT, CombatBotEntity::createAttributes);
        entities.registerAttributes(SCRAPLING, ScraplingEntity::createAttributes);
    }

    /** The locomotion of a companion entity id, or empty if the id is not a NeroCompanion companion entity. */
    public static Optional<Locomotion> locomotionOf(Identifier entityId) {
        return Optional.ofNullable(LOCOMOTION.get(entityId));
    }

    /** The entity type for a companion entity id. */
    public static Optional<EntityType<? extends CompanionEntity>> entityType(Identifier entityId) {
        RegistryEntry<? extends EntityType<? extends CompanionEntity>> entry = BY_ID.get(entityId);
        return entry == null ? Optional.empty() : Optional.of(entry.get());
    }
}
