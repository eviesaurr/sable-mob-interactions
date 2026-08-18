package com.eviesaurr.sablemobinteractions.sublevel;

import com.eviesaurr.sablemobinteractions.ModTags;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches whether a sub-level contains any ship-essential block (per the
 * ship_essential_blocks tag), so mobs only target genuine vehicles, not
 * arbitrary player-built sub-levels. Recomputed periodically rather than
 * every poll, since a sub-level's composition rarely changes tick-to-tick.
 */
public class ShipDetectionCache {

    private static final int LIFETIME_TICKS = 200; // ~10 seconds

    private record Entry(boolean isShip, long tick) {}

    private static final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public static boolean isShip(ServerLevel level, SubLevel subLevel, long currentTick) {
        Entry entry = cache.get(subLevel.getUniqueId());
        if (entry != null && currentTick - entry.tick() < LIFETIME_TICKS) {
            return entry.isShip();
        }

        boolean result = scan(subLevel);
        cache.put(subLevel.getUniqueId(), new Entry(result, currentTick));
        return result;
    }

    private static boolean scan(SubLevel subLevel) {
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            var chunk = holder.getChunk();
            if (chunk == null) continue;

            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                if (chunk.getBlockState(pos).is(ModTags.SHIP_ESSENTIAL_BLOCKS)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Call when a sub-level is removed, to avoid leaking stale cache entries. */
    public static void clearSubLevel(UUID subLevelId) {
        cache.remove(subLevelId);
    }
}