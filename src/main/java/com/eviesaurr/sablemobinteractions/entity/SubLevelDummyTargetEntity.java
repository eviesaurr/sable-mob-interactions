package com.eviesaurr.sablemobinteractions.entity;

import com.eviesaurr.sablemobinteractions.sublevel.BlockBreakTracker;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class SubLevelDummyTargetEntity extends Mob {

    private SubLevel targetSubLevel;
    private BlockPos targetLocalPos;
    private int lifetimeTicks;

    public SubLevelDummyTargetEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(false); // must be false so hurt() actually gets called
        this.setNoGravity(true);
        this.setInvisible(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void assignTarget(SubLevel subLevel, BlockPos localPos) {
        this.targetSubLevel = subLevel;
        this.targetLocalPos = localPos;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.targetSubLevel == null || this.targetLocalPos == null) {
            return false;
        }
        var attacker = source.getEntity();
        BlockBreakTracker.registerHit(this.targetSubLevel, this.targetLocalPos, attacker != null ? attacker : this);
        return true; // absorb the hit - never actually takes damage or dies
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.lifetimeTicks++;
            if (this.lifetimeTicks > 200 || this.targetSubLevel == null || this.targetSubLevel.isRemoved()) {
                this.discard();
            }
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}