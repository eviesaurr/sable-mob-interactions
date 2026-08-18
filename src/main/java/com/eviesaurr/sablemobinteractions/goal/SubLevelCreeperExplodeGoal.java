package com.eviesaurr.sablemobinteractions.goal;

import com.eviesaurr.sablemobinteractions.sublevel.NearestBlockCache;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelCreeperExplodeGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 20;

    private final Creeper creeper;
    private final double searchRadius;
    private final double explodeRange;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;

    public SubLevelCreeperExplodeGoal(Creeper creeper, double searchRadius, double explodeRange) {
        this.creeper = creeper;
        this.searchRadius = searchRadius;
        this.explodeRange = explodeRange;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(this.creeper.level() instanceof ServerLevel level)) return false;

        Optional<SubLevelInteraction.Found> found = NearestBlockCache.getOrCompute(
                this.creeper.position(), this.searchRadius, level.getGameTime(),
                () -> SubLevelInteraction.findNearestBlock(level, this.creeper.position(), this.searchRadius)
        );
        if (found.isEmpty()) return false;

        this.targetSubLevel = found.get().subLevel();
        this.targetLocalPos = found.get().localPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetSubLevel != null
                && this.targetLocalPos != null
                && !this.targetSubLevel.isRemoved();
    }

    @Override
    public void stop() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    @Override
    public void tick() {
        if (this.creeper.tickCount % RETARGET_INTERVAL_TICKS == 0
                && this.creeper.getSwellDir() <= 0) { // don't retarget mid-fuse
            var chunk = this.targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(this.targetLocalPos));
            if (chunk == null || chunk.getBlockState(this.targetLocalPos).isAir()) {
                this.targetSubLevel = null;
                this.targetLocalPos = null;
                if (!(this.creeper.level() instanceof ServerLevel level)) return;
                var found = SubLevelInteraction.findNearestBlock(level, this.creeper.position(), this.searchRadius);
                if (found.isEmpty()) return;
                this.targetSubLevel = found.get().subLevel();
                this.targetLocalPos = found.get().localPos();
            }
        }
        if (this.targetSubLevel == null) return;

        Vec3 worldPos = SubLevelInteraction.localToWorld(this.targetSubLevel, this.targetLocalPos);
        double distSqr = this.creeper.position().distanceToSqr(worldPos);

        if (distSqr > this.searchRadius * this.searchRadius) {
            this.targetSubLevel = null;
            this.targetLocalPos = null;
            this.creeper.setSwellDir(-1);
            return;
        }

        this.creeper.getLookControl().setLookAt(worldPos.x, worldPos.y, worldPos.z);

        if (distSqr > this.explodeRange * this.explodeRange) {
            this.creeper.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            this.creeper.setSwellDir(-1); // not close enough yet - stay unlit
            return;
        }

        if (!SubLevelInteraction.hasLineOfSight(this.creeper, worldPos)) {
            this.creeper.setSwellDir(-1); // can't see it - stay unlit, don't light through walls
            return;
        }

        this.creeper.setSwellDir(1);
    }
}