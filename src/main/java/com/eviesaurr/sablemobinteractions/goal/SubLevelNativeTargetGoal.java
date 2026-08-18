package com.eviesaurr.sablemobinteractions.goal;

import com.eviesaurr.sablemobinteractions.ModEntityTypes;
import com.eviesaurr.sablemobinteractions.entity.SubLevelDummyTargetEntity;
import com.eviesaurr.sablemobinteractions.sublevel.NearestBlockCache;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SubLevelNativeTargetGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 20;

    private final Mob mob;
    private final double searchRadius;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private SubLevelDummyTargetEntity dummy;

    public SubLevelNativeTargetGoal(Mob mob, double searchRadius) {
        this.mob = mob;
        this.searchRadius = searchRadius;
        this.setFlags(EnumSet.noneOf(Flag.class)); // no MOVE/LOOK - the mob's own goals own those
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTarget() != null) return false; // don't hijack a real fight (e.g. attacking a player)
        if (!(this.mob.level() instanceof ServerLevel level)) return false;

        var found = NearestBlockCache.getOrCompute(
                this.mob.position(), this.searchRadius, level.getGameTime(),
                () -> SubLevelInteraction.findNearestBlock(level, this.mob.position(), this.searchRadius)
        );
        if (found.isEmpty()) return false;

        this.targetSubLevel = found.get().subLevel();
        this.targetLocalPos = found.get().localPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.dummy != null && this.dummy.isAlive()
                && this.mob.getTarget() == this.dummy
                && this.targetSubLevel != null && !this.targetSubLevel.isRemoved();
    }

    @Override
    public void start() {
        if (!(this.mob.level() instanceof ServerLevel level)) return;

        Vec3 worldPos = SubLevelInteraction.localToWorld(this.targetSubLevel, this.targetLocalPos);
        this.dummy = ModEntityTypes.TARGET_DUMMY.get().create(level);
        if (this.dummy == null) return;

        this.dummy.moveTo(worldPos.x, worldPos.y, worldPos.z);
        this.dummy.assignTarget(this.targetSubLevel, this.targetLocalPos);
        level.addFreshEntity(this.dummy);

        this.mob.setTarget(this.dummy);
    }

    @Override
    public void stop() {
        if (this.dummy != null) {
            this.dummy.discard();
            this.dummy = null;
        }
        if (this.mob.getTarget() instanceof SubLevelDummyTargetEntity) {
            this.mob.setTarget(null);
        }
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    @Override
    public void tick() {
        if (this.dummy == null) return;

        double distSqr = this.mob.position().distanceToSqr(this.dummy.position());
        if (distSqr > this.searchRadius * this.searchRadius) {
            this.dummy.discard();
            this.dummy = null;
            this.mob.setTarget(null);
            this.targetSubLevel = null;
            this.targetLocalPos = null;
            return;
        }

        if (this.mob.tickCount % RETARGET_INTERVAL_TICKS == 0) {
            Vec3 currentWorldPos = SubLevelInteraction.localToWorld(this.targetSubLevel, this.targetLocalPos);
            this.dummy.moveTo(currentWorldPos.x, currentWorldPos.y, currentWorldPos.z);

            var chunk = this.targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(this.targetLocalPos));
            if (chunk == null || chunk.getBlockState(this.targetLocalPos).isAir()) {
                this.dummy.discard();
                this.dummy = null;
                this.mob.setTarget(null);
                return;
            }
        }

        if (!SubLevelInteraction.hasLineOfSight(this.mob, this.dummy.position())) {
            this.mob.setTarget(null);
        } else if (this.mob.getTarget() != this.dummy) {
            this.mob.setTarget(this.dummy);
        }
    }
}
