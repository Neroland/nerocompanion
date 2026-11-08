package za.co.neroland.nerocompanion.scout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The chunk sections an ore scan visits (pure): every 16-block section that touches the sphere of {@code radius}
 * around a block, nearest first, consumed in slices of {@link ScanCadence#SECTIONS_PER_STEP}.
 */
public final class SectionPlan {

    /** Section coordinates {@code (sx, sy, sz)} and the squared distance from the centre to the section's box. */
    public record Section(int sx, int sy, int sz, double distanceSq) {
    }

    private final List<Section> sections;
    private int next;

    public SectionPlan(int x, int y, int z, int radius) {
        double r2 = (double) radius * radius;
        List<Section> list = new ArrayList<>();
        for (int sx = (x - radius) >> 4; sx <= (x + radius) >> 4; sx++) {
            for (int sy = (y - radius) >> 4; sy <= (y + radius) >> 4; sy++) {
                for (int sz = (z - radius) >> 4; sz <= (z + radius) >> 4; sz++) {
                    double dx = axis(x, sx << 4);
                    double dy = axis(y, sy << 4);
                    double dz = axis(z, sz << 4);
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= r2) {
                        list.add(new Section(sx, sy, sz, d2));
                    }
                }
            }
        }
        list.sort(Comparator.comparingDouble(Section::distanceSq));
        this.sections = List.copyOf(list);
    }

    private static double axis(int c, int base) {
        if (c < base) {
            return base - c;
        }
        if (c > base + 15) {
            return c - (base + 15);
        }
        return 0.0D;
    }

    public List<Section> all() {
        return this.sections;
    }

    /** The next slice of at most {@code max} sections (empty when done). */
    public List<Section> nextSlice(int max) {
        int end = Math.min(this.sections.size(), this.next + Math.max(0, max));
        List<Section> slice = this.sections.subList(this.next, end);
        this.next = end;
        return slice;
    }

    public boolean done() {
        return this.next >= this.sections.size();
    }
}
