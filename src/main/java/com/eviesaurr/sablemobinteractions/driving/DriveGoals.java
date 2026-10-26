package com.eviesaurr.sablemobinteractions.driving;

import com.eviesaurr.sablemobinteractions.driving.goals.AvoidGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.AvoidCliffGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.AvoidWaterGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.ChaseGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.SlowTurnGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.StayUprightGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.UnstickGoal;
import com.eviesaurr.sablemobinteractions.driving.goals.WanderGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DriveGoals {

    private static final Map<String, DriveGoalType<?>> TYPES = new LinkedHashMap<>();

    public static <G extends DriveGoal> DriveGoalType<G> register(String id, MapCodec<G> codec) {
        DriveGoalType<G> type = new DriveGoalType<>(id, codec);
        TYPES.put(id, type);
        return type;
    }

    public static final DriveGoalType<ChaseGoal> CHASE = register("chase", ChaseGoal.CODEC);
    public static final DriveGoalType<WanderGoal> WANDER = register("wander", WanderGoal.CODEC);
    public static final DriveGoalType<AvoidGoal> AVOID = register("avoid", AvoidGoal.CODEC);
    public static final DriveGoalType<AvoidWaterGoal> AVOID_WATER = register("avoid_water", AvoidWaterGoal.CODEC);
    public static final DriveGoalType<AvoidCliffGoal> AVOID_CLIFFS = register("avoid_cliffs", AvoidCliffGoal.CODEC);
    public static final DriveGoalType<SlowTurnGoal> SLOW_TURN = register("slow_turn", SlowTurnGoal.CODEC);
    public static final DriveGoalType<StayUprightGoal> STAY_UPRIGHT = register("stay_upright", StayUprightGoal.CODEC);
    public static final DriveGoalType<UnstickGoal> UNSTICK = register("unstick", UnstickGoal.CODEC);

    private static final Codec<DriveGoalType<?>> TYPE_CODEC = Codec.STRING.flatXmap(
            id -> {
                DriveGoalType<?> type = TYPES.get(id);
                return type == null
                        ? DataResult.<DriveGoalType<?>>error(() -> "Unknown drive goal type '" + id + "'")
                        : DataResult.<DriveGoalType<?>>success(type);
            },
            type -> DataResult.success(type.id()));

    public static final Codec<DriveGoal> CODEC = TYPE_CODEC.dispatch("type", DriveGoal::type, DriveGoalType::codec);

    public static List<DriveGoal> defaults() {
        return List.of(ChaseGoal.defaults(), WanderGoal.defaults(), AvoidGoal.defaults(),
                AvoidWaterGoal.defaults(), AvoidCliffGoal.defaults(), SlowTurnGoal.defaults(), StayUprightGoal.defaults(),
                UnstickGoal.defaults());
    }

    private DriveGoals() {
    }
}