package com.eviesaurr.sablemobinteractions.driving;

import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity.DriveInput;
import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VehicleDriver {

    private static final int[][] AXES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final double TURN_ON = Math.toRadians(10);
    private static final double TURN_OFF = Math.toRadians(4);
    private static final double MOVING_SPEED = 0.08;
    private static final double CALIBRATION_SPEED = 0.03;
    private static final double CONFIDENT_SCORE = 1.5;
    private static final double MAX_CALIBRATION_SPEED = 8.0;
    private static final double CALIBRATION_COAST_ON = 0.4;
    private static final double CALIBRATION_COAST_OFF = 0.3;
    private static final int SETTLE_TICKS = 30;
    private static final int THROTTLE_TICKS_NEEDED = 8;
    private static final int CALIBRATION_TICKS = 240;
    private static final int STEER_WINDOW = 20;
    private static final double STEER_DECIDE_SCORE = 0.06;

    private final List<DriveGoal> goals;
    private final Map<DriveGoal, Object> states = new IdentityHashMap<>();

    private int physicalAxis = -1;
    private final String front;
    private final double maxSpeedPerTick;
    private boolean coasting;
    private boolean calibrationCoasting;
    private boolean calibrated;
    private boolean swapped;
    private int lastFrontAxis = -1;
    private int lastHeadingAxis = -1;
    private int throttleTicks;
    private int throttleSign;
    private boolean warnedPerpendicular;
    private final double[] axisScore = new double[4];
    private double travel;
    private double lastError;
    private double lastSpeed;
    private final double[] steerGain = {1.0, -1.0};
    private final double[] steerScore = new double[2];
    private final int[] steerSamples = new int[2];

    private Vec3 lastPos;
    private double lastHeadingX;
    private double lastHeadingZ;
    private boolean hasLastHeading;
    private Set<DriveInput> lastInputs = EnumSet.noneOf(DriveInput.class);

    private int ticksAlive;
    private boolean turningRight;
    private boolean turningLeft;
    private String note = "idle";

    public VehicleDriver(List<DriveGoal> goals) {
        this(goals, "opposite", 0);
    }

    public VehicleDriver(List<DriveGoal> goals, String front, double maxSpeed) {
        this.goals = goals;
        this.front = front;
        this.maxSpeedPerTick = maxSpeed / 20.0;
    }

    public float localFrontYaw() {
        if (lastHeadingAxis < 0) {
            return Float.NaN;
        }
        return (float) Math.toDegrees(Math.atan2(-AXES[lastHeadingAxis][0], AXES[lastHeadingAxis][1]));
    }

    public String debug() {
        return String.format("%s | error %.0f deg | inputs %s | speed %.1f b/s | steerGain fwd %.0f back %.0f | swapped %s | throttle axis %d | front axis %d | calibrated %s",
                note, Math.toDegrees(lastError), lastInputs, lastSpeed, steerGain[0], steerGain[1], swapped, physicalAxis, lastFrontAxis, calibrated);
    }

    public boolean isCalibrated() {
        return calibrated && physicalAxis >= 0;
    }

    public int physicalAxis() {
        return physicalAxis;
    }

    public boolean isSteerInverted() {
        return steerGain[0] < 0;
    }

    public void restore(int axis, boolean steerInvertedValue) {
        if (axis < 0 || axis > 3) {
            return;
        }
        physicalAxis = axis;
        calibrated = true;
        steerGain[0] = steerInvertedValue ? -1.0 : 1.0;
        steerGain[1] = -steerGain[0];
        axisScore[axis] = CONFIDENT_SCORE * 4;
        travel = CONFIDENT_SCORE * 4;
    }

    public String note() {
        return note;
    }

    public void reset() {
        lastPos = null;
        hasLastHeading = false;
        lastInputs = EnumSet.noneOf(DriveInput.class);
        turningRight = false;
        turningLeft = false;
    }

    public Set<DriveInput> tick(ServerLevel level, Entity pilot, SubLevel subLevel, Direction facing,
                                Vec3 carPos, RandomSource random) {
        ticksAlive++;

        Vec3 origin = subLevel.logicalPose().transformPosition(Vec3.ZERO);
        double[][] world = new double[4][2];
        for (int i = 0; i < 4; i++) {
            Vec3 axis = subLevel.logicalPose().transformPosition(new Vec3(AXES[i][0], 0, AXES[i][1])).subtract(origin);
            double length = Math.hypot(axis.x, axis.z);
            world[i][0] = length < 1.0E-6 ? 0 : axis.x / length;
            world[i][1] = length < 1.0E-6 ? 0 : axis.z / length;
        }

        Direction frontDirection = switch (front) {
            case "opposite" -> facing.getOpposite();
            case "left" -> facing.getCounterClockWise();
            case "right" -> facing.getClockWise();
            default -> facing;
        };
        int frontAxis = axisFor(frontDirection);

        double moveX = 0;
        double moveZ = 0;
        double speed = 0;
        if (lastPos != null) {
            moveX = carPos.x - lastPos.x;
            moveZ = carPos.z - lastPos.z;
            speed = Math.hypot(moveX, moveZ);
            if (speed > 8.0) {
                moveX = 0;
                moveZ = 0;
                speed = 0;
            }
        }
        lastPos = carPos;
        lastSpeed = speed * 20.0;

        calibrateForward(world, moveX, moveZ, speed);

        lastFrontAxis = frontAxis;
        int headingAxis = frontAxis;
        swapped = false;
        if (calibrated && physicalAxis >= 0) {
            if (physicalAxis == (frontAxis ^ 1)) {
                swapped = true;
            } else if (physicalAxis != frontAxis) {
                headingAxis = physicalAxis;
                if (!warnedPerpendicular) {
                    warnedPerpendicular = true;
                    SableMobInteractions.LOGGER.warn("Vehicle driver: transmitter front is axis {} but the throttle moves the car along axis {}, "
                                    + "so the car will drive along its throttle axis. Rotate the Mob Transmitter or change the tier front setting",
                            frontAxis, physicalAxis);
                }
            }
        }

        lastHeadingAxis = headingAxis;
        double headingX = world[headingAxis][0];
        double headingZ = world[headingAxis][1];
        if (Math.abs(headingX) + Math.abs(headingZ) < 1.0E-6) {
            lastInputs = EnumSet.noneOf(DriveInput.class);
            return lastInputs;
        }

        calibrateSteering(headingX, headingZ, speed);
        lastHeadingX = headingX;
        lastHeadingZ = headingZ;
        hasLastHeading = true;

        AABB bounds = subLevel.getPlot().getBoundingBox().toAABB();
        double extent = headingAxis < 2 ? bounds.getXsize() : bounds.getZsize();
        double halfLength = Math.max(2.0, extent / 2.0) + 1.0;

        DriveContext context = new DriveContext(level, pilot, subLevel, carPos, headingX, headingZ, speed,
                halfLength, random, ticksAlive, states);
        DriveCommand command = new DriveCommand();
        for (DriveGoal goal : goals) {
            goal.apply(context, command);
        }
        note = command.note;

        Set<DriveInput> inputs = EnumSet.noneOf(DriveInput.class);
        double throttle = command.throttle;
        if (swapped) {
            throttle = -throttle;
        }
        if (maxSpeedPerTick > 0) {
            if (speed > maxSpeedPerTick) {
                coasting = true;
            } else if (speed < maxSpeedPerTick * 0.85) {
                coasting = false;
            }
            if (coasting) {
                throttle = 0;
            }
        }
        boolean braking = false;
        if (command.speedLimit > 0 && !command.override && command.throttle > 0) {
            if (speed > command.speedLimit) {
                throttle = 0;
            }
            braking = speed > command.speedLimit * 1.35;
        }
        if (!calibrated && !command.override && ticksAlive < CALIBRATION_TICKS) {
            if (speed > CALIBRATION_COAST_ON) {
                calibrationCoasting = true;
            } else if (speed < CALIBRATION_COAST_OFF) {
                calibrationCoasting = false;
            }
            if (ticksAlive >= SETTLE_TICKS && !coasting && !calibrationCoasting) {
                inputs.add(DriveInput.FORWARD);
            }
            note = "calibrating drive direction";
            turningLeft = false;
            turningRight = false;
        } else if (command.override) {
            if (throttle > 0) {
                inputs.add(DriveInput.FORWARD);
            } else if (throttle < 0) {
                inputs.add(DriveInput.BACKWARD);
            }
            if (command.steer != null) {
                inputs.add(command.steer);
            }
            turningLeft = false;
            turningRight = false;
        } else {
            if (throttle > 0) {
                inputs.add(DriveInput.FORWARD);
            } else if (throttle < 0) {
                inputs.add(DriveInput.BACKWARD);
            }
            if (braking) {
                inputs.add(swapped ? DriveInput.FORWARD : DriveInput.BACKWARD);
            }
            if (command.hasHeading) {
                lastError = wrap(command.desiredAngle - context.headingAngle());
                steerToward(inputs, lastError, (command.throttle < 0) != swapped);
            }
        }

        lastInputs = inputs.isEmpty() ? EnumSet.noneOf(DriveInput.class) : EnumSet.copyOf(inputs);
        return inputs;
    }

    private void steerToward(Set<DriveInput> inputs, double error, boolean back) {
        turningRight = turningRight ? error > TURN_OFF : error > TURN_ON;
        turningLeft = turningLeft ? -error > TURN_OFF : -error > TURN_ON;
        if (turningRight && turningLeft) {
            if (error >= 0) {
                turningLeft = false;
            } else {
                turningRight = false;
            }
        }
        boolean flip = steerGain[back ? 1 : 0] < 0;
        if (turningRight) {
            inputs.add(flip ? DriveInput.LEFT : DriveInput.RIGHT);
        }
        if (turningLeft) {
            inputs.add(flip ? DriveInput.RIGHT : DriveInput.LEFT);
        }
    }

    private void calibrateForward(double[][] world, double moveX, double moveZ, double speed) {
        boolean forward = lastInputs.contains(DriveInput.FORWARD) && !lastInputs.contains(DriveInput.BACKWARD);
        boolean backward = lastInputs.contains(DriveInput.BACKWARD) && !lastInputs.contains(DriveInput.FORWARD);
        if (!forward && !backward) {
            throttleTicks = 0;
            throttleSign = 0;
            return;
        }
        int direction = forward ? 1 : -1;
        if (direction != throttleSign) {
            throttleSign = direction;
            throttleTicks = 0;
        }
        throttleTicks++;
        if (ticksAlive < SETTLE_TICKS || throttleTicks < THROTTLE_TICKS_NEEDED
                || speed < CALIBRATION_SPEED || speed > MAX_CALIBRATION_SPEED) {
            return;
        }

        double sign = forward ? 1.0 : -1.0;
        for (int i = 0; i < 4; i++) {
            axisScore[i] = axisScore[i] * 0.995 + sign * (world[i][0] * moveX + world[i][1] * moveZ);
        }
        travel = travel * 0.995 + speed;

        int best = 0;
        for (int i = 1; i < 4; i++) {
            if (axisScore[i] > axisScore[best]) {
                best = i;
            }
        }
        double runnerUp = 0;
        for (int i = 0; i < 4; i++) {
            if (i != best) {
                runnerUp = Math.max(runnerUp, axisScore[i]);
            }
        }
        if (axisScore[best] < CONFIDENT_SCORE || axisScore[best] < runnerUp * 2.0 || axisScore[best] < travel * 0.8) {
            return;
        }

        calibrated = true;
        if (best != physicalAxis) {
            SableMobInteractions.LOGGER.info("Vehicle driver: throttle moves the car along axis {}, front is axis {} (setting {}), {}",
                    best, lastFrontAxis, front, best == (lastFrontAxis ^ 1) ? "driving with swapped throttle" : best == lastFrontAxis ? "throttle already drives the front" : "perpendicular");
            physicalAxis = best;
            java.util.Arrays.fill(steerScore, 0);
            java.util.Arrays.fill(steerSamples, 0);
            hasLastHeading = false;
        }
    }

    private void calibrateSteering(double headingX, double headingZ, double speed) {
        boolean fwd = lastInputs.contains(DriveInput.FORWARD);
        boolean back = lastInputs.contains(DriveInput.BACKWARD);
        if (!hasLastHeading || speed < MOVING_SPEED || fwd == back) {
            return;
        }
        boolean right = lastInputs.contains(DriveInput.RIGHT);
        boolean left = lastInputs.contains(DriveInput.LEFT);
        if (right == left) {
            return;
        }

        double cross = lastHeadingX * headingZ - lastHeadingZ * headingX;
        int index = back ? 1 : 0;
        steerScore[index] += right ? cross : -cross;
        steerSamples[index]++;
        if (steerSamples[index] < STEER_WINDOW) {
            return;
        }

        if (Math.abs(steerScore[index]) > STEER_DECIDE_SCORE) {
            steerGain[index] = steerScore[index] > 0 ? 1.0 : -1.0;
        }
        steerScore[index] = 0;
        steerSamples[index] = 0;
    }

    private static int axisFor(Direction facing) {
        for (int i = 0; i < 4; i++) {
            if (AXES[i][0] == facing.getStepX() && AXES[i][1] == facing.getStepZ()) {
                return i;
            }
        }
        return 3;
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