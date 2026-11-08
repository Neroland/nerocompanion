package za.co.neroland.nerocompanion.scout;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Scout marks as ore: {@code #c:ores} (every loader's common ore tag, which Core materials and sibling mods
 * fill), Core's own {@code #neroland:ores} if a pack or a later Core defines it, and the vanilla per-ore tags as a
 * fallback for a loader whose {@code #c:ores} is empty. A tag nobody defines is simply empty - the same safety as
 * {@code "required": false} in a tag file - so no tag file is needed and none can fail to load.
 *
 * <p>{@link Cache} answers per {@link BlockState} identity after the first lookup, so the scan loop does one map
 * lookup per block instead of up to ten tag checks. Each scan owner keeps its own cache and drops it periodically, so
 * a {@code /reload} that changes the tags is picked up.
 */
public final class ScoutOres {

    private static final List<TagKey<Block>> TAGS = List.of(
            tag("c", "ores"),
            tag("neroland", "ores"),
            tag("minecraft", "coal_ores"),
            tag("minecraft", "iron_ores"),
            tag("minecraft", "copper_ores"),
            tag("minecraft", "gold_ores"),
            tag("minecraft", "redstone_ores"),
            tag("minecraft", "lapis_ores"),
            tag("minecraft", "diamond_ores"),
            tag("minecraft", "emerald_ores"));

    private ScoutOres() {
    }

    private static TagKey<Block> tag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(namespace, path));
    }

    public static boolean isOre(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        for (TagKey<Block> tag : TAGS) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** A per-scanner ore-ness cache keyed by block state identity. */
    public static final class Cache {
        private final Map<BlockState, Boolean> known = new IdentityHashMap<>();

        public boolean isOre(BlockState state) {
            Boolean hit = this.known.get(state);
            if (hit == null) {
                hit = ScoutOres.isOre(state);
                this.known.put(state, hit);
            }
            return hit;
        }

        public void clear() {
            this.known.clear();
        }
    }
}
