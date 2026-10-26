package com.eviesaurr.sablemobinteractions.driving;

import com.mojang.serialization.MapCodec;

public record DriveGoalType<G extends DriveGoal>(String id, MapCodec<G> codec) {
}