package za.co.neroland.nerocompanion.client.look;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.look.Look;
import za.co.neroland.nerocompanion.look.LookKind;

/**
 * The client's copy of the server's skin and hat catalogue ({@code Payloads.LookCatalogue}, sent on join and after
 * a reload) plus the texture resolution the renderer needs: a skin's base, glow and animated-glow files per type, its
 * eye colour and model override; a hat's texture and glow. Without a catalogue (before join) every companion draws
 * its standard {@code textures/entity/<type>/default.png}. Client only.
 */
public final class ClientLooks {

    private static final Map<Identifier, Look> SKINS = new HashMap<>();
    private static final Map<Identifier, Look> HATS = new HashMap<>();
    /** Resolved files, keyed by {@code type|skin|frame} (the renderer asks every frame). */
    private static final Map<String, Identifier> FILES = new HashMap<>();

    private ClientLooks() {
    }

    public static synchronized void accept(List<Look> looks) {
        SKINS.clear();
        HATS.clear();
        FILES.clear();
        for (Look look : looks) {
            (look.kind() == LookKind.HAT ? HATS : SKINS).put(look.id(), look);
        }
    }

    public static synchronized void clear() {
        SKINS.clear();
        HATS.clear();
        FILES.clear();
    }

    /** Every look of {@code kind} in the catalogue (for the gallery capture harness). */
    public static synchronized List<Look> all(LookKind kind) {
        return new ArrayList<>((kind == LookKind.HAT ? HATS : SKINS).values());
    }

    /** Every look of {@code kind} that type {@code type} may wear (for the Skin Studio). */
    public static synchronized List<Look> forType(LookKind kind, Identifier type) {
        List<Look> out = new ArrayList<>();
        for (Look look : (kind == LookKind.HAT ? HATS : SKINS).values()) {
            if (look.allows(type)) {
                out.add(look);
            }
        }
        return out;
    }

    @Nullable
    public static synchronized Look skin(String id) {
        Identifier parsed = id.isEmpty() ? null : Identifier.tryParse(id);
        return parsed == null ? null : SKINS.get(parsed);
    }

    @Nullable
    public static synchronized Look hat(String id) {
        Identifier parsed = id.isEmpty() ? null : Identifier.tryParse(id);
        return parsed == null ? null : HATS.get(parsed);
    }

    /** The base texture of {@code type} wearing {@code skinId} ({@code ""} = standard). */
    public static synchronized Identifier texture(String type, String skinId) {
        return FILES.computeIfAbsent(type + "|" + skinId + "|base", key -> {
            Look look = skin(skinId);
            Optional<Identifier> file = look == null ? Optional.empty() : look.texture(type);
            return file.orElseGet(() -> standard(type, ""));
        });
    }

    /** The glow texture of {@code type} wearing {@code skinId} at animation tick {@code age}. */
    public static synchronized Identifier glow(String type, String skinId, float age) {
        Look look = skin(skinId);
        int frame = look == null || look.frames() <= 0 ? 0
                : Math.floorMod((int) (age / Math.max(1, look.frameTime())), look.frames());
        return FILES.computeIfAbsent(type + "|" + skinId + "|glow" + frame, key -> {
            Optional<Identifier> file = look == null ? Optional.empty() : look.glowFrame(type, frame);
            return file.orElseGet(() -> standard(type, "_glow"));
        });
    }

    /** The healthy-state eye colour ({@code 0xRRGGBB}) of {@code skinId}, or {@code fallback}. */
    public static synchronized int eyeColour(String skinId, int fallback) {
        Look look = skin(skinId);
        return look == null || look.eyeColor() < 0 ? fallback : look.eyeColor();
    }

    /** The model variant a skin draws with: its override, else the type. */
    public static synchronized String variant(String type, String skinId) {
        Look look = skin(skinId);
        return look == null || look.modelOverride().isEmpty() ? type : look.modelOverride();
    }

    private static Identifier standard(String type, String suffix) {
        return Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID,
                "textures/entity/" + type + "/default" + suffix + ".png");
    }
}
