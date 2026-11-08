package za.co.neroland.nerocompanion.look;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.type.ValidationIssue;

/**
 * Reads one skin or hat JSON ({@code 010} section 6.1). Problems are issues, never exceptions: a bad optional field
 * is dropped ({@code FIELD_DEFAULTED}); a file without a valid {@code types}, {@code texture} or {@code unlock} is
 * skipped ({@code DROPPED}). Pure: the known companion types and model variants are passed in.
 */
public final class LookParser {

    private LookParser() {
    }

    /**
     * Parses {@code json} as look {@code id}.
     *
     * @param knownTypes     companion type ids a look may name ({@code types})
     * @param knownOverrides model override ids a skin may name ({@code model_override}); hats ignore the field
     */
    public static Optional<Look> parse(Identifier id, LookKind kind, JsonElement json, Set<Identifier> knownTypes,
            Set<Identifier> knownOverrides, List<ValidationIssue> issues) {
        if (json == null || !json.isJsonObject()) {
            issues.add(drop(id, "(file)", "is not a JSON object"));
            return Optional.empty();
        }
        JsonObject o = json.getAsJsonObject();
        List<String> types = types(id, o, knownTypes, issues);
        if (types.isEmpty()) {
            return Optional.empty();
        }
        String texture = string(o, "texture");
        if (texture == null || Identifier.tryParse(texture.replace("{type}", "t").replace("{frame}", "0")) == null) {
            issues.add(drop(id, "texture", "is missing or not a sprite id"));
            return Optional.empty();
        }
        boolean family = types.contains(Look.ANY_TYPE);
        if (kind == LookKind.SKIN && family && !texture.contains("{type}")) {
            issues.add(drop(id, "texture", "a family skin (types [\"*\"]) needs {type} in its texture path"));
            return Optional.empty();
        }
        String glow = string(o, "glow");
        if (glow != null && Identifier.tryParse(glow.replace("{type}", "t")) == null) {
            issues.add(field(id, "glow", "is not a sprite id; no glow"));
            glow = null;
        }
        Optional<UnlockRule> unlock = unlock(id, o, issues);
        if (unlock.isEmpty()) {
            return Optional.empty();
        }
        int eye = colour(id, o, "eye_color", issues);
        int swatch = colour(id, o, "swatch", issues);
        int frames = 0;
        int frameTime = 4;
        String frameTexture = "";
        if (o.has("animated_glow")) {
            JsonElement a = o.get("animated_glow");
            if (a.isJsonObject() && a.getAsJsonObject().has("frames")
                    && a.getAsJsonObject().get("frames").isJsonPrimitive()) {
                JsonObject anim = a.getAsJsonObject();
                frames = clampInt(anim.get("frames"), 0, 64);
                frameTime = anim.has("frametime") ? clampInt(anim.get("frametime"), 1, 200) : 4;
                String ft = string(anim, "frame_texture");
                if (frames > 0 && (ft == null || !ft.contains("{frame}"))) {
                    issues.add(field(id, "animated_glow.frame_texture", "needs {frame}; glow not animated"));
                    frames = 0;
                } else {
                    frameTexture = ft == null ? "" : ft;
                }
            } else {
                issues.add(field(id, "animated_glow", "needs a frames count; glow not animated"));
            }
        }
        String override = "";
        if (kind == LookKind.SKIN && o.has("model_override")) {
            String raw = string(o, "model_override");
            Identifier parsed = raw == null ? null : Identifier.tryParse(raw);
            if (parsed == null || !knownOverrides.contains(parsed)) {
                issues.add(field(id, "model_override", "names no known model variant; standard model used"));
            } else {
                override = parsed.getPath();
            }
        }
        return Optional.of(new Look(id, kind, types, texture, glow, eye, swatch, frames, frameTime, frameTexture,
                override, unlock.get()));
    }

    private static List<String> types(Identifier id, JsonObject o, Set<Identifier> known, List<ValidationIssue> issues) {
        JsonElement raw = o.get("types");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().isEmpty()) {
            issues.add(drop(id, "types", "needs a non-empty list of companion types or [\"*\"]"));
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement e : (JsonArray) raw) {
            String s = e.isJsonPrimitive() ? e.getAsString() : "";
            if (Look.ANY_TYPE.equals(s)) {
                return List.of(Look.ANY_TYPE);
            }
            Identifier type = Identifier.tryParse(s);
            if (type == null || !known.contains(type)) {
                issues.add(field(id, "types", "'" + s + "' is not a companion type; ignored"));
            } else if (!out.contains(type.toString())) {
                out.add(type.toString());
            }
        }
        if (out.isEmpty()) {
            issues.add(drop(id, "types", "lists no known companion type"));
        }
        return out;
    }

    static Optional<UnlockRule> unlock(Identifier id, JsonObject o, List<ValidationIssue> issues) {
        JsonElement raw = o.get("unlock");
        if (raw == null || !raw.isJsonObject()) {
            issues.add(drop(id, "unlock", "is missing"));
            return Optional.empty();
        }
        JsonObject u = raw.getAsJsonObject();
        String ruleName = string(u, "rule");
        Optional<UnlockRule.Kind> rule = ruleName == null ? Optional.empty()
                : UnlockRule.Kind.byId(ruleName.toLowerCase(Locale.ROOT));
        if (rule.isEmpty()) {
            issues.add(drop(id, "unlock.rule", "must be default, bond, advancement, item, shop, season or hook"));
            return Optional.empty();
        }
        return switch (rule.get()) {
            case DEFAULT -> Optional.of(UnlockRule.ALWAYS);
            case BOND -> {
                JsonElement level = u.get("level");
                int n = level != null && level.isJsonPrimitive() && level.getAsJsonPrimitive().isNumber()
                        ? level.getAsInt() : -1;
                if (n < 1 || n > 4) {
                    issues.add(drop(id, "unlock.level", "a bond rule needs a level from 1 to 4"));
                    yield Optional.empty();
                }
                yield Optional.of(new UnlockRule(UnlockRule.Kind.BOND, Integer.toString(n)));
            }
            case ADVANCEMENT -> idRule(id, u, "id", UnlockRule.Kind.ADVANCEMENT, issues);
            case ITEM -> idRule(id, u, "item", UnlockRule.Kind.ITEM, issues);
            case HOOK -> idRule(id, u, "hook", UnlockRule.Kind.HOOK, issues);
            case SHOP -> {
                JsonElement price = u.get("price");
                long p = price != null && price.isJsonPrimitive() && price.getAsJsonPrimitive().isNumber()
                        ? price.getAsLong() : -1L;
                if (p < 0L) {
                    issues.add(drop(id, "unlock.price", "a shop rule needs a price of 0 or more"));
                    yield Optional.empty();
                }
                yield Optional.of(new UnlockRule(UnlockRule.Kind.SHOP, Long.toString(p)));
            }
            case SEASON -> {
                String season = string(u, "season");
                String s = season == null ? "" : season.toLowerCase(Locale.ROOT);
                if (!UnlockRule.SEASONS.contains(s) || "none".equals(s)) {
                    issues.add(drop(id, "unlock.season", "must be spring, summer, autumn, winter or festive"));
                    yield Optional.empty();
                }
                yield Optional.of(new UnlockRule(UnlockRule.Kind.SEASON, s));
            }
        };
    }

    private static Optional<UnlockRule> idRule(Identifier id, JsonObject u, String key, UnlockRule.Kind kind,
            List<ValidationIssue> issues) {
        String raw = string(u, key);
        Identifier target = raw == null ? null : Identifier.tryParse(raw);
        if (target == null) {
            issues.add(drop(id, "unlock." + key, "a " + kind.id() + " rule needs a valid '" + key + "'"));
            return Optional.empty();
        }
        return Optional.of(new UnlockRule(kind, target.toString()));
    }

    private static int colour(Identifier id, JsonObject o, String key, List<ValidationIssue> issues) {
        String raw = string(o, key);
        if (raw == null) {
            return -1;
        }
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() == 6) {
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
                // falls through to the issue
            }
        }
        issues.add(field(id, key, "is not a #RRGGBB colour; default used"));
        return -1;
    }

    private static int clampInt(JsonElement e, int min, int max) {
        try {
            return Math.max(min, Math.min(max, e.getAsInt()));
        } catch (RuntimeException ex) {
            return min;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static ValidationIssue drop(Identifier id, String field, String message) {
        return new ValidationIssue(ValidationIssue.Severity.DROPPED, id, field, message);
    }

    private static ValidationIssue field(Identifier id, String field, String message) {
        return new ValidationIssue(ValidationIssue.Severity.FIELD_DEFAULTED, id, field, message);
    }
}
