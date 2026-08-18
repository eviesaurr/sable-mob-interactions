package com.eviesaurr.sablemobinteractions.goal;

import com.eviesaurr.sablemobinteractions.sublevel.BlockBreakTracker;
import com.eviesaurr.sablemobinteractions.sublevel.NearestBlockCache;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelMeleeAttackGoal extends Goal {

    private final PathfinderMob mob;
    private final double searchRadius;
    private final double attackRange;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private int attackCooldown;

    public SubLevelMeleeAttackGoal(PathfinderMob mob, double searchRadius, double attackRange) {
        this.mob = mob;
        this.searchRadius = searchRadius;
        this.attackRange = attackRange;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level)) return false;

        Optional<SubLevelInteraction.Found> found = NearestBlockCache.getOrCompute(
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
        return this.targetSubLevel != null
                && this.targetLocalPos != null
                && !this.targetSubLevel.isRemoved();
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
    }

    @Override
    public void stop() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    private static final int RETARGET_INTERVAL_TICKS = 20; // re-check once a second

    @Override
    public void tick() {
        if (this.mob.tickCount % RETARGET_INTERVAL_TICKS == 0) {
            retargetIfBetterOptionExists();
            if (this.targetSubLevel == null) return;
        }

        Vec3 worldPos = SubLevelInteraction.localToWorld(this.targetSubLevel, this.targetLocalPos);
        double distSqr = this.mob.position().distanceToSqr(worldPos);

        if (distSqr > this.searchRadius * this.searchRadius) {
            this.targetSubLevel = null;
            this.targetLocalPos = null;
            return;
        }

        this.mob.getLookControl().setLookAt(worldPos.x, worldPos.y, worldPos.z);

        if (distSqr > this.attackRange * this.attackRange) {
            this.mob.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            if (this.mob.getNavigation().isDone()) {
                this.targetSubLevel = null;
                this.targetLocalPos = null;
            }
            return;
        }

        if (!SubLevelInteraction.hasLineOfSight(this.mob, worldPos)) {
            return;
        }

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }

        this.attackCooldown = 10;
        this.mob.swing(InteractionHand.MAIN_HAND);
        if (BlockBreakTracker.registerHit(this.targetSubLevel, this.targetLocalPos, this.mob)) {
            this.targetSubLevel = null;
            this.targetLocalPos = null;
        }
    }

    private void retargetIfBetterOptionExists() {
        if (!(this.mob.level() instanceof ServerLevel level)) return;

        var found = NearestBlockCache.getOrCompute(
                this.mob.position(), this.searchRadius, level.getGameTime(),
                () -> SubLevelInteraction.findNearestBlock(level, this.mob.position(), this.searchRadius)
        );
        if (found.isEmpty()) return;

        SubLevel newSubLevel = found.get().subLevel();
        BlockPos newLocalPos = found.get().localPos();

        boolean sameTarget = newSubLevel == this.targetSubLevel && newLocalPos.equals(this.targetLocalPos);
        if (!sameTarget) {
            this.targetSubLevel = newSubLevel;
            this.targetLocalPos = newLocalPos;
        }
    }
}