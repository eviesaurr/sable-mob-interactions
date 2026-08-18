package com.eviesaurr.sablemobinteractions.sublevel;

import com.eviesaurr.sablemobinteractions.config.Config;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakTracker {

    public static final int HITS_TO_BREAK = 16;

    private static final Map<UUID, Map<BlockPos, Integer>> hitCounts = new ConcurrentHashMap<>();

    public static boolean registerHit(SubLevel subLevel, BlockPos localPos, Entity source) {
        var subLevelHits = hitCounts.computeIfAbsent(subLevel.getUniqueId(), id -> new ConcurrentHashMap<>());
        int hits = subLevelHits.merge(localPos, 1, Integer::sum);

        BlockPos rawPos = SubLevelInteraction.toRawBlockPos(subLevel, localPos);

        int hitsNeeded = HITS_TO_BREAK;
        if (com.eviesaurr.sablemobinteractions.compat.BigCannonsCompat.isLoaded()
                && Config.SCALE_HITS_BY_HARDNESS.get()) {
            var state = source.level().getBlockState(rawPos);
            double multiplier = com.eviesaurr.sablemobinteractions.compat.BigCannonsCompat.getHitMultiplier(
                    (ServerLevel) source.level(), state, rawPos);
            hitsNeeded = Math.max(1, (int) (HITS_TO_BREAK * multiplier));
        }

        int stage = Math.min(9, (hits * 10) / hitsNeeded);
        source.level().destroyBlockProgress(source.getId(), rawPos, stage);

        if (hits >= hitsNeeded) {
            source.level().destroyBlock(rawPos, true, source);
            subLevelHits.remove(localPos);
            return true;
        }
        return false;
    }

    public static void clearSubLevel(UUID subLevelId) {
        BlockBreakTracker.hitCounts.remove(subLevelId);
    }
}