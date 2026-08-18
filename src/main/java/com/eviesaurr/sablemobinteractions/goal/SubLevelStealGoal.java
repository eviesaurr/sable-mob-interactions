package com.eviesaurr.sablemobinteractions.goal;

import com.eviesaurr.sablemobinteractions.ModTags;
import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.config.Config;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelStealGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 20;
    private static final double PICKUP_RANGE = 4.0;

    private final EnderMan enderman;
    private final double searchRadius;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;

    public SubLevelStealGoal(EnderMan enderman, double searchRadius) {
        this.enderman = enderman;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static final float STEAL_CHANCE = 0.15f; // 15% chance to even consider stealing, checked once per canUse poll

    @Override
    public boolean canUse() {
        if (this.enderman.getCarriedBlock() != null) return false;
        if (this.enderman.getRandom().nextFloat() > STEAL_CHANCE) return false;
        if (!(this.enderman.level() instanceof ServerLevel level)) return false;

        Optional<SubLevelInteraction.Found> found = findStealableBlock(level, this.enderman.position());
        if (found.isEmpty()) return false;

        this.targetSubLevel = found.get().subLevel();
        this.targetLocalPos = found.get().localPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetSubLevel != null
                && this.targetLocalPos != null
                && !this.targetSubLevel.isRemoved()
                && this.enderman.getCarriedBlock() == null;
    }

    @Override
    public void stop() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    @Override
    public void tick() {
        if (this.enderman.tickCount % RETARGET_INTERVAL_TICKS == 0) {
            if (!(this.enderman.level() instanceof ServerLevel level)) return;
            var found = findStealableBlock(level, this.enderman.position());
            if (found.isEmpty()) {
                this.targetSubLevel = null;
                return;
            }
            this.targetSubLevel = found.get().subLevel();
            this.targetLocalPos = found.get().localPos();
        }
        if (this.targetSubLevel == null) return;

        Vec3 worldPos = SubLevelInteraction.localToWorld(this.targetSubLevel, this.targetLocalPos);
        double distSqr = this.enderman.position().distanceToSqr(worldPos);

        if (distSqr > this.searchRadius * this.searchRadius) {
            this.targetSubLevel = null;
            this.targetLocalPos = null;
            return;
        }

        this.enderman.getLookControl().setLookAt(worldPos.x, worldPos.y, worldPos.z);

        if (distSqr > PICKUP_RANGE * PICKUP_RANGE) {
            this.enderman.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            if (this.enderman.getNavigation().isDone()) {
                this.targetSubLevel = null;
                this.targetLocalPos = null;
            }
            return;
        }

        if (!SubLevelInteraction.hasLineOfSight(this.enderman, worldPos)) {
            return; // close enough by distance, but can't actually see/reach it
        }

        var chunk = this.targetSubLevel.getPlot().getChunk(
                new net.minecraft.world.level.ChunkPos(this.targetLocalPos)
        );
        if (chunk == null) {
            this.targetSubLevel = null;
            this.targetLocalPos = null;
            return;
        }

        BlockState state = chunk.getBlockState(this.targetLocalPos);
        BlockPos rawPos = SubLevelInteraction.toRawBlockPos(this.targetSubLevel, this.targetLocalPos);

        this.enderman.setCarriedBlock(state);
        this.enderman.level().destroyBlock(rawPos, false, this.enderman);
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    private boolean isStealable(BlockState state) {
        if (state.is(ModTags.ENDERMAN_STEALABLE)) return true;

        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return Config.ENDERMAN_STEAL_MOD_NAMESPACES.get().contains(blockId.getNamespace());
    }

    private Optional<SubLevelInteraction.Found> findStealableBlock(ServerLevel level, Vec3 pos) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) return Optional.empty();

        var searchBox = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                pos.x - this.searchRadius, pos.y - this.searchRadius, pos.z - this.searchRadius,
                pos.x + this.searchRadius, pos.y + this.searchRadius, pos.z + this.searchRadius
        );

        SubLevel nearestSubLevel = null;
        BlockPos nearestLocalPos = null;
        double nearestDistSqr = this.searchRadius * this.searchRadius;

        for (SubLevel subLevel : container.queryIntersecting(searchBox)) {
            for (var holder : subLevel.getPlot().getLoadedChunks()) {
                var chunk = holder.getChunk();
                if (chunk == null) continue;

                int minY = chunk.getMinBuildHeight();
                int maxY = chunk.getMaxBuildHeight();
                var cp = chunk.getPos();

                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int ly = minY; ly < maxY; ly++) {
                            BlockPos localPos = new BlockPos(cp.getMinBlockX() + lx, ly, cp.getMinBlockZ() + lz);
                            var state = chunk.getBlockState(localPos);
                            if (!isStealable(state)) continue;

                            Vec3 blockWorldPos = SubLevelInteraction.localToWorld(subLevel, localPos);
                            double distSqr = pos.distanceToSqr(blockWorldPos);
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
                ? Optional.of(new SubLevelInteraction.Found(nearestSubLevel, nearestLocalPos))
                : Optional.empty();
    }
}