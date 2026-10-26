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
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public record AvoidCliffGoal(double reach, double speedReach, int maxDrop, List<Double> fanDegrees) implements DriveGoal {

    private static final List<Double> DEFAULT_FAN = List.of(-30.0, 30.0, -60.0, 60.0, -90.0, 90.0);

    public static final MapCodec<AvoidCliffGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("reach", 14.0).forGetter(AvoidCliffGoal::reach),
            Codec.DOUBLE.optionalFieldOf("speed_reach", 24.0).forGetter(AvoidCliffGoal::speedReach),
            Codec.INT.optionalFieldOf("max_drop", 4).forGetter(AvoidCliffGoal::maxDrop),
            Codec.DOUBLE.listOf().optionalFieldOf("fan_degrees", DEFAULT_FAN).forGetter(AvoidCliffGoal::fanDegrees)
    ).apply(instance, AvoidCliffGoal::new));

    public static AvoidCliffGoal defaults() {
        return new AvoidCliffGoal(14.0, 24.0, 4, DEFAULT_FAN);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.AVOID_CLIFFS;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        if (command.override) {
            return;
        }
        int carX = Mth.floor(context.carPos().x);
        int carZ = Mth.floor(context.carPos().z);
        if (!context.level().hasChunk(carX >> 4, carZ >> 4)) {
            return;
        }
        int reference = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, carX, carZ);
        HazardSteering.steer(this, context, command, (ctx, x, z) -> drop(ctx, x, z, reference), reach, speedReach,
                fanDegrees, "avoiding a drop");
    }

    private boolean drop(DriveContext context, int x, int z, int reference) {
        var level = context.level();
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return reference - ground > maxDrop;
    }
}