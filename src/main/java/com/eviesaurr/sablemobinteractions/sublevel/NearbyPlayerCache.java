package com.eviesaurr.sablemobinteractions.sublevel;

import com.eviesaurr.sablemobinteractions.config.Config;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NearbyPlayerCache {
    private static final int LIFETIME_TICKS = 20; // 1 second - players move, keep this short
    private record Entry(boolean present, long tick) {}
    private static final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public static boolean hasNearbyPlayer(ServerLevel level, SubLevel subLevel, long currentTick) {
        Entry e = cache.get(subLevel.getUniqueId());
        if (e != null && currentTick - e.tick() < LIFETIME_TICKS) return e.present();

        double range = Config.PLAYER_PRESENCE_RANGE.get();
        boolean result = range <= 0 || checkReal(level, subLevel, range);
        cache.put(subLevel.getUniqueId(), new Entry(result, currentTick));
        return result;
    }

    private static boolean checkReal(ServerLevel level, SubLevel subLevel, double range) {
        var bounds = subLevel.boundingBox();
        var expandedBox = new net.minecraft.world.phys.AABB(
                bounds.minX() - range, bounds.minY() - range, bounds.minZ() - range,
                bounds.maxX() + range, bounds.maxY() + range, bounds.maxZ() + range
        );
        return !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, expandedBox).isEmpty();
    }
}