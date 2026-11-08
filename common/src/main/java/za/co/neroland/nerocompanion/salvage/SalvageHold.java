package za.co.neroland.nerocompanion.salvage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerocompanion.NeroCompanionCommon;

/**
 * The Salvage Hold (overworld SavedData through Core's {@link SavedDataRecovery} guard): owner UUID → the items their
 * companions gathered after they died, the gathering companions' ids and the expiry. The rules are {@link HoldBook};
 * this class persists them. Player data: erased by owner UUID (with a {@code backupNow}) and purged by
 * {@code privacy.ledgerRetentionDays}.
 */
public final class SalvageHold extends SavedData {

    private static final AtomicBoolean ENCODE_WARNED = new AtomicBoolean();

    /**
     * One stack, encoded on its own: a stack another mod's component cannot encode becomes empty (and is dropped on
     * load) instead of failing the save of every owner's hold. Logged once, without data.
     */
    static final Codec<ItemStack> SAFE_STACK = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<Pair<ItemStack, T>> read = ItemStack.OPTIONAL_CODEC.decode(ops, input);
            return read.result().isPresent() ? read : DataResult.success(Pair.of(ItemStack.EMPTY, input));
        }

        @Override
        public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
            try {
                DataResult<T> written = ItemStack.OPTIONAL_CODEC.encode(stack, ops, prefix);
                if (written.result().isPresent()) {
                    return written;
                }
            } catch (RuntimeException failure) {
                // fall through to the empty stack
            }
            if (ENCODE_WARNED.compareAndSet(false, true)) {
                NeroCompanionCommon.LOGGER.warn("[NeroCompanion] a salvaged item could not be saved and was skipped");
            }
            return ItemStack.OPTIONAL_CODEC.encode(ItemStack.EMPTY, ops, prefix);
        }
    };

    private record Row(List<ItemStack> items, List<UUID> holders, long expiresAt, long touchedAt) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                SAFE_STACK.listOf().optionalFieldOf("items", List.of()).forGetter(Row::items),
                UUIDUtil.CODEC.listOf().optionalFieldOf("holders", List.of()).forGetter(Row::holders),
                Codec.LONG.optionalFieldOf("expires", 0L).forGetter(Row::expiresAt),
                Codec.LONG.optionalFieldOf("touched", 0L).forGetter(Row::touchedAt)
        ).apply(i, Row::new));
    }

    private static final Codec<SalvageHold> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Row.CODEC)
            .xmap(SalvageHold::fromRows, SalvageHold::toRows);

    public static final SavedDataType<SalvageHold> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, "salvage_hold"), SalvageHold::new, CODEC);
    public static final String RECOVERY_NAME = "nerocompanion:salvage_hold";

    private final HoldBook<ItemStack> book;

    public SalvageHold() {
        this(Map.of());
    }

    private SalvageHold(Map<UUID, HoldBook.Hold<ItemStack>> holds) {
        this.book = new HoldBook<>(holds);
        this.book.onChange(this::setDirty);
    }

    private static SalvageHold fromRows(Map<UUID, Row> rows) {
        Map<UUID, HoldBook.Hold<ItemStack>> holds = new HashMap<>();
        long now = System.currentTimeMillis();
        rows.forEach((owner, row) -> holds.put(owner, new HoldBook.Hold<>(
                row.items().stream().filter(stack -> !stack.isEmpty()).toList(), Set.copyOf(row.holders()),
                row.expiresAt(), row.touchedAt() > 0L ? row.touchedAt() : now)));
        return new SalvageHold(holds);
    }

    private Map<UUID, Row> toRows() {
        Map<UUID, Row> rows = new HashMap<>();
        this.book.all().forEach((owner, hold) -> rows.put(owner, new Row(
                hold.items().stream().filter(stack -> !stack.isEmpty()).toList(), List.copyOf(hold.holders()),
                hold.expiresAt(), hold.touchedAt())));
        return rows;
    }

    public static SalvageHold get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, SalvageHold::new, RECOVERY_NAME);
    }

    public static HoldBook<ItemStack> book(MinecraftServer server) {
        return get(server).book;
    }
}
