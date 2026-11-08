package za.co.neroland.nerocompanion.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Word wrapping for the screens (pure: the text width is passed in, so it is tested on the plain JVM). Lines break
 * only between words. When the text does not fit in the lines given, the last line ends in an ellipsis, so text is
 * never cut silently; a single word wider than its line is shortened with an ellipsis rather than split across lines.
 */
public final class TextWrap {

    /** U+2026 horizontal ellipsis. */
    public static final String ELLIPSIS = "…";

    private TextWrap() {
    }

    /**
     * {@code text} wrapped into at most {@code widths.length} lines; line {@code i} is at most {@code widths[i]} wide.
     */
    public static List<String> wrap(String text, ToIntFunction<String> width, int... widths) {
        List<String> out = new ArrayList<>();
        String stripped = text.strip();
        if (widths.length == 0 || stripped.isEmpty()) {
            return out;
        }
        String[] words = stripped.split("\\s+");
        StringBuilder line = new StringBuilder();
        int li = 0;
        int i = 0;
        while (i < words.length) {
            int max = widths[li];
            boolean last = li == widths.length - 1;
            String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
            if (width.applyAsInt(candidate) <= max) {
                line.setLength(0);
                line.append(candidate);
                i++;
                continue;
            }
            if (line.length() == 0) {
                // One word wider than a whole line: shorten it.
                out.add(cut(words[i], width, max));
                i++;
                if (last) {
                    return out;
                }
                li++;
                continue;
            }
            if (last) {
                out.add(withEllipsis(line.toString(), width, max));
                return out;
            }
            out.add(line.toString());
            line.setLength(0);
            li++;
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** {@code text} on one line of at most {@code max}: whole words, then an ellipsis when something was left out. */
    public static String fitWords(String text, ToIntFunction<String> width, int max) {
        List<String> lines = wrap(text, width, max);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    /** {@code line} (which had more words after it) with an ellipsis, dropping trailing words until it fits. */
    static String withEllipsis(String line, ToIntFunction<String> width, int max) {
        String current = line;
        while (width.applyAsInt(current + ELLIPSIS) > max) {
            int space = current.lastIndexOf(' ');
            if (space <= 0) {
                return cut(current, width, max);
            }
            current = current.substring(0, space);
        }
        return current + ELLIPSIS;
    }

    /** One word cut to fit {@code max} with an ellipsis (unchanged when it already fits). */
    static String cut(String word, ToIntFunction<String> width, int max) {
        if (width.applyAsInt(word) <= max) {
            return word;
        }
        String current = word;
        while (!current.isEmpty() && width.applyAsInt(current + ELLIPSIS) > max) {
            current = current.substring(0, current.length() - 1);
        }
        return current + ELLIPSIS;
    }
}
