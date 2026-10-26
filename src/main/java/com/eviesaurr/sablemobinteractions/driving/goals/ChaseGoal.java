package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity.DriveInput;
import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoalType;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public record ChaseGoal(double range, double orbitDistance, double collisionMargin) implements DriveGoal {

    public static final MapCodec<ChaseGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 64.0).forGetter(ChaseGoal::range),
            Codec.DOUBLE.optionalFieldOf("orbit_distance", 12.0).forGetter(ChaseGoal::orbitDistance),
            Codec.DOUBLE.optionalFieldOf("collision_margin", 2.5).forGetter(ChaseGoal::collisionMargin)
    ).apply(instance, ChaseGoal::new));

    private static final int MEMORY_TICKS = 160;

    private static final class State {
        int side;
        int sideTicks;
        java.util.UUID targetId;
        int lostTicks;
        double lastX;
        double lastZ;
    }

    public static ChaseGoal defaults() {
        return new ChaseGoal(64.0, 12.0, 2.5);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.CHASE;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        Vec3 car = context.carPos();
        State state = context.state(this, State::new);
        double loseRange = range * 1.8;

        Player target = null;
        double best = range * range;
        for (Player candidate : context.level().players()) {
            if (candidate.isSpectator() || !candidate.isAlive()) {
                continue;
            }
            if (Math.abs(candidate.getX()) > 1.0E6 || Math.abs(candidate.getZ()) > 1.0E6) {
                continue;
            }
            double ox = candidate.getX() - car.x;
            double oz = candidate.getZ() - car.z;
            double flat = ox * ox + oz * oz;
            if (candidate.getUUID().equals(state.targetId)) {
                if (flat < loseRange * loseRange) {
                    target = candidate;
                    break;
                }
                continue;
            }
            if (flat < best && Math.abs(candidate.getY() - car.y) <= range) {
                best = flat;
                target = candidate;
            }
        }

        double targetX;
        double targetZ;
        if (target != null) {
            state.targetId = target.getUUID();
            state.lostTicks = 0;
            state.lastX = target.getX();
            state.lastZ = target.getZ();
            targetX = target.getX();
            targetZ = target.getZ();
        } else if (state.targetId != null && state.lostTicks++ < MEMORY_TICKS) {
            targetX = state.lastX;
            targetZ = state.lastZ;
        } else {
            state.targetId = null;
            return;
        }

        if (state.side == 0) {
            state.side = context.random().nextBoolean() ? 1 : -1;
        }
        if (++state.sideTicks > 400 + context.random().nextInt(400)) {
            state.side = -state.side;
            state.sideTicks = 0;
        }

        double dx = targetX - car.x;
        double dz = targetZ - car.z;
        double distance = Math.hypot(dx, dz);
        double toward = Math.atan2(dz, dx);

        double hit = context.halfLength() + collisionMargin;
        double relative = wrap(toward - context.headingAngle());

        if (distance < hit && Math.abs(relative) < Math.PI * 0.6) {
            command.setHeading(toward, "backing away from a player");
            command.override = true;
            command.throttle = -1.0;
            command.steer = state.side > 0 ? DriveInput.LEFT : DriveInput.RIGHT;
            command.note = "backing away from a player";
            return;
        }

        double orbit = Math.max(orbitDistance, hit + 4.0);
        if (distance < orbit) {
            double closeness = Math.min(1.0, Math.max(0.0, (orbit - distance) / (orbit - hit)));
            double offset = Math.PI / 2 + closeness * (Math.PI / 3);
            command.setHeading(toward + state.side * offset, "circling a player");
            return;
        }

        command.setHeading(toward, "chasing a player");
    }

    private static double wrap(double angle) {
        while (angle > Math.PI) {
            angle -= Math.PI * 2;
        }
        while (angle < -Math.PI) {
            angle += Math.PI * 2;
        }
        return angle;
    }
}