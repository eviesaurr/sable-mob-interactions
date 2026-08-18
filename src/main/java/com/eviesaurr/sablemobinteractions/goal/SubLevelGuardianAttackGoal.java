package com.eviesaurr.sablemobinteractions.goal;

import com.eviesaurr.sablemobinteractions.sublevel.BlockBreakTracker;
import com.eviesaurr.sablemobinteractions.sublevel.NearestBlockCache;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelGuardianAttackGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 20;
    private static final int CHARGE_TICKS = 80; // matches vanilla guardian beam charge-up (~4s)

    private final Guardian guardian;
    private final double searchRadius;
    private final double beamRange;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private int chargeTicks;

    public SubLevelGuardianAttackGoal(Guardian guardian, double searchRadius, double beamRange) {
        this.guardian = guardian;
        this.searchRadius = searchRadius;
        this.beamRange = beamRange;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(this.guardian.level() instanceof ServerLevel level)) return false;

        Optional<SubLevelInteraction.Found> found = NearestBlockCache.getOrCompute(
                this.guardian.position(), this.searchRadius, level.getGameTime(),
                () -> SubLevelInteraction.findNearestBlock(level, this.guardian.position(), this.searchRadius)
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
        this.chargeTicks = 0;
    }

    @Override
    public void stop() {
        this.chargeTicks = 0;
        this.targetSubLevel = null;
        this.targetLocalPos = null;
    }

    @Override
    public void tick() {
        if (this.guardian.tickCount % RETARGET_INTERVAL_TICKS == 0) {
            var chunk = this.targetSubLevel.getPlot().getChunk(new net.minecraft.world.level.ChunkPos(this.targetLocalPos));
            if (chunk == null || chunk.getBlockState(this.targetLocalPos).isAir()) {
                retarget();
                if (this.targetSubLevel == null) return;
            }
        }

        Vec3 worldPos = SubLevelInteraction.localToWorld(this.targetSubLevel, this.targetLocalPos);
        double distSqr = this.guardian.position().distanceToSqr(worldPos);

        if (distSqr > this.searchRadius * this.searchRadius) {
            this.targetSubLevel = null;
            this.targetLocalPos = null;
            this.chargeTicks = 0;
            return;
        }

        this.guardian.getLookControl().setLookAt(worldPos.x, worldPos.y, worldPos.z);

        if (distSqr > this.beamRange * this.beamRange) {
            this.guardian.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            this.chargeTicks = 0; // lost range - reset the charge
            return;
        }

        this.guardian.getNavigation().stop();

        if (!SubLevelInteraction.hasLineOfSight(this.guardian, worldPos)) {
            this.chargeTicks = 0; // lost sight - reset the charge, same as losing range
            return;
        }

        this.chargeTicks++;

        if (this.chargeTicks % 5 == 0) {
            Vec3 eyePos = this.guardian.getEyePosition();
            Vec3 dir = worldPos.subtract(eyePos).normalize();
            for (double d = 0; d < Math.sqrt(distSqr); d += 0.5) {
                Vec3 particlePos = eyePos.add(dir.scale(d));
                ((ServerLevel) this.guardian.level()).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.BUBBLE,
                        particlePos.x, particlePos.y, particlePos.z,
                        1, 0, 0, 0, 0);
            }
        }

        if (this.chargeTicks >= CHARGE_TICKS) {
            this.chargeTicks = 0;
            if (BlockBreakTracker.registerHit(this.targetSubLevel, this.targetLocalPos, this.guardian)) {
                this.targetSubLevel = null;
                this.targetLocalPos = null;
            }
        }
    }

    private void retarget() {
        this.targetSubLevel = null;
        this.targetLocalPos = null;
        if (!(this.guardian.level() instanceof ServerLevel level)) return;
        var found = NearestBlockCache.getOrCompute(
                this.guardian.position(), this.searchRadius, level.getGameTime(),
                () -> SubLevelInteraction.findNearestBlock(level, this.guardian.position(), this.searchRadius)
        );
        if (found.isEmpty()) return;
        this.targetSubLevel = found.get().subLevel();
        this.targetLocalPos = found.get().localPos();
    }
}