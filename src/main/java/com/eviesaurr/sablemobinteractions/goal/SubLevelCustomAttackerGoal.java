package com.eviesaurr.sablemobinteractions.goal;

import com.eviesaurr.sablemobinteractions.api.SubLevelAttacker;
import com.eviesaurr.sablemobinteractions.sublevel.NearestBlockCache;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelCustomAttackerGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 20;

    private final Mob mob;
    private final SubLevelAttacker attacker;
    private final double searchRadius;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private int cooldown;

    public SubLevelCustomAttackerGoal(Mob mob, SubLevelAttacker attacker, double searchRadius) {
        this.mob = mob;
        this.attacker = attacker;
        this.searchRadius = searchRadius;
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
        return this.targetSubLevel != null && this.targetLocalPos != null && !this.targetSubLevel.isRemoved();
    }

    @Override
    public void start() {
        this.cooldown = 0;
    }

    @Override
    public void stop() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    @Override
    public void tick() {
        if (this.mob.tickCount % RETARGET_INTERVAL_TICKS == 0) {
            retarget();
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

        double attackRange = this.attacker.getSubLevelAttackRange();
        if (distSqr > attackRange * attackRange) {
            this.mob.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            return;
        }

        this.mob.getNavigation().stop();

        if (!SubLevelInteraction.hasLineOfSight(this.mob, worldPos)) {
            return; // in range but can't actually see it - hold position, don't fire
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        this.cooldown = this.attacker.getSubLevelAttackCooldownTicks();
        this.attacker.attackSubLevelBlock(worldPos, this.targetSubLevel, this.targetLocalPos);
    }

    private void retarget() {
        if (this.targetSubLevel != null && this.targetLocalPos != null && !this.targetSubLevel.isRemoved()) {
            var chunk = this.targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(this.targetLocalPos));
            if (chunk != null && !chunk.getBlockState(this.targetLocalPos).isAir()) return;
            this.targetSubLevel = null;
            this.targetLocalPos = null;
        }
        if (!(this.mob.level() instanceof ServerLevel level)) return;
        var found = NearestBlockCache.getOrCompute(
                this.mob.position(), this.searchRadius, level.getGameTime(),
                () -> SubLevelInteraction.findNearestBlock(level, this.mob.position(), this.searchRadius)
        );
        if (found.isEmpty()) return;
        this.targetSubLevel = found.get().subLevel();
        this.targetLocalPos = found.get().localPos();
    }
}