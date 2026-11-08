package za.co.neroland.nerocompanion.client.emote;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.client.ClientHooks;
import za.co.neroland.nerocompanion.client.CompanionRenderer;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.personality.ChatterLines;
import za.co.neroland.nerocompanion.personality.Emote;
import za.co.neroland.nerocompanion.personality.Trait;

/**
 * Client emote bubbles: a {@code Chatter} payload (entity id, lang key, trait) becomes one bubble above that
 * companion for {@link #LIFE} ticks - 4-tick pop-in, 30 hold, 6 fade ({@code 010} section 5.1). At most one bubble
 * per companion; a new line replaces it. Unknown keys (a newer server) are ignored. Until the emote sheet ships
 * the bubble shows the line's text (translated here from the key) instead of icons. Holds entity ids, keys and
 * icon ids only - nothing personal. Client thread only.
 */
public final class EmoteBubbles {

    public static final int POP = 4;
    public static final int FADE = 6;
    public static final int LIFE = 40;

    /**
     * One live bubble. {@code second} is null for a one-icon bubble; {@code start} is the client tick. {@code key}
     * is the line's lang key, translated on this client for the text fallback ({@link BubbleText}); the text is
     * laid out lazily on first draw.
     */
    public record Bubble(String key, Emote first, @Nullable Emote second, Trait trait, long start,
            BubbleText.Holder text) {
    }

    private static final Map<Integer, Bubble> ACTIVE = new HashMap<>();
    private static long clientTicks;

    private EmoteBubbles() {
    }

    /** Client init (integration agent: {@code EmoteBubbles.register();} in {@code ClientCompanionContent}). */
    public static void register() {
        ClientHooks.onChatter(EmoteBubbles::receive);
        ClientHooks.onTick(EmoteBubbles::tick);
        CompanionRenderer.registerLayer(renderer -> renderer.addCompanionLayer(new EmoteBubbleLayer(renderer)));
    }

    static void receive(Payloads.Chatter payload) {
        ChatterLines.byKey(payload.key()).ifPresent(line -> ACTIVE.put(payload.entityId(),
                new Bubble(line.key(), line.first(), line.second(), Trait.byIndex(payload.trait()), clientTicks,
                        new BubbleText.Holder())));
    }

    static void tick() {
        clientTicks++;
        if (!ACTIVE.isEmpty()) {
            ACTIVE.values().removeIf(bubble -> clientTicks - bubble.start() >= LIFE);
        }
    }

    /** The live bubble over {@code entityId}, if any. */
    @Nullable
    public static Bubble active(int entityId) {
        return ACTIVE.isEmpty() ? null : ACTIVE.get(entityId);
    }

    /** Age of {@code bubble} in ticks, with the sub-tick {@code partial} (0..1). */
    public static float age(Bubble bubble, float partial) {
        return (clientTicks - bubble.start()) + partial;
    }

    /** Scale at {@code age}: easeOutBack 0 to 1 over the pop-in, then 1. */
    public static float scale(float age) {
        if (age >= POP) {
            return 1.0F;
        }
        float t = Math.max(0.0F, age / POP) - 1.0F;
        float c1 = 1.70158F;
        return 1.0F + (c1 + 1.0F) * t * t * t + c1 * t * t;
    }

    /** Alpha at {@code age}: 1, fading to 0 over the last {@link #FADE} ticks. */
    public static float alpha(float age) {
        float fadeStart = LIFE - FADE;
        if (age <= fadeStart) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (age - fadeStart) / FADE);
    }
}
