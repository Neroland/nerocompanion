package za.co.neroland.nerocompanion.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocompanion.entity.CompanionMode;
import za.co.neroland.nerocompanion.entity.GalleryPose;
import za.co.neroland.nerocompanion.look.Look;

/**
 * The {@code /nerocompanion gallery} stage, worked out from what there is to show before a block is placed. Pure: the
 * server command builds from the plan and the client capture harness frames the same plan, so the two never drift.
 *
 * <p>Stage coordinates are relative to the caller's feet: x {@code 0..length-1} east, z {@code 0..depth-1} south,
 * floor at y-1. Companion sections each start a new row and wrap after {@link #MAX_COLUMNS}; rows sit
 * {@link #ROW_STEP} apart from z {@link #FIRST_ROW_Z}, columns {@link #STEP} apart from x {@link #FIRST_X}. The
 * chargers stand at x {@link #CHARGER_X} on the first three rows. The item wall is the stage's south edge: item frames
 * on its north face at z {@code wallZ}, {@link #WALL_ROWS} high (y 1 up), one group after another with a label sign
 * under each group's first column (y 0) and one empty column between groups.
 */
public final class GalleryLayout {

    public static final int FIRST_X = 4;
    public static final int STEP = 3;
    public static final int FIRST_ROW_Z = 3;
    public static final int ROW_STEP = 5;
    public static final int MAX_COLUMNS = 12;
    public static final int CHARGER_X = 1;
    public static final int CHARGERS = 3;
    /** Air above the floor the build clears (and the wall's height). */
    public static final int HEIGHT = 6;
    public static final int WALL_ROWS = 4;
    /** Blocks between the last companion row and the item frames. */
    public static final int WALL_GAP_Z = 5;

    /** The companion sections, in row order. */
    public static final String STANDARD = "standard";
    public static final String SKINS = "skins";
    public static final String FAMILY = "family";
    public static final String HATS = "hats";
    public static final String RESTING = "resting";
    public static final String WORKING = "working";
    public static final String STATUS = "status";

    /** The item-wall groups, in wall order. */
    public static final String WALL_ITEMS = "items";
    public static final String WALL_FILLED = "filled";
    public static final String WALL_BLUEPRINTS = "blueprints";
    public static final String WALL_EGGS = "eggs";

    /** Energy levels shown: one per eye-colour band (healthy, amber, red, dim red, off). */
    public static final int[] ENERGY_LEVELS = {100, 50, 20, 10, 0};
    /** Bond levels shown on nameplates. */
    public static final int BOND_LEVELS = 5;
    /** Scrap Egg looks: plain, cracked, glowing crack. */
    public static final int EGG_STAGES = 3;

    /**
     * The types with a work animation and the mode that plays it (the client model's work loops); the Scrapling
     * has none.
     */
    public static final Map<String, CompanionMode> WORK_POSES = workPoses();

    /** The type that shows the single-companion poses (nap is the Scrapling's own). */
    private static final String NAPPER = "scrapling";

    /** One display companion. {@code name} empty = unnamed; {@code onPad} = stands on a Charging Pad. */
    public record Display(String section, Identifier type, Optional<Identifier> skin, Optional<Identifier> hat,
            GalleryPose pose, String name, boolean onPad) {
    }

    public enum WallKind { ITEM, FILLED_CAPSULE, BLUEPRINT, EGG_STAGE }

    /** One item on the wall: a registry item, or a Capsule / Scrap Egg variant ({@code type} or {@code stage}). */
    public record WallItem(String group, WallKind kind, @Nullable Identifier item, @Nullable Identifier type,
            int stage) {
    }

    public record Slot(Display display, int dx, int dz) {
    }

    public record WallSlot(WallItem item, int dx, int dy) {
    }

    /** A wall group's label sign at {@code (dx, 0, wallZ)} and the columns it spans. */
    public record Label(String group, int dx, int columns, int count) {
    }

    /** One stage row: its section, z, and how many companions stand in it. */
    public record Row(String section, int dz, int count) {
    }

    /** The whole stage. {@code wallZ} is the item frames' z; the wall blocks stand at {@code wallZ + 1}. */
    public record Plan(int length, int depth, int height, int wallZ, List<Row> rows, List<Slot> companions,
            List<WallSlot> wall, List<Label> labels, List<int[]> chargers) {

        public int companionCount() {
            return this.companions.size();
        }

        public int count(String section) {
            int n = 0;
            for (Slot slot : this.companions) {
                if (slot.display().section().equals(section)) {
                    n++;
                }
            }
            return n;
        }

        public int wallCount(String group) {
            int n = 0;
            for (WallSlot slot : this.wall) {
                if (slot.item().group().equals(group)) {
                    n++;
                }
            }
            return n;
        }
    }

    private GalleryLayout() {
    }

    private static Map<String, CompanionMode> workPoses() {
        Map<String, CompanionMode> map = new LinkedHashMap<>();
        map.put("personal_drone", CompanionMode.CARRY);
        map.put("scout_drone", CompanionMode.SCOUT);
        map.put("mining_bot", CompanionMode.MINE);
        map.put("carry_bot", CompanionMode.CARRY);
        map.put("repair_bot", CompanionMode.REPAIR);
        map.put("combat_bot", CompanionMode.GUARD);
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * Everything to show: every type standard; every type skin on each type it allows (seasonal, shop and other
     * locked ones included); each family skin on every type; every hat on every type allowed to wear it; then the
     * poses. Skins and hats are sorted by id so server and client agree.
     */
    public static List<Display> displays(List<Identifier> types, Collection<Look> skins, Collection<Look> hats) {
        List<Look> sortedSkins = sorted(skins);
        List<Look> sortedHats = sorted(hats);
        List<Display> out = new ArrayList<>();
        for (Identifier type : types) {
            out.add(plain(STANDARD, type, Optional.empty(), Optional.empty(), GalleryPose.still()));
        }
        for (Identifier type : types) {
            for (Look skin : sortedSkins) {
                if (!skin.family() && !skin.isDefault() && skin.allows(type)) {
                    out.add(plain(SKINS, type, Optional.of(skin.id()), Optional.empty(), GalleryPose.still()));
                }
            }
        }
        for (Look skin : sortedSkins) {
            if (skin.family()) {
                for (Identifier type : types) {
                    out.add(plain(FAMILY, type, Optional.of(skin.id()), Optional.empty(), GalleryPose.still()));
                }
            }
        }
        for (Look hat : sortedHats) {
            for (Identifier type : types) {
                if (hat.allows(type)) {
                    out.add(plain(HATS, type, Optional.empty(), Optional.of(hat.id()), GalleryPose.still()));
                }
            }
        }
        // Poses: asleep (every type), napping (the Scrapling).
        for (Identifier type : types) {
            out.add(plain(RESTING, type, Optional.empty(), Optional.empty(), GalleryPose.asleep()));
        }
        types.stream().filter(type -> type.getPath().equals(NAPPER)).findFirst().ifPresent(type ->
                out.add(plain(RESTING, type, Optional.empty(), Optional.empty(), GalleryPose.napping())));
        // Working (every type with a work animation), then charging on a pad.
        for (Identifier type : types) {
            CompanionMode mode = WORK_POSES.get(type.getPath());
            if (mode != null) {
                out.add(plain(WORKING, type, Optional.empty(), Optional.empty(), GalleryPose.working(mode)));
            }
        }
        if (!types.isEmpty()) {
            out.add(new Display(WORKING, types.get(0), Optional.empty(), Optional.empty(), GalleryPose.charging(), "",
                    true));
        }
        // Status: the energy bands and the bond levels, named so the nameplate reads as a label.
        if (!types.isEmpty()) {
            Identifier first = types.get(0);
            for (int pct : ENERGY_LEVELS) {
                out.add(new Display(STATUS, first, Optional.empty(), Optional.empty(), withName(GalleryPose.energy(pct)),
                        pct + "%", false));
            }
            for (int level = 0; level < BOND_LEVELS; level++) {
                out.add(new Display(STATUS, first, Optional.empty(), Optional.empty(), GalleryPose.bond(level),
                        "Bond " + level, false));
            }
        }
        return out;
    }

    /** The wall: every item in the namespace, then a filled and a blueprint Capsule per type, then each egg stage. */
    public static List<WallItem> wallItems(List<Identifier> items, List<Identifier> types) {
        List<WallItem> out = new ArrayList<>();
        for (Identifier item : items) {
            out.add(new WallItem(WALL_ITEMS, WallKind.ITEM, item, null, 0));
        }
        for (Identifier type : types) {
            out.add(new WallItem(WALL_FILLED, WallKind.FILLED_CAPSULE, null, type, 0));
        }
        for (Identifier type : types) {
            out.add(new WallItem(WALL_BLUEPRINTS, WallKind.BLUEPRINT, null, type, 0));
        }
        for (int stage = 0; stage < EGG_STAGES; stage++) {
            out.add(new WallItem(WALL_EGGS, WallKind.EGG_STAGE, null, null, stage));
        }
        return out;
    }

    /** Places every display and wall item; the stage is sized to hold them all. */
    public static Plan plan(List<Display> displays, List<WallItem> wallItems) {
        // Companion rows: each section starts a row and wraps at MAX_COLUMNS.
        Map<String, List<Display>> sections = new LinkedHashMap<>();
        for (Display display : displays) {
            sections.computeIfAbsent(display.section(), key -> new ArrayList<>()).add(display);
        }
        List<Row> rows = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        int widest = 1;
        int row = 0;
        for (Map.Entry<String, List<Display>> section : sections.entrySet()) {
            List<Display> list = section.getValue();
            for (int start = 0; start < list.size(); start += MAX_COLUMNS) {
                int dz = FIRST_ROW_Z + row * ROW_STEP;
                int count = Math.min(MAX_COLUMNS, list.size() - start);
                for (int c = 0; c < count; c++) {
                    slots.add(new Slot(list.get(start + c), FIRST_X + c * STEP, dz));
                }
                widest = Math.max(widest, count);
                rows.add(new Row(section.getKey(), dz, count));
                row++;
            }
        }
        int lastRowZ = FIRST_ROW_Z + Math.max(row - 1, CHARGERS - 1) * ROW_STEP;
        int wallZ = lastRowZ + WALL_GAP_Z;
        int depth = wallZ + 2;

        // Wall groups, column-major from the top row down.
        Map<String, List<WallItem>> groups = new LinkedHashMap<>();
        for (WallItem item : wallItems) {
            groups.computeIfAbsent(item.group(), key -> new ArrayList<>()).add(item);
        }
        List<WallSlot> wall = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        int x = 1;
        for (Map.Entry<String, List<WallItem>> group : groups.entrySet()) {
            List<WallItem> list = group.getValue();
            int columns = (list.size() + WALL_ROWS - 1) / WALL_ROWS;
            for (int i = 0; i < list.size(); i++) {
                wall.add(new WallSlot(list.get(i), x + i / WALL_ROWS, WALL_ROWS - i % WALL_ROWS));
            }
            labels.add(new Label(group.getKey(), x, columns, list.size()));
            x += columns + 1;
        }
        int lastWallX = Math.max(1, x - 2); // x stepped past the last group and its gap

        int lastCompanionX = FIRST_X + (widest - 1) * STEP;
        int length = Math.max(lastCompanionX + 3, lastWallX + 2);
        List<int[]> chargers = new ArrayList<>();
        for (int i = 0; i < CHARGERS; i++) {
            chargers.add(new int[] {CHARGER_X, FIRST_ROW_Z + i * ROW_STEP});
        }
        return new Plan(length, depth, HEIGHT, wallZ, List.copyOf(rows), List.copyOf(slots), List.copyOf(wall),
                List.copyOf(labels), List.copyOf(chargers));
    }

    private static Display plain(String section, Identifier type, Optional<Identifier> skin, Optional<Identifier> hat,
            GalleryPose pose) {
        return new Display(section, type, skin, hat, pose, "", false);
    }

    private static GalleryPose withName(GalleryPose pose) {
        return new GalleryPose(pose.mode(), pose.flags(), pose.energyPercent(), pose.bondLevel(), true);
    }

    private static List<Look> sorted(Collection<Look> looks) {
        List<Look> out = new ArrayList<>(looks);
        out.sort(Comparator.comparing(look -> look.id().toString()));
        return out;
    }
}
