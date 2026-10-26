package com.eviesaurr.sablemobinteractions.sublevel;

import com.eviesaurr.sablemobinteractions.config.Config;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockBreakTracker {

    public static final int HITS_TO_BREAK = 16;
    private static final int MIN_PARTICLES = 9;
    private static final int PARTICLES_PER_STAGE = 2;

    private static final Map<UUID, Map<BlockPos, Integer>> hitCounts = new ConcurrentHashMap<>();

    public record HitResult(int hits, boolean broke) {}

    public static HitResult registerHit(SubLevel subLevel, BlockPos localPos, Entity source) {
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

        Vec3 renderWorldPos = SubLevelInteraction.localToWorld(subLevel, localPos);

        if (source.level() instanceof ServerLevel serverLevel) {
            BlockState visualState = serverLevel.getBlockState(rawPos);
            if (!visualState.isAir()) {
                int particleCount = MIN_PARTICLES + (stage * PARTICLES_PER_STAGE);
                serverLevel.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK, visualState),
                        renderWorldPos.x, renderWorldPos.y, renderWorldPos.z,
                        particleCount, 0.3, 0.3, 0.3, 0.0
                );
            }
        }

        boolean broke = hits >= hitsNeeded;
        if (broke) {
            source.level().destroyBlock(rawPos, true, source);
            subLevelHits.remove(localPos);
        }
        return new HitResult(hits, broke);
    }

    public static void clearSubLevel(UUID subLevelId) {
        BlockBreakTracker.hitCounts.remove(subLevelId);
    }
}