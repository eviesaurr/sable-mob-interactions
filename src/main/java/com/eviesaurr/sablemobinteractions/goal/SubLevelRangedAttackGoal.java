package com.eviesaurr.sablemobinteractions.goal;

// import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.sublevel.NearestBlockCache;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class SubLevelRangedAttackGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 20;
    private static final float ARROW_VELOCITY = 1.6f;
    private static final float ARROW_INACCURACY = 1.5f;

    private final PathfinderMob mob;
    private final double searchRadius;
    private final double attackRange;
    private final int fireCooldownTicks;

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private int cooldown;

    public SubLevelRangedAttackGoal(PathfinderMob mob, double searchRadius, double attackRange, int fireCooldownTicks) {
        this.mob = mob;
        this.searchRadius = searchRadius;
        this.attackRange = attackRange;
        this.fireCooldownTicks = fireCooldownTicks;
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

        if (distSqr > this.attackRange * this.attackRange) {
            this.mob.getNavigation().moveTo(worldPos.x, worldPos.y, worldPos.z, 1.0);
            return;
        }

        this.mob.getNavigation().stop();

        if (!SubLevelInteraction.hasLineOfSight(this.mob, worldPos)) {
            return;
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        this.cooldown = this.fireCooldownTicks;
        fireArrowAt(worldPos);
    }

    private void fireArrowAt(Vec3 targetPos) {
        if (!(this.mob.level() instanceof ServerLevel level)) return;

        this.mob.swing(InteractionHand.MAIN_HAND);

        // debug: check before firing:
//        SableMobInteractions.LOGGER.info("Aiming at targetLocalPos={}", this.targetLocalPos);

        AbstractArrow arrow = new Arrow(level, this.mob, new ItemStack(Items.ARROW), null);
        arrow.setPos(this.mob.getX(), this.mob.getEyeY() - 0.1, this.mob.getZ());

        double dx = targetPos.x - arrow.getX();
        double dy = targetPos.y - arrow.getY();
        double dz = targetPos.z - arrow.getZ();

        arrow.shoot(dx, dy, dz, ARROW_VELOCITY, ARROW_INACCURACY);

//        SableMobInteractions.LOGGER.info("Skeleton firing arrow toward {}", targetPos);
        level.addFreshEntity(arrow);
    }

    private void retarget() {
        if (this.targetSubLevel != null && this.targetLocalPos != null && !this.targetSubLevel.isRemoved()) {
            var chunk = this.targetSubLevel.getPlot().getChunk(new ChunkPos(this.targetLocalPos));
            if (chunk != null && !chunk.getBlockState(this.targetLocalPos).isAir()) {
                return;
            }

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