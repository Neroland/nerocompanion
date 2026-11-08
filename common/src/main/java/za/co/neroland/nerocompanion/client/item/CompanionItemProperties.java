package za.co.neroland.nerocompanion.client.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.incubation.Incubation;
import za.co.neroland.nerocompanion.item.ScrapEggItem;
import za.co.neroland.nerocompanion.registry.ModDataComponents;
import za.co.neroland.nerocompanion.registry.ModEntities;

/**
 * The two item-model properties the art reads:
 * {@code nerocompanion:capsule_type} (the companion or blueprint type in a Capsule; empty = the empty Capsule) and
 * {@code nerocompanion:egg_stage} (a Scrap Egg's carried incubation: 0, 1 from 50 %, 2 from 90 %). From 1.21.4 they
 * are {@code select} properties for {@code items/capsule.json} and {@code items/scrap_egg.json}; on 1.21.1 they are
 * generic clamped properties the {@code models/item} overrides test (capsule: type index / 10; egg: 0, 0.5, 1).
 * Registered once from client init, before models load. Client only.
 */
public final class CompanionItemProperties {

    public static final Identifier CAPSULE_TYPE = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
            "capsule_type");
    public static final Identifier EGG_STAGE = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
            "egg_stage");

    private CompanionItemProperties() {
    }

    /** The type path a Capsule shows ({@code ""} when empty). */
    public static String capsuleType(ItemStack stack) {
        CompanionRecord record = stack.get(ModDataComponents.COMPANION.get());
        Identifier type = record != null ? record.type() : stack.get(ModDataComponents.BLUEPRINT.get());
        return type == null ? "" : type.getPath();
    }

    /** The egg stage 0..2 from the progress an egg carries. */
    public static int eggStage(ItemStack stack) {
        return stage(Incubation.progressOf(ScrapEggItem.carriedTicks(stack)));
    }

    /** 0 below 50 %, 1 from 50 %, 2 from 90 %. */
    public static int stage(float progress) {
        return progress >= 0.9F ? 2 : progress >= 0.5F ? 1 : 0;
    }

    public static void register() {
        //? if >=1.21.4 {
        put(CAPSULE_TYPE, CapsuleType.TYPE);
        put(EGG_STAGE, EggStage.TYPE);
        //?} else {
        /*net.minecraft.client.renderer.item.ItemProperties.registerGeneric(CAPSULE_TYPE, (stack, level, entity, seed) -> {
            int index = ModEntities.COMPANION_TYPES.stream().map(Identifier::getPath).toList()
                    .indexOf(capsuleType(stack));
            return index < 0 ? 0.0F : (index + 1) / 10.0F;
        });
        net.minecraft.client.renderer.item.ItemProperties.registerGeneric(EGG_STAGE,
                (stack, level, entity, seed) -> stack.getItem() instanceof ScrapEggItem ? eggStage(stack) / 2.0F : 0.0F);
        *///?}
    }

    //? if >=1.21.4 {
    /**
     * Adds a select property type to vanilla's id mapper. The mapper is private outside Fabric's access widener and
     * Forge has no registration event, so it is found by type (not name) on every loader; failing that the Capsule and
     * Scrap Egg simply show their base icon.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void put(Identifier id, Object type) {
        try {
            for (java.lang.reflect.Field field : net.minecraft.client.renderer.item.properties.select
                    .SelectItemModelProperties.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && net.minecraft.util.ExtraCodecs.LateBoundIdMapper.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    ((net.minecraft.util.ExtraCodecs.LateBoundIdMapper) field.get(null)).put(id, type);
                    return;
                }
            }
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] item model property {} not registered: no id mapper", id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            NeroCompanionCommon.LOGGER.warn("[NeroCompanion] item model property {} not registered ({})", id,
                    e.getClass().getSimpleName());
        }
    }
    //?}

    /** Index of {@code type} in the 1.21.1 capsule overrides (also used by the tests). */
    public static int overrideIndex(String type) {
        return ModEntities.COMPANION_TYPES.stream().map(Identifier::getPath).toList().indexOf(type);
    }

    //? if >=1.21.4 {
    /** {@code nerocompanion:capsule_type}. */
    public record CapsuleType() implements net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty<String> {
        public static final Type<CapsuleType, String> TYPE = Type.create(
                com.mojang.serialization.MapCodec.unit(new CapsuleType()), com.mojang.serialization.Codec.STRING);

        @Override
        public String get(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.client.multiplayer.ClientLevel level,
                @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity, int seed,
                net.minecraft.world.item.ItemDisplayContext context) {
            return capsuleType(stack);
        }

        @Override
        public Type<CapsuleType, String> type() {
            return TYPE;
        }
        //? if >=1.21.5 {

        @Override
        public com.mojang.serialization.Codec<String> valueCodec() {
            return com.mojang.serialization.Codec.STRING;
        }
        //?}
    }

    /** {@code nerocompanion:egg_stage}. */
    public record EggStage() implements net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty<String> {
        public static final Type<EggStage, String> TYPE = Type.create(
                com.mojang.serialization.MapCodec.unit(new EggStage()), com.mojang.serialization.Codec.STRING);

        @Override
        public String get(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.client.multiplayer.ClientLevel level,
                @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity, int seed,
                net.minecraft.world.item.ItemDisplayContext context) {
            return Integer.toString(eggStage(stack));
        }

        @Override
        public Type<EggStage, String> type() {
            return TYPE;
        }
        //? if >=1.21.5 {

        @Override
        public com.mojang.serialization.Codec<String> valueCodec() {
            return com.mojang.serialization.Codec.STRING;
        }
        //?}
    }
    //?}
}
