package com.eviesaurr.sablemobinteractions.sublevel;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class NearestBlockCache {
    private static final int CACHE_LIFETIME_TICKS = 10;
    private static final double POSITION_BUCKET_SIZE = 4.0; // group nearby queries together

    private record CacheKey(long chunkX, long chunkY, long chunkZ, double radius) {}
    private record CacheEntry(Optional<SubLevelInteraction.Found> result, long tick) {}

    private static final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public static Optional<SubLevelInteraction.Found> getOrCompute(
            Vec3 worldPos, double radius, long currentTick,
            java.util.function.Supplier<Optional<SubLevelInteraction.Found>> compute) {

        CacheKey key = new CacheKey(
                Math.round(worldPos.x / POSITION_BUCKET_SIZE),
                Math.round(worldPos.y / POSITION_BUCKET_SIZE),
                Math.round(worldPos.z / POSITION_BUCKET_SIZE),
                radius
        );

        CacheEntry entry = cache.get(key);
        if (entry != null && currentTick - entry.tick() < CACHE_LIFETIME_TICKS) {
            return entry.result();
        }

        var result = compute.get();
        cache.put(key, new CacheEntry(result, currentTick));
        return result;
    }
}