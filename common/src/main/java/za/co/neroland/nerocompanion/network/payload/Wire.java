package za.co.neroland.nerocompanion.network.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/**
 * Small, version-stable wire helpers shared by the payload codecs. Strings are length-capped on read, lists are
 * count-capped, enums travel as ordinals clamped on read, identifiers as strings (an unparsable one reads as
 * empty): a malformed packet yields a harmless value, never an exception deep in a handler.
 */
public final class Wire {

    public static final int MAX_LIST = 64;
    public static final int MAX_STRING = 256;

    private Wire() {
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, path));
    }

    public static void mode(FriendlyByteBuf buf, CompanionMode mode) {
        buf.writeByte(mode.ordinal());
    }

    public static CompanionMode mode(FriendlyByteBuf buf) {
        return CompanionMode.byOrdinal(buf.readByte());
    }

    public static void string(FriendlyByteBuf buf, String value) {
        buf.writeUtf(value, MAX_STRING);
    }

    public static String string(FriendlyByteBuf buf) {
        return buf.readUtf(MAX_STRING);
    }

    public static void id(FriendlyByteBuf buf, Optional<Identifier> id) {
        buf.writeUtf(id.map(Identifier::toString).orElse(""), MAX_STRING);
    }

    public static Optional<Identifier> id(FriendlyByteBuf buf) {
        String raw = buf.readUtf(MAX_STRING);
        return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(raw));
    }

    public static <B extends FriendlyByteBuf, T> void list(B buf, List<T> list, BiConsumer<B, T> writer) {
        int count = Math.min(MAX_LIST, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    public static <B extends FriendlyByteBuf, T> List<T> list(B buf, Function<B, T> reader) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_LIST) {
            throw new IllegalArgumentException("list too long: " + count);
        }
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }
}
