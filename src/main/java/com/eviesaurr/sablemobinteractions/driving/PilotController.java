package com.eviesaurr.sablemobinteractions.driving;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlock;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity.DriveInput;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTier;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTierManager;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

public final class PilotController {

    public static final String TIER_KEY = "SmiPilotTier";
    private static final String AXIS_KEY = "SmiThrottleAxis";
    private static final String STEER_KEY = "SmiSteerInverted";

    private static final double SEARCH_RADIUS = 8.0;
    private static final int ORPHAN_TICKS = 200;
    private static final double MAX_WORLD_COORDINATE = 1.0E6;

    private final Mob mob;
    @Nullable
    private BlockPos transmitterPos;
    @Nullable
    private VehicleDriver driver;
    private String lastStatus = "";
    private int orphanTicks;
    private int debugTicks;
    public static volatile boolean debug;

    public PilotController(Mob mob) {
        this.mob = mob;
    }

    private VehicleDriver driver() {
        if (driver == null) {
            ResourceLocation tierId = mob.getPersistentData().contains(TIER_KEY)
                    ? ResourceLocation.parse(mob.getPersistentData().getString(TIER_KEY))
                    : null;
            VehicleTier tier = tierId == null ? null : VehicleTierManager.getTiers().get(tierId);
            driver = tier != null ? new VehicleDriver(tier.goals(), tier.front(), tier.maxSpeed()) : new VehicleDriver(DriveGoals.defaults());
            CompoundTag data = mob.getPersistentData();
            if (data.contains(AXIS_KEY)) {
                driver.restore(data.getInt(AXIS_KEY), data.getBoolean(STEER_KEY));
            }
        }
        return driver;
    }

    public void tick(ServerLevel level) {
        if (mob.isDeadOrDying()) {
            stop();
            return;
        }

        if (!mob.isPassenger()) {
            stop();
            report("not riding anything");
            if (++orphanTicks > ORPHAN_TICKS) {
                mob.discard();
            }
            return;
        }
        orphanTicks = 0;

        Entity seat = mob.getVehicle();
        Optional<SubLevelInteraction.Found> found = Optional.empty();
        if (seat != null) {
            found = SubLevelInteraction.findAtRaw(level, seat.blockPosition());
        }
        if (found.isEmpty()) {
            found = SubLevelInteraction.findAt(level, mob.position());
        }
        if (found.isEmpty()) {
            report("riding, but seat and pilot are not inside any sub-level");
            return;
        }
        SubLevel subLevel = found.get().subLevel();

        if (transmitterPos == null || !(level.getBlockEntity(transmitterPos) instanceof MobTransmitterBlockEntity)) {
            transmitterPos = findNearestTransmitter(level, subLevel, found.get().localPos());
            if (transmitterPos == null) {
                report("no Mob Transmitter block within " + (int) SEARCH_RADIUS + " blocks");
                return;
            }
        }

        BlockState state = level.getBlockState(transmitterPos);
        Direction facing = state.hasProperty(MobTransmitterBlock.FACING)
                ? state.getValue(MobTransmitterBlock.FACING)
                : Direction.NORTH;

        VehicleDriver active = driver();
        Vec3 carPos = SubLevelInteraction.localToWorld(subLevel, seat != null ? seat.blockPosition() : found.get().localPos());
        if (Math.abs(carPos.x) > MAX_WORLD_COORDINATE || Math.abs(carPos.z) > MAX_WORLD_COORDINATE) {
            if (driver != null) {
                driver.reset();
            }
            apply(level, Set.of());
            report("waiting for the sub-level pose to leave plot space");
            return;
        }
        Set<DriveInput> inputs = active.tick(level, mob, subLevel, facing, carPos, mob.getRandom());
        apply(level, inputs);
        persistCalibration(active);
        faceFront(active);
        report("driving, " + active.note());
        if (debug && ++debugTicks % 40 == 0) {
            SableMobInteractions.LOGGER.info("Drive debug {}: {}", mob.getUUID().toString().substring(0, 8), active.debug());
        }
    }

    private void persistCalibration(VehicleDriver active) {
        if (!active.isCalibrated()) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(AXIS_KEY) || data.getInt(AXIS_KEY) != active.physicalAxis()) {
            data.putInt(AXIS_KEY, active.physicalAxis());
        }
        if (!data.contains(STEER_KEY) || data.getBoolean(STEER_KEY) != active.isSteerInverted()) {
            data.putBoolean(STEER_KEY, active.isSteerInverted());
        }
    }

    private void faceFront(VehicleDriver active) {
        float yaw = active.localFrontYaw();
        if (Float.isNaN(yaw)) {
            return;
        }
        ResourceLocation tierId = mob.getPersistentData().contains(TIER_KEY)
                ? ResourceLocation.parse(mob.getPersistentData().getString(TIER_KEY))
                : null;
        VehicleTier tier = tierId == null ? null : VehicleTierManager.getTiers().get(tierId);
        if (tier != null) {
            yaw += (float) tier.pilotYawOffset();
        }
        mob.setYRot(yaw);
        mob.yRotO = yaw;
        mob.setYBodyRot(yaw);
        mob.yBodyRotO = yaw;
        mob.setYHeadRot(yaw);
        mob.yHeadRotO = yaw;
    }

    public void stop() {
        if (transmitterPos != null && mob.level() instanceof ServerLevel level) {
            apply(level, Set.of());
        }
        if (driver != null) {
            driver.reset();
        }
    }

    private void apply(ServerLevel level, Set<DriveInput> inputs) {
        if (transmitterPos == null) return;
        if (level.getBlockEntity(transmitterPos) instanceof MobTransmitterBlockEntity transmitter) {
            transmitter.setActiveInputs(inputs);
        } else {
            transmitterPos = null;
        }
    }

    private void report(String status) {
        if (status.equals(lastStatus)) return;
        lastStatus = status;
        SableMobInteractions.LOGGER.info("Pilot {} ({}): {}", mob.getUUID(), mob.getType().builtInRegistryHolder().key().location(), status);
    }

    private static BlockPos findNearestTransmitter(ServerLevel level, SubLevel subLevel, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        int radius = (int) SEARCH_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockPos raw = SubLevelInteraction.toRawBlockPos(subLevel, cursor);
                    if (!(level.getBlockEntity(raw) instanceof MobTransmitterBlockEntity)) continue;
                    double dist = cursor.distSqr(center);
                    if (dist < best) {
                        best = dist;
                        nearest = raw.immutable();
                    }
                }
            }
        }
        return nearest;
    }
}