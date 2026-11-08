package za.co.neroland.nerocompanion.personality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

/**
 * Every chatter line: its lang key, the trait pool it belongs to, the event that can trigger it and the one or two
 * emote icons the bubble shows. Shared by server (picking) and client (key -> icons), so the client needs no data
 * pack. Lines are lang keys only - never free text, never player data. Pure.
 *
 * <p>Key format: {@code chatter.nerocompanion.<trait>.<n>}, {@code n} from 1.
 */
public final class ChatterLines {

    /** One line. {@code second} is null for a single-icon bubble. */
    public record Line(String key, Trait trait, ChatterEvent event, Emote first, @Nullable Emote second) {
    }

    private static final Map<Trait, List<Line>> BY_TRAIT = new EnumMap<>(Trait.class);
    private static final Map<String, Line> BY_KEY = new HashMap<>();

    static {
        pool(Trait.CURIOUS,
                ChatterEvent.DEPLOY, Emote.QUESTION, Emote.SPARKLE,
                ChatterEvent.LOW_BATTERY, Emote.BOLT, Emote.QUESTION,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.QUESTION,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.EXCLAIM,
                ChatterEvent.JOB_DONE, Emote.TICK, Emote.QUESTION,
                ChatterEvent.PETTED, Emote.HEART, Emote.QUESTION,
                ChatterEvent.PETTED, Emote.HEART, Emote.EYE,
                ChatterEvent.IDLE, Emote.EYE, Emote.DOTS,
                ChatterEvent.IDLE, Emote.DOTS, null,
                ChatterEvent.IDLE, Emote.EYE, Emote.SPARKLE,
                ChatterEvent.STRANGER, Emote.EYE, Emote.QUESTION,
                ChatterEvent.BOND_UP, Emote.HEART, Emote.SPARKLE,
                ChatterEvent.TRICK, Emote.SPIRAL, Emote.SPARKLE,
                ChatterEvent.IDLE, Emote.LEAF, Emote.QUESTION,
                ChatterEvent.THREAT_SPOTTED, Emote.EYE, Emote.DANGER,
                ChatterEvent.TARGET_DOWN, Emote.TICK, Emote.QUESTION);
        pool(Trait.LOYAL,
                ChatterEvent.DEPLOY, Emote.TICK, Emote.SHIELD,
                ChatterEvent.LOW_BATTERY, Emote.BOLT, Emote.HOME,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.TICK,
                ChatterEvent.JOB_DONE, Emote.TICK, null,
                ChatterEvent.JOB_DONE, Emote.TICK, Emote.HEART,
                ChatterEvent.PETTED, Emote.HEART, null,
                ChatterEvent.PETTED, Emote.HEART, Emote.SHIELD,
                ChatterEvent.IDLE, Emote.SHIELD, Emote.EYE,
                ChatterEvent.IDLE, Emote.HOURGLASS, Emote.TICK,
                ChatterEvent.IDLE, Emote.HEART, Emote.HOME,
                ChatterEvent.STRANGER, Emote.SHIELD, Emote.EXCLAIM,
                ChatterEvent.BOND_UP, Emote.HEART, Emote.STAR,
                ChatterEvent.TRICK, Emote.TICK, Emote.STAR,
                ChatterEvent.IDLE, Emote.HOME, Emote.HEART,
                ChatterEvent.THREAT_SPOTTED, Emote.SHIELD, Emote.DANGER,
                ChatterEvent.TARGET_DOWN, Emote.SHIELD, Emote.TICK);
        pool(Trait.GRUMPY,
                ChatterEvent.DEPLOY, Emote.PUFF, null,
                ChatterEvent.LOW_BATTERY, Emote.BOLT, Emote.PUFF,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.PUFF,
                ChatterEvent.JOB_DONE, Emote.TICK, Emote.PUFF,
                ChatterEvent.JOB_DONE, Emote.PUFF, null,
                ChatterEvent.PETTED, Emote.PUFF, Emote.HEART,
                ChatterEvent.PETTED, Emote.SWEAT, Emote.HEART,
                ChatterEvent.IDLE, Emote.PUFF, Emote.DOTS,
                ChatterEvent.IDLE, Emote.SUN, Emote.PUFF,
                ChatterEvent.IDLE, Emote.SWEAT, Emote.PUFF,
                ChatterEvent.STRANGER, Emote.PUFF, Emote.EXCLAIM,
                ChatterEvent.BOND_UP, Emote.PUFF, Emote.HEART,
                ChatterEvent.TRICK, Emote.PUFF, Emote.EXCLAIM,
                ChatterEvent.IDLE, Emote.CROSS, null,
                ChatterEvent.THREAT_SPOTTED, Emote.PUFF, Emote.TARGET,
                ChatterEvent.TARGET_DOWN, Emote.PUFF, Emote.TICK);
        pool(Trait.SLEEPY,
                ChatterEvent.DEPLOY, Emote.ZZ, null,
                ChatterEvent.LOW_BATTERY, Emote.BOLT, Emote.ZZ,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.ZZ,
                ChatterEvent.JOB_DONE, Emote.TICK, Emote.MOON,
                ChatterEvent.JOB_DONE, Emote.ZZ, Emote.TICK,
                ChatterEvent.PETTED, Emote.HEART, Emote.ZZ,
                ChatterEvent.PETTED, Emote.HEART, Emote.MOON,
                ChatterEvent.IDLE, Emote.ZZ, null,
                ChatterEvent.IDLE, Emote.MOON, Emote.QUESTION,
                ChatterEvent.IDLE, Emote.DOTS, Emote.ZZ,
                ChatterEvent.STRANGER, Emote.ZZ, Emote.CROSS,
                ChatterEvent.BOND_UP, Emote.HEART, Emote.MOON,
                ChatterEvent.TRICK, Emote.SPIRAL, Emote.MOON,
                ChatterEvent.IDLE, Emote.HOURGLASS, Emote.DOTS,
                ChatterEvent.THREAT_SPOTTED, Emote.ZZ, Emote.EXCLAIM,
                ChatterEvent.TARGET_DOWN, Emote.TICK, Emote.ZZ);
        pool(Trait.SHOWOFF,
                ChatterEvent.DEPLOY, Emote.STAR, Emote.SUN,
                ChatterEvent.LOW_BATTERY, Emote.BOLT, Emote.STAR,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.STAR,
                ChatterEvent.JOB_DONE, Emote.STAR, Emote.TICK,
                ChatterEvent.JOB_DONE, Emote.SPARKLE, Emote.STAR,
                ChatterEvent.PETTED, Emote.HEART, Emote.SUN,
                ChatterEvent.PETTED, Emote.HEART, Emote.NOTE,
                ChatterEvent.IDLE, Emote.SPARKLE, Emote.EXCLAIM,
                ChatterEvent.IDLE, Emote.NOTE, null,
                ChatterEvent.IDLE, Emote.SUN, Emote.SPARKLE,
                ChatterEvent.STRANGER, Emote.STAR, Emote.NOTE,
                ChatterEvent.BOND_UP, Emote.STAR, Emote.HEART,
                ChatterEvent.TRICK, Emote.STAR, Emote.SUN,
                ChatterEvent.TRICK, Emote.SPARKLE, Emote.NOTE,
                ChatterEvent.THREAT_SPOTTED, Emote.TARGET, Emote.STAR,
                ChatterEvent.TARGET_DOWN, Emote.STAR, Emote.SPARKLE);
        pool(Trait.SHY,
                ChatterEvent.DEPLOY, Emote.BLUSH, null,
                ChatterEvent.LOW_BATTERY, Emote.BOLT, Emote.SWEAT,
                ChatterEvent.ORE_FOUND, Emote.ORE, Emote.DOTS,
                ChatterEvent.JOB_DONE, Emote.TICK, Emote.BLUSH,
                ChatterEvent.JOB_DONE, Emote.DOTS, Emote.BLUSH,
                ChatterEvent.PETTED, Emote.BLUSH, null,
                ChatterEvent.PETTED, Emote.BLUSH, Emote.HEART,
                ChatterEvent.IDLE, Emote.DOTS, null,
                ChatterEvent.IDLE, Emote.SWEAT, Emote.DOTS,
                ChatterEvent.IDLE, Emote.EYE, Emote.BLUSH,
                ChatterEvent.STRANGER, Emote.SWEAT, Emote.BLUSH,
                ChatterEvent.BOND_UP, Emote.HEART, Emote.BLUSH,
                ChatterEvent.TRICK, Emote.BLUSH, Emote.EYE,
                ChatterEvent.IDLE, Emote.HEART, null,
                ChatterEvent.THREAT_SPOTTED, Emote.SWEAT, Emote.DANGER,
                ChatterEvent.TARGET_DOWN, Emote.TICK, Emote.BLUSH);
    }

    /** Lines shared by every trait (functional alerts), by event. */
    private static final Map<ChatterEvent, Line> SHARED = new EnumMap<>(ChatterEvent.class);

    /** The Scout's Danger Alert line (key kept from the scout package). */
    public static final String DANGER_KEY = "chatter.nerocompanion.scout.danger";

    static {
        Line danger = new Line(DANGER_KEY, Trait.CURIOUS, ChatterEvent.DANGER, Emote.DANGER, null);
        SHARED.put(ChatterEvent.DANGER, danger);
        BY_KEY.put(DANGER_KEY, danger);
    }

    private ChatterLines() {
    }

    private static void pool(Trait trait, Object... triples) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 3) {
            String key = key(trait, lines.size() + 1);
            Line line = new Line(key, trait, (ChatterEvent) triples[i], (Emote) triples[i + 1],
                    (Emote) triples[i + 2]);
            lines.add(line);
            BY_KEY.put(key, line);
        }
        BY_TRAIT.put(trait, Collections.unmodifiableList(lines));
    }

    /** {@code chatter.nerocompanion.<trait>.<n>}. */
    public static String key(Trait trait, int n) {
        return "chatter.nerocompanion." + trait.id() + "." + n;
    }

    /** Every line of a trait, in key order. */
    public static List<Line> pool(Trait trait) {
        return BY_TRAIT.get(trait);
    }

    /** The line for a key, if it is one of ours (the client ignores unknown keys from a newer server). */
    public static Optional<Line> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** The shared line for {@code event}, if the event has one (then no trait pool answers it). */
    public static Optional<Line> shared(ChatterEvent event) {
        return Optional.ofNullable(SHARED.get(event));
    }

    /** All keys: every trait pool, then the shared lines. */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        for (Trait trait : Trait.values()) {
            for (Line line : pool(trait)) {
                keys.add(line.key());
            }
        }
        for (Line line : SHARED.values()) {
            keys.add(line.key());
        }
        return keys;
    }

    /**
     * Picks a line for {@code event}: the shared line if the event has one, else from {@code trait}'s pool with
     * {@code roll} (any int, e.g. a random), falling back to the idle lines if the pool has none for the event.
     */
    public static Line pick(Trait trait, ChatterEvent event, int roll) {
        Line shared = SHARED.get(event);
        if (shared != null) {
            return shared;
        }
        List<Line> candidates = new ArrayList<>();
        for (Line line : pool(trait)) {
            if (line.event() == event) {
                candidates.add(line);
            }
        }
        if (candidates.isEmpty()) {
            for (Line line : pool(trait)) {
                if (line.event() == ChatterEvent.IDLE) {
                    candidates.add(line);
                }
            }
        }
        return candidates.get(Math.floorMod(roll, candidates.size()));
    }
}
