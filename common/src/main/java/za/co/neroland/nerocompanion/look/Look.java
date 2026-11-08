package za.co.neroland.nerocompanion.look;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.network.payload.Wire;

/**
 * One skin or hat from {@code data/<ns>/skins/**} / {@code data/<ns>/hats/*.json} ({@code 010} section 6.1). Looks
 * only: a look never changes stats, hitbox, anchors or sounds. Texture fields are templates in sprite form
 * ({@code nerocompanion:entity/{type}/aurora}) where {@code {type}} is the companion type path and {@code {frame}}
 * the animated-glow frame. No player data; the whole catalogue is safe to send to every client (it is what draws
 * other players' companions), while which looks a player has unlocked goes to that player only.
 *
 * @param types    the companion types allowed ({@code "*"} = all)
 * @param eyeColor healthy-state eye colour {@code 0xRRGGBB}, or -1 for the default teal
 * @param swatch   Skin Studio card band colour {@code 0xRRGGBB}, or -1 for the default
 * @param frames   animated glow frame count (0 = none); {@code frameTime} ticks per frame
 * @param modelOverride the companion model variant this skin draws with ({@code scrapling/mossback}), or empty
 */
public record Look(Identifier id, LookKind kind, List<String> types, String texture, String glow, int eyeColor,
        int swatch, int frames, int frameTime, String frameTexture, String modelOverride, UnlockRule unlock) {

    public static final String ANY_TYPE = "*";

    public Look {
        types = List.copyOf(types);
        glow = glow == null ? "" : glow;
        frameTexture = frameTexture == null ? "" : frameTexture;
        modelOverride = modelOverride == null ? "" : modelOverride;
    }

    /** Whether this look may be worn by companion type {@code type}. */
    public boolean allows(Identifier type) {
        return this.types.contains(ANY_TYPE) || this.types.contains(type.toString());
    }

    /** A family look (one id for every type, texture paths keyed by {@code {type}}). */
    public boolean family() {
        return this.types.contains(ANY_TYPE);
    }

    /** {@code <type>/default}: the standard look, worn when no skin is set. */
    public boolean isDefault() {
        return this.unlock.kind() == UnlockRule.Kind.DEFAULT && this.id.getPath().endsWith("/default");
    }

    /** The base texture file for {@code type}, or empty when the template is malformed. */
    public Optional<Identifier> texture(String type) {
        return file(this.texture, type, 0);
    }

    /** The glow texture file for {@code type} (no animation), or empty when the look has none. */
    public Optional<Identifier> glow(String type) {
        return this.glow.isEmpty() ? Optional.empty() : file(this.glow, type, 0);
    }

    /** The glow file for animation frame {@code frame}; the static glow when the look is not animated. */
    public Optional<Identifier> glowFrame(String type, int frame) {
        if (this.frames <= 0 || this.frameTexture.isEmpty()) {
            return this.glow(type);
        }
        return file(this.frameTexture, type, Math.floorMod(frame, this.frames));
    }

    /** The lang key of this look's name: {@code skin.nerocompanion.personal_drone.graphite}. */
    public String nameKey() {
        return this.kind.langPrefix() + "." + this.id.getNamespace() + "." + this.id.getPath().replace('/', '.');
    }

    /** The optional per-look unlock sentence key ({@code <nameKey>.unlock}). */
    public String unlockKey() {
        return this.nameKey() + ".unlock";
    }

    /** {@code nerocompanion:entity/x/y} -> {@code nerocompanion:textures/entity/x/y.png}, with the placeholders filled. */
    static Optional<Identifier> file(String template, String type, int frame) {
        String filled = template.replace("{type}", type).replace("{frame}", Integer.toString(frame));
        Identifier sprite = Identifier.tryParse(filled);
        if (sprite == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Identifier.tryBuild(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png"));
    }

    public static void write(FriendlyByteBuf buf, Look look) {
        Wire.id(buf, Optional.of(look.id));
        buf.writeBoolean(look.kind == LookKind.HAT);
        Wire.list(buf, look.types, Wire::string);
        Wire.string(buf, look.texture);
        Wire.string(buf, look.glow);
        buf.writeInt(look.eyeColor);
        buf.writeInt(look.swatch);
        buf.writeVarInt(look.frames);
        buf.writeVarInt(look.frameTime);
        Wire.string(buf, look.frameTexture);
        Wire.string(buf, look.modelOverride);
        buf.writeVarInt(look.unlock.kind().ordinal());
        Wire.string(buf, look.unlock.arg());
    }

    public static Look read(FriendlyByteBuf buf) {
        Identifier id = Wire.id(buf).orElseGet(() -> Identifier.fromNamespaceAndPath("nerocompanion", "invalid"));
        LookKind kind = buf.readBoolean() ? LookKind.HAT : LookKind.SKIN;
        List<String> types = Wire.list(buf, Wire::string);
        String texture = Wire.string(buf);
        String glow = Wire.string(buf);
        int eye = buf.readInt();
        int swatch = buf.readInt();
        int frames = Math.max(0, Math.min(64, buf.readVarInt()));
        int frameTime = Math.max(1, Math.min(200, buf.readVarInt()));
        String frameTexture = Wire.string(buf);
        String modelOverride = Wire.string(buf);
        int rule = buf.readVarInt();
        UnlockRule.Kind[] kinds = UnlockRule.Kind.values();
        UnlockRule unlock = new UnlockRule(rule >= 0 && rule < kinds.length ? kinds[rule] : UnlockRule.Kind.HOOK,
                Wire.string(buf));
        return new Look(id, kind, types, texture, glow, eye, swatch, frames, frameTime, frameTexture, modelOverride,
                unlock);
    }
}
