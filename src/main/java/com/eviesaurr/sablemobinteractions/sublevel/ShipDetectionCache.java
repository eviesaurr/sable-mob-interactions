package com.eviesaurr.sablemobinteractions.sublevel;

import com.eviesaurr.sablemobinteractions.ModTags;
import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        int chunksScanned = 0;
        for (var holder : subLevel.getPlot().getLoadedChunks()) {
            var chunk = holder.getChunk();
            if (chunk == null) continue;
            chunksScanned++;
            int minY = chunk.getMinBuildHeight();
            int maxY = chunk.getMaxBuildHeight();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                        var state = chunk.getBlockState(pos);
                        if (state.isAir()) continue;
                        System.out.println("[SHIP-DEBUG] non-air: " + state.getBlock() + " at " + pos + " (tagged: " + state.is(ModTags.SHIP_ESSENTIAL_BLOCKS) + ")");
                        if (state.is(ModTags.SHIP_ESSENTIAL_BLOCKS)) return true;
                    }
                }
            }
        }
        System.out.println("[SHIP-DEBUG] No essential block found. Chunks scanned: " + chunksScanned);
        return false;
    }

    public static void clearSubLevel(UUID subLevelId) {
        cache.remove(subLevelId);
    }
}
