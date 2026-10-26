package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import net.minecraft.util.Mth;

import java.util.List;

final class HazardSteering {

    interface Hazard {
        boolean at(DriveContext context, int x, int z);
    }

    private static final class State {
        int lockTicks;
        double lockAngle;
        int reverseTicks;
    }

    private static final int LOCK_TICKS = 30;
    private static final int REVERSE_TICKS = 40;
    private static final double[] FRACTIONS = {0.2, 0.4, 0.6, 0.8, 1.0};

    static boolean steer(DriveGoal owner, DriveContext context, DriveCommand command, Hazard hazard, double reach,
                         double speedReach, List<Double> fanDegrees, String reason) {
        State state = context.state(owner, State::new);

        if (state.reverseTicks > 0) {
            state.reverseTicks--;
            command.override = true;
            command.throttle = -1.0;
            command.steer = null;
            command.note = reason + " (backing off)";
            return true;
        }

        double speed = Math.min(context.speed(), 3.0);
        double length = reach + speed * speedReach;
        double base = command.hasHeading ? command.desiredAngle : context.headingAngle();

        if (state.lockTicks > 0) {
            state.lockTicks--;
            if (!blocked(context, hazard, state.lockAngle, length)) {
                apply(command, state.lockAngle, speed, reason);
                return true;
            }
            state.lockTicks = 0;
        }

        boolean ahead = blocked(context, hazard, base, length);
        boolean along = blocked(context, hazard, context.headingAngle(), length * 0.7);
        if (!ahead && !along) {
            return false;
        }

        List<Double> sorted = fanDegrees.stream().sorted((a, b) -> Double.compare(Math.abs(a), Math.abs(b))).toList();
        for (double degrees : sorted) {
            double angle = base + Math.toRadians(degrees);
            if (!blocked(context, hazard, angle, length)) {
                state.lockAngle = angle;
                state.lockTicks = LOCK_TICKS;
                apply(command, angle, speed, reason);
                return true;
            }
        }

        state.reverseTicks = REVERSE_TICKS;
        command.override = true;
        command.throttle = -1.0;
        command.steer = null;
        command.note = reason + " (backing off)";
        return true;
    }

    private static void apply(DriveCommand command, double angle, double speed, String reason) {
        command.hasHeading = true;
        command.desiredAngle = angle;
        command.note = reason;
        command.limitSpeed(Math.max(0.2, speed * 0.5));
    }

    private static boolean blocked(DriveContext context, Hazard hazard, double angle, double length) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double startX = context.carPos().x + context.headingX() * context.halfLength();
        double startZ = context.carPos().z + context.headingZ() * context.halfLength();
        for (double fraction : FRACTIONS) {
            int x = Mth.floor(startX + cos * length * fraction);
            int z = Mth.floor(startZ + sin * length * fraction);
            if (hazard.at(context, x, z)) {
                return true;
            }
        }
        return false;
    }

    private HazardSteering() {
    }
}