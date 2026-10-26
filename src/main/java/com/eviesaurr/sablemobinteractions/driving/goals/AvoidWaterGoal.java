package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoalType;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

public record AvoidWaterGoal(double reach, double speedReach, int deepBlocks, List<Double> fanDegrees) implements DriveGoal {

    private static final List<Double> DEFAULT_FAN = List.of(-30.0, 30.0, -60.0, 60.0, -90.0, 90.0);

    public static final MapCodec<AvoidWaterGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("reach", 14.0).forGetter(AvoidWaterGoal::reach),
            Codec.DOUBLE.optionalFieldOf("speed_reach", 24.0).forGetter(AvoidWaterGoal::speedReach),
            Codec.INT.optionalFieldOf("deep_blocks", 2).forGetter(AvoidWaterGoal::deepBlocks),
            Codec.DOUBLE.listOf().optionalFieldOf("fan_degrees", DEFAULT_FAN).forGetter(AvoidWaterGoal::fanDegrees)
    ).apply(instance, AvoidWaterGoal::new));

    public static AvoidWaterGoal defaults() {
        return new AvoidWaterGoal(14.0, 24.0, 2, DEFAULT_FAN);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.AVOID_WATER;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        if (command.override) {
            return;
        }
        HazardSteering.steer(this, context, command, this::deep, reach, speedReach, fanDegrees, "avoiding water");
    }

    private boolean deep(DriveContext context, int x, int z) {
        var level = context.level();
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int depth = 0;
        for (int y = top - 1; y > level.getMinBuildHeight() && depth < deepBlocks; y--) {
            var fluid = level.getFluidState(new BlockPos(x, y, z));
            if (fluid.is(FluidTags.LAVA)) {
                return true;
            }
            if (!fluid.is(FluidTags.WATER)) {
                break;
            }
            depth++;
        }
        return depth >= deepBlocks;
    }
}