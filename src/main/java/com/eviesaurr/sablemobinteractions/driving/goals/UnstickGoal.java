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
import net.minecraft.world.phys.Vec3;

public record UnstickGoal(int settleTicks, int stuckTicks, int reverseTicks, double minProgress) implements DriveGoal {

    public static final MapCodec<UnstickGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("settle_ticks", 100).forGetter(UnstickGoal::settleTicks),
            Codec.INT.optionalFieldOf("stuck_ticks", 80).forGetter(UnstickGoal::stuckTicks),
            Codec.INT.optionalFieldOf("reverse_ticks", 40).forGetter(UnstickGoal::reverseTicks),
            Codec.DOUBLE.optionalFieldOf("min_progress", 1.0).forGetter(UnstickGoal::minProgress)
    ).apply(instance, UnstickGoal::new));

    private static final class State {
        int reversing;
        int windowTicks;
        Vec3 anchor;
        DriveInput steer = DriveInput.LEFT;
    }

    public static UnstickGoal defaults() {
        return new UnstickGoal(100, 80, 40, 1.0);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.UNSTICK;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        State state = context.state(this, State::new);

        if (state.reversing > 0) {
            state.reversing--;
            command.override = true;
            command.throttle = -1.0;
            command.steer = state.steer;
            command.note = "reversing out of a jam";
            state.anchor = null;
            return;
        }

        if (context.ticksAlive() < settleTicks) {
            return;
        }

        Vec3 position = context.carPos();
        if (state.anchor == null) {
            state.anchor = position;
            state.windowTicks = 0;
            return;
        }

        state.windowTicks++;
        double progress = Math.hypot(position.x - state.anchor.x, position.z - state.anchor.z);
        if (progress >= minProgress) {
            state.anchor = position;
            state.windowTicks = 0;
            return;
        }

        int needed = command.cornered ? Math.max(20, stuckTicks / 2) : stuckTicks;
        if (state.windowTicks >= needed) {
            state.reversing = reverseTicks;
            state.anchor = null;
            state.windowTicks = 0;
            state.steer = context.random().nextBoolean() ? DriveInput.LEFT : DriveInput.RIGHT;
        }
    }
}