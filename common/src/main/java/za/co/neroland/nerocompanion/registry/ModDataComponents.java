package za.co.neroland.nerocompanion.registry;

import com.mojang.serialization.Codec;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.platform.Services;
//?} else {
/*import net.minecraft.nbt.NbtOps;
*///?}

import za.co.neroland.nerocompanion.entity.CompanionRecord;

/** NeroCompanion's item data components. */
public final class ModDataComponents {

    //? if >=1.20.5 {
    private static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            Services.REGISTRATION.provider(Registries.DATA_COMPONENT_TYPE, NeroCompanionCommon.MOD_ID);

    /**
     * {@code nerocompanion:companion} - the companion inside a Capsule, as its full {@link CompanionRecord}.
     * Synced to clients through the record's stripped network codec (no owner UUID, no home position).
     */
    public static final StackData<CompanionRecord> COMPANION =
            new StackData<>(COMPONENTS.register("companion", key -> DataComponentType.<CompanionRecord>builder()
                    .persistent(CompanionRecord.CODEC)
                    .networkSynchronized(CompanionRecord.STREAM_CODEC)
                    .build()));

    /**
     * {@code nerocompanion:charge} - the stored companion's charge in percent, written on recall for the
     * tooltip (the client has no type registry to divide by). Display only; the record is authoritative.
     */
    public static final StackData<Integer> CHARGE =
            new StackData<>(COMPONENTS.register("charge", key -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 100))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build()));

    /**
     * {@code nerocompanion:blueprint} - a crafted, never-deployed companion: the type id only. Using the Capsule
     * deploys a brand-new companion of that type (fresh identity, full charge) owned by the user. The crafting
     * recipes put it on the result; it carries no personal data.
     */
    public static final StackData<net.minecraft.resources.Identifier> BLUEPRINT =
            new StackData<>(COMPONENTS.register("blueprint", key -> DataComponentType.<net.minecraft.resources.Identifier>builder()
                    .persistent(net.minecraft.resources.Identifier.CODEC)
                    .networkSynchronized(net.minecraft.resources.Identifier.STREAM_CODEC)
                    .build()));

    /**
     * {@code nerocompanion:ticket} - the Recovery Ledger ticket of the companion in a Capsule. Every recall, Bay break
     * and reclaim mints a new one; a Capsule deploys only while its ticket is the ledger's, so a copied Capsule can
     * never bring the same companion out twice. A random number, no personal data.
     */
    public static final StackData<Long> TICKET =
            new StackData<>(COMPONENTS.register("ticket", key -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build()));

    public record StackData<T>(RegistryEntry<DataComponentType<T>> entry) {
        public @Nullable T get(ItemStack stack) {
            return stack.get(entry.get());
        }

        public void set(ItemStack stack, T value) {
            stack.set(entry.get(), value);
        }

        public void remove(ItemStack stack) {
            stack.remove(entry.get());
        }

        public boolean has(ItemStack stack) {
            return stack.has(entry.get());
        }
    }
    //?} else {
    /*public static final StackData<CompanionRecord> COMPANION = new StackData<>("nerocompanion:companion",
            CompanionRecord.CODEC.xmap(CompanionRecord::forNetwork, CompanionRecord::forNetwork));
    public static final StackData<Integer> CHARGE = new StackData<>("nerocompanion:charge", Codec.intRange(0, 100));
    public static final StackData<net.minecraft.resources.Identifier> BLUEPRINT =
            new StackData<>("nerocompanion:blueprint", net.minecraft.resources.Identifier.CODEC);
    public static final StackData<Long> TICKET = new StackData<>("nerocompanion:ticket", Codec.LONG);

    public record StackData<T>(String key, Codec<T> codec) {
        public @Nullable T get(ItemStack stack) {
            var tag = stack.getTag();
            if (tag == null || !tag.contains(key)) {
                return null;
            }
            return codec.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(null);
        }

        public void set(ItemStack stack, T value) {
            var encoded = codec.encodeStart(NbtOps.INSTANCE, value).result()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid NeroCompanion item data: " + key));
            stack.getOrCreateTag().put(key, encoded);
        }

        public void remove(ItemStack stack) {
            stack.removeTagKey(key);
        }

        public boolean has(ItemStack stack) {
            return stack.getTag() != null && stack.getTag().contains(key);
        }
    }
    *///?}

    private ModDataComponents() {
    }

    /** Classload-forcing no-op (init step 3, first). */
    public static void init() {
    }
}
