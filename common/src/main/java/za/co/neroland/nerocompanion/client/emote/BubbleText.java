package za.co.neroland.nerocompanion.client.emote;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jetbrains.annotations.Nullable;

/**
 * The text fallback for bubbles while {@code textures/gui/emotes.png} does not exist: the line's lang key is
 * translated on the viewing client and wrapped to {@link #MAX_WIDTH_PX} font pixels (at most {@link #MAX_LINES}
 * lines), drawn at {@link #SCALE} blocks per pixel. The wire still carries only the key and the trait. Client only.
 */
public final class BubbleText {

    public static final int MAX_WIDTH_PX = 100;
    public static final int MAX_LINES = 3;
    /** Blocks per font pixel (vanilla nameplates use 0.025). */
    public static final float SCALE = 0.016F;

    /** Laid-out text of one bubble. */
    public record Layout(List<FormattedCharSequence> lines, int widthPx, int heightPx) {
    }

    /** Per-bubble lazy cache. */
    public static final class Holder {
        @Nullable
        private Layout layout;

        Layout get(String key) {
            Layout cached = this.layout;
            if (cached == null) {
                cached = layout(key);
                this.layout = cached;
            }
            return cached;
        }
    }

    private BubbleText() {
    }

    static Layout layout(String key) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.translatable(key), MAX_WIDTH_PX);
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        return new Layout(List.copyOf(lines), width, lines.size() * font.lineHeight - 1);
    }
}
