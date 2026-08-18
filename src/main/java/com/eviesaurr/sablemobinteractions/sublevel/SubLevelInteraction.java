package com.eviesaurr.sablemobinteractions.sublevel;

import com.eviesaurr.sablemobinteractions.config.Config;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class SubLevelInteraction {

    public record Found(SubLevel subLevel, BlockPos localPos) {}

    public static Optional<Found> findAt(ServerLevel level, Vec3 worldPos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        var point = new BoundingBox3d(worldPos.x, worldPos.y, worldPos.z, worldPos.x, worldPos.y, worldPos.z);
        for (SubLevel subLevel : container.queryIntersecting(point)) {
            BlockPos localPos = worldToLocal(subLevel, worldPos);
            return Optional.of(new Found(subLevel, localPos));
        }
        return Optional.empty();
    }

    public static Optional<Found> findAtRaw(ServerLevel level, BlockPos rawPos) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        ChunkPos rawChunkPos = new ChunkPos(rawPos);
        if (!container.inBounds(rawChunkPos)) return Optional.empty();

        var plot = container.getPlot(rawChunkPos);
        if (plot == null) return Optional.empty();

        ChunkPos localChunkPos = plot.toLocal(rawChunkPos);
        BlockPos localPos = new BlockPos(
                (localChunkPos.x << 4) | (rawPos.getX() & 15),
                rawPos.getY(),
                (localChunkPos.z << 4) | (rawPos.getZ() & 15)
        );

        return Optional.of(new Found(plot.getSubLevel(), localPos));
    }

    public static BlockPos toRawBlockPos(SubLevel subLevel, BlockPos localPos) {
        ChunkPos localChunkPos = new ChunkPos(localPos);
        LevelChunk chunk = subLevel.getPlot().getChunk(localChunkPos);
        if (chunk == null) return localPos;

        ChunkPos realChunkPos = chunk.getPos();
        return new BlockPos(
                realChunkPos.getMinBlockX() | (localPos.getX() & 15),
                localPos.getY(),
                realChunkPos.getMinBlockZ() | (localPos.getZ() & 15)
        );
    }

    public static Vec3 localToWorld(SubLevel subLevel, BlockPos localPos) {
        Vec3 local = new Vec3(localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5);
        return subLevel.logicalPose().transformPosition(local);
    }

    public static BlockPos worldToLocal(SubLevel subLevel, Vec3 worldPos) {
        Vec3 local = subLevel.logicalPose().transformPositionInverse(worldPos);
        return BlockPos.containing(local.x, local.y, local.z);
    }

    public static boolean hasLineOfSight(net.minecraft.world.entity.Mob mob, Vec3 targetPos) {
        Vec3 eyePos = mob.getEyePosition();
        var clipContext = new net.minecraft.world.level.ClipContext(
                eyePos, targetPos,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mob
        );
        var result = mob.level().clip(clipContext);

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return true;
        }

        double distToHit = eyePos.distanceToSqr(result.getLocation());
        double distToTarget = eyePos.distanceToSqr(targetPos);
        return distToHit >= distToTarget - 1.0;
    }

    public static Optional<Found> findNearestBlock(ServerLevel level, Vec3 worldPos, double radius) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        var searchBox = new BoundingBox3d(
                worldPos.x - radius, worldPos.y - radius, worldPos.z - radius,
                worldPos.x + radius, worldPos.y + radius, worldPos.z + radius
        );

        SubLevel nearestSubLevel = null;
        BlockPos nearestLocalPos = null;
        double nearestDistSqr = radius * radius;

        for (SubLevel subLevel : container.queryIntersecting(searchBox)) {
            if (Config.REQUIRE_SHIP_ESSENTIAL_BLOCKS.get()
                    && !ShipDetectionCache.isShip(level, subLevel, level.getGameTime())) continue;
            if (!NearbyPlayerCache.hasNearbyPlayer(level, subLevel, level.getGameTime())) continue;
            for (var holder : subLevel.getPlot().getLoadedChunks()) {
                var chunk = holder.getChunk();
                if (chunk == null) continue;

                int minY = chunk.getMinBuildHeight();
                int maxY = chunk.getMaxBuildHeight();
                ChunkPos cp = chunk.getPos();

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int ly = minY; ly < maxY; ly++) {
                            BlockPos localPos = new BlockPos(cp.getMinBlockX() + lx, ly, cp.getMinBlockZ() + lz);
                            var state = chunk.getBlockState(localPos);
                            if (state.isAir()) continue;

                            Vec3 blockWorldPos = localToWorld(subLevel, localPos);
                            double distSqr = worldPos.distanceToSqr(blockWorldPos);
                            if (distSqr < nearestDistSqr) {
                                nearestSubLevel = subLevel;
                                nearestLocalPos = localPos;
                                nearestDistSqr = distSqr;
                            }
                        }
                    }
                }
            }
        }

        return nearestSubLevel != null
                ? Optional.of(new Found(nearestSubLevel, nearestLocalPos))
                : Optional.empty();
    }
}