package com.eviesaurr.sablemobinteractions.entity;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlock;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity.DriveInput;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.eviesaurr.sablemobinteractions.driving.VehicleDriver;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTier;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTierManager;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

public class SubLevelPilotZombie extends Zombie {

    private static final double SEARCH_RADIUS = 8.0;

    @Nullable
    private BlockPos transmitterPos;
    private String lastStatus = "";
    @Nullable
    private ResourceLocation tierId;
    @Nullable
    private VehicleDriver driver;

    public SubLevelPilotZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public void setTier(ResourceLocation tierId) {
        this.tierId = tierId;
        this.driver = null;
    }

    private VehicleDriver driver() {
        if (driver == null) {
            VehicleTier tier = tierId == null ? null : VehicleTierManager.getTiers().get(tierId);
            driver = new VehicleDriver(tier != null ? tier.goals() : DriveGoals.defaults());
        }
        return driver;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.CRAMMING)
                || super.isInvulnerableTo(source);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;

        if (isDeadOrDying()) {
            stopDriving();
            return;
        }

        if (!isPassenger()) {
            stopDriving();
            reportStatus("not riding anything");
            return;
        }

        Entity seat = getVehicle();
        Optional<SubLevelInteraction.Found> found = Optional.empty();
        if (seat != null) {
            found = SubLevelInteraction.findAtRaw(serverLevel, seat.blockPosition());
        }
        if (found.isEmpty()) {
            found = SubLevelInteraction.findAt(serverLevel, position());
        }
        if (found.isEmpty()) {
            reportStatus("riding, but seat and pilot are not inside any sub-level");
            return;
        }
        SubLevel subLevel = found.get().subLevel();

        if (transmitterPos == null || !(serverLevel.getBlockEntity(transmitterPos) instanceof MobTransmitterBlockEntity)) {
            transmitterPos = findNearestTransmitter(serverLevel, subLevel, found.get().localPos());
            if (transmitterPos == null) {
                reportStatus("riding inside a sub-level, but no Mob Transmitter block within " + (int) SEARCH_RADIUS + " blocks");
                return;
            }
        }

        BlockState transmitterState = serverLevel.getBlockState(transmitterPos);
        Direction facing = transmitterState.hasProperty(MobTransmitterBlock.FACING)
                ? transmitterState.getValue(MobTransmitterBlock.FACING)
                : Direction.NORTH;

        VehicleDriver activeDriver = driver();
        Set<DriveInput> inputs = activeDriver.tick(serverLevel, this, subLevel, facing, position(), getRandom());
        applyInputs(serverLevel, inputs);
        reportStatus("driving, " + activeDriver.note());
    }

    @Override
    public void die(DamageSource source) {
        stopDriving();
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        stopDriving();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (tierId != null) {
            tag.putString("Tier", tierId.toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Tier")) {
            tierId = ResourceLocation.parse(tag.getString("Tier"));
        }
    }

    private void stopDriving() {
        if (transmitterPos != null && level() instanceof ServerLevel serverLevel) {
            applyInputs(serverLevel, Set.of());
        }
        if (driver != null) {
            driver.reset();
        }
    }

    private void applyInputs(ServerLevel level, Set<DriveInput> inputs) {
        if (transmitterPos == null) return;
        if (level.getBlockEntity(transmitterPos) instanceof MobTransmitterBlockEntity transmitter) {
            transmitter.setActiveInputs(inputs);
        } else {
            transmitterPos = null;
        }
    }

    private void reportStatus(String status) {
        if (status.equals(lastStatus)) return;
        lastStatus = status;
        SableMobInteractions.LOGGER.info("Pilot {}: {}", getUUID(), status);
    }

    private static BlockPos findNearestTransmitter(ServerLevel level, SubLevel subLevel, BlockPos centerLocalPos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos nearestRaw = null;
        double nearestDistSqr = Double.MAX_VALUE;
        int radius = (int) SEARCH_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centerLocalPos.getX() + dx, centerLocalPos.getY() + dy, centerLocalPos.getZ() + dz);
                    BlockPos rawPos = SubLevelInteraction.toRawBlockPos(subLevel, cursor);
                    if (!(level.getBlockEntity(rawPos) instanceof MobTransmitterBlockEntity)) continue;
                    double distSqr = cursor.distSqr(centerLocalPos);
                    if (distSqr < nearestDistSqr) {
                        nearestDistSqr = distSqr;
                        nearestRaw = rawPos.immutable();
                    }
                }
            }
        }
        return nearestRaw;
    }
}