package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoalType;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record WanderGoal(int minTicks, int maxTicks, double maxTurnDegrees, double homeRadius) implements DriveGoal {

    public static final MapCodec<WanderGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("min_ticks", 200).forGetter(WanderGoal::minTicks),
            Codec.INT.optionalFieldOf("max_ticks", 600).forGetter(WanderGoal::maxTicks),
            Codec.DOUBLE.optionalFieldOf("max_turn_degrees", 70.0).forGetter(WanderGoal::maxTurnDegrees),
            Codec.DOUBLE.optionalFieldOf("home_radius", 150.0).forGetter(WanderGoal::homeRadius)
    ).apply(instance, WanderGoal::new));

    private static final class State {
        int timer;
        double angle;
        Vec3 home;
    }

    public static WanderGoal defaults() {
        return new WanderGoal(200, 600, 70.0, 150.0);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.WANDER;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        State state = context.state(this, State::new);
        Vec3 car = context.carPos();
        if (state.home == null) {
            var data = context.pilot().getPersistentData();
            if (data.contains("SmiHomeX")) {
                state.home = new Vec3(data.getDouble("SmiHomeX"), car.y, data.getDouble("SmiHomeZ"));
            }
        }
        if (state.home == null || Math.abs(state.home.x) > 1.0E6 || Math.abs(state.home.z) > 1.0E6) {
            state.home = car;
            var data = context.pilot().getPersistentData();
            data.putDouble("SmiHomeX", car.x);
            data.putDouble("SmiHomeZ", car.z);
        }
        if (command.hasHeading || command.override) {
            return;
        }
        if (homeRadius > 0) {
            double dx = state.home.x - car.x;
            double dz = state.home.z - car.z;
            if (Math.hypot(dx, dz) > homeRadius) {
                command.setHeading(Math.atan2(dz, dx), "returning home");
                state.timer = 0;
                return;
            }
        }
        if (--state.timer <= 0) {
            double turn = (context.random().nextDouble() * 2 - 1) * Math.toRadians(maxTurnDegrees);
            state.angle = context.headingAngle() + turn;
            int span = Math.max(1, maxTicks - minTicks);
            state.timer = minTicks + context.random().nextInt(span);
        }
        command.setHeading(state.angle, "wandering");
    }
}