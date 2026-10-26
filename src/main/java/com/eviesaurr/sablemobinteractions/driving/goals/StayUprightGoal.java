package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoalType;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public record StayUprightGoal(double warnDegrees, double flippedDegrees, double steadySpeed,
                              int rightAfterTicks, double liftBlocks, int cooldownTicks) implements DriveGoal {

    public static final MapCodec<StayUprightGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("warn_degrees", 22.0).forGetter(StayUprightGoal::warnDegrees),
            Codec.DOUBLE.optionalFieldOf("flipped_degrees", 70.0).forGetter(StayUprightGoal::flippedDegrees),
            Codec.DOUBLE.optionalFieldOf("steady_speed", 0.3).forGetter(StayUprightGoal::steadySpeed),
            Codec.INT.optionalFieldOf("right_after_ticks", 60).forGetter(StayUprightGoal::rightAfterTicks),
            Codec.DOUBLE.optionalFieldOf("lift_blocks", 2.5).forGetter(StayUprightGoal::liftBlocks),
            Codec.INT.optionalFieldOf("cooldown_ticks", 100).forGetter(StayUprightGoal::cooldownTicks)
    ).apply(instance, StayUprightGoal::new));

    private static final double STILL_SPEED = 0.08;

    private static final class State {
        int flippedTicks;
        int cooldown;
    }

    public static StayUprightGoal defaults() {
        return new StayUprightGoal(22.0, 70.0, 0.3, 60, 2.5, 100);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.STAY_UPRIGHT;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        State state = context.state(this, State::new);
        if (state.cooldown > 0) {
            state.cooldown--;
        }

        var pose = context.subLevel().logicalPose();
        Vec3 origin = pose.transformPosition(Vec3.ZERO);
        Vec3 up = pose.transformPosition(new Vec3(0, 1, 0)).subtract(origin);
        double length = up.length();
        if (length < 1.0e-6) {
            return;
        }
        double tilt = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, up.y / length))));

        if (tilt >= flippedDegrees) {
            command.hasHeading = false;
            command.limitSpeed(0.01);
            if (context.speed() < STILL_SPEED) {
                state.flippedTicks++;
            }
            if (state.flippedTicks >= rightAfterTicks && state.cooldown == 0) {
                state.flippedTicks = 0;
                state.cooldown = cooldownTicks;
                rightCar(context, new Vector3d(up.x / length, up.y / length, up.z / length));
            }
            command.note = "flipped over";
            return;
        }

        state.flippedTicks = 0;
        if (tilt < warnDegrees) {
            return;
        }
        command.hasHeading = false;
        command.limitSpeed(steadySpeed);
        command.note = "steadying the car";
    }

    private void rightCar(DriveContext context, Vector3d up) {
        if (!(context.subLevel() instanceof ServerSubLevel target)) {
            return;
        }
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(context.level());
        if (container == null) {
            return;
        }

        var pose = target.logicalPose();
        Vector3d worldUp = new Vector3d(0, 1, 0);
        Quaterniond fix = new Quaterniond();
        if (up.dot(worldUp) < -0.95) {
            Vector3d axis = new Vector3d(context.headingX(), 0, context.headingZ());
            if (axis.lengthSquared() < 1.0e-6) {
                axis.set(1, 0, 0);
            }
            fix.fromAxisAngleRad(axis.normalize(), Math.PI);
        } else {
            fix.rotationTo(up, worldUp);
        }
        Quaterniond orientation = fix.mul(new Quaterniond(pose.orientation()), new Quaterniond());
        orientation.normalize();

        Vector3d position = new Vector3d(pose.position());
        position.y += liftBlocks;

        var pipeline = container.physicsSystem().getPipeline();
        pipeline.resetVelocity(target);
        pose.position().set(position.x, position.y, position.z);
        pipeline.teleport(target, position, orientation);
        target.updateLastPose();
        target.updateBoundingBox();
        SableMobInteractions.LOGGER.info("Righted a flipped vehicle {}", target.getUniqueId());
    }
}