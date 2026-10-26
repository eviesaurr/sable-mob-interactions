package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoalType;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

public record SlowTurnGoal(double startDegrees, double fullDegrees, double turnSpeed, double minSpeed) implements DriveGoal {

    public static final MapCodec<SlowTurnGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("start_degrees", 25.0).forGetter(SlowTurnGoal::startDegrees),
            Codec.DOUBLE.optionalFieldOf("full_degrees", 90.0).forGetter(SlowTurnGoal::fullDegrees),
            Codec.DOUBLE.optionalFieldOf("turn_speed", 1.2).forGetter(SlowTurnGoal::turnSpeed),
            Codec.DOUBLE.optionalFieldOf("min_speed", 0.3).forGetter(SlowTurnGoal::minSpeed)
    ).apply(instance, SlowTurnGoal::new));

    public static SlowTurnGoal defaults() {
        return new SlowTurnGoal(25.0, 90.0, 1.2, 0.3);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.SLOW_TURN;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        if (command.override || !command.hasHeading) {
            return;
        }
        double error = Math.abs(Math.toDegrees(wrap(command.desiredAngle - context.headingAngle())));
        if (error <= startDegrees) {
            return;
        }
        double t = Mth.clamp((error - startDegrees) / Math.max(1.0, fullDegrees - startDegrees), 0.0, 1.0);
        command.limitSpeed(Mth.lerp(t, turnSpeed, minSpeed));
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