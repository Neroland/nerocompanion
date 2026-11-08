package za.co.neroland.nerocompanion.registry;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionRecord;
import za.co.neroland.nerocompanion.platform.Services;

/** NeroCompanion's item data components. */
public final class ModDataComponents {

    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            Services.REGISTRATION.provider(Registries.DATA_COMPONENT_TYPE, NeroCompanionCommon.MOD_ID);

    /**
     * {@code nerocompanion:companion} - the companion inside a Capsule, as its full {@link CompanionRecord}.
     * Synced to clients through the record's stripped network codec (no owner UUID, no home position).
     */
    public static final RegistryEntry<DataComponentType<CompanionRecord>> COMPANION =
            COMPONENTS.register("companion", key -> DataComponentType.<CompanionRecord>builder()
                    .persistent(CompanionRecord.CODEC)
                    .networkSynchronized(CompanionRecord.STREAM_CODEC)
                    .build());

    /**
     * {@code nerocompanion:charge} - the stored companion's charge in percent, written on recall for the
     * tooltip (the client has no type registry to divide by). Display only; the record is authoritative.
     */
    public static final RegistryEntry<DataComponentType<Integer>> CHARGE =
            COMPONENTS.register("charge", key -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 100))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * {@code nerocompanion:blueprint} - a crafted, never-deployed companion: the type id only. Using the Capsule
     * deploys a brand-new companion of that type (fresh identity, full charge) owned by the user. The crafting
     * recipes put it on the result; it carries no personal data.
     */
    public static final RegistryEntry<DataComponentType<net.minecraft.resources.Identifier>> BLUEPRINT =
            COMPONENTS.register("blueprint", key -> DataComponentType.<net.minecraft.resources.Identifier>builder()
                    .persistent(net.minecraft.resources.Identifier.CODEC)
                    .networkSynchronized(net.minecraft.resources.Identifier.STREAM_CODEC)
                    .build());

    /**
     * {@code nerocompanion:ticket} - the Recovery Ledger ticket of the companion in a Capsule. Every recall, Bay break
     * and reclaim mints a new one; a Capsule deploys only while its ticket is the ledger's, so a copied Capsule can
     * never bring the same companion out twice. A random number, no personal data.
     */
    public static final RegistryEntry<DataComponentType<Long>> TICKET =
            COMPONENTS.register("ticket", key -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    private ModDataComponents() {
    }

    /** Classload-forcing no-op (init step 3, first). */
    public static void init() {
    }
}
