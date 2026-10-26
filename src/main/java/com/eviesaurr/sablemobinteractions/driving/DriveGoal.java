package com.eviesaurr.sablemobinteractions.driving;

public interface DriveGoal {

    DriveGoalType<?> type();

    void apply(DriveContext context, DriveCommand command);
}