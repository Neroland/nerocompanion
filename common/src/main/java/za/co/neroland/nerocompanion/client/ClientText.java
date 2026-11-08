package za.co.neroland.nerocompanion.client;

import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.client.gui.UiFormat;
import za.co.neroland.nerocompanion.entity.CompanionMode;

/** Shared display strings: names, type and trait names, durations. Client only; every word is a lang key. */
public final class ClientText {

    private static final String[] TRAITS = {"curious", "loyal", "grumpy", "sleepy", "showoff", "shy"};

    private ClientText() {
    }

    public static Component typeName(Optional<Identifier> type) {
        return type.map(id -> (Component) Component.translatable("entity." + id.getNamespace() + "." + id.getPath()))
                .orElse(Component.translatable("gui.nerocompanion.unknown_type"));
    }

    /** The filtered record name, else the type name. */
    public static Component displayName(String name, Optional<Identifier> type) {
        return name.isEmpty() ? typeName(type) : Component.literal(name);
    }

    public static String traitId(int trait) {
        return TRAITS[Math.floorMod(trait, TRAITS.length)];
    }

    public static Component traitName(int trait) {
        return Component.translatable("trait.nerocompanion." + traitId(trait));
    }

    public static Component modeName(CompanionMode mode) {
        return Component.translatable("mode.nerocompanion." + mode.id());
    }

    public static Component duration(double minutes) {
        UiFormat.Duration d = UiFormat.duration(minutes);
        return Component.translatable(d.key(), d.args());
    }

    /** {@code sprite index} of a type on the sheet's type rows (unknown types use the first glyph). */
    public static int typeIndex(Optional<Identifier> type) {
        String path = type.map(Identifier::getPath).orElse("");
        return switch (path) {
            case "scout_drone" -> 1;
            case "mining_bot" -> 2;
            case "carry_bot" -> 3;
            case "repair_bot" -> 4;
            case "combat_bot" -> 5;
            case "scrapling" -> 6;
            default -> 0;
        };
    }
}
