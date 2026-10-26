package com.eviesaurr.sablemobinteractions.driving.goals;

import com.eviesaurr.sablemobinteractions.driving.DriveCommand;
import com.eviesaurr.sablemobinteractions.driving.DriveContext;
import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoalType;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record AvoidGoal(double reach, double speedReach, List<Double> fanDegrees, double obstacleWeight,
                        int interval, boolean avoidPlayers, double playerMargin) implements DriveGoal {

    private static final List<Double> DEFAULT_FAN = List.of(-60.0, -40.0, -20.0, 0.0, 20.0, 40.0, 60.0);
    private static final double CLEAR_ENOUGH = 0.9;

    public static final MapCodec<AvoidGoal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("reach", 9.0).forGetter(AvoidGoal::reach),
            Codec.DOUBLE.optionalFieldOf("speed_reach", 20.0).forGetter(AvoidGoal::speedReach),
            Codec.DOUBLE.listOf().optionalFieldOf("fan_degrees", DEFAULT_FAN).forGetter(AvoidGoal::fanDegrees),
            Codec.DOUBLE.optionalFieldOf("obstacle_weight", 3.0).forGetter(AvoidGoal::obstacleWeight),
            Codec.INT.optionalFieldOf("interval", 3).forGetter(AvoidGoal::interval),
            Codec.BOOL.optionalFieldOf("avoid_players", true).forGetter(AvoidGoal::avoidPlayers),
            Codec.DOUBLE.optionalFieldOf("player_margin", 1.5).forGetter(AvoidGoal::playerMargin)
    ).apply(instance, AvoidGoal::new));

    private static final class State {
        double[] clearance;
        int countdown;
    }

    public static AvoidGoal defaults() {
        return new AvoidGoal(9.0, 20.0, DEFAULT_FAN, 3.0, 3, true, 1.5);
    }

    @Override
    public DriveGoalType<?> type() {
        return DriveGoals.AVOID;
    }

    @Override
    public void apply(DriveContext context, DriveCommand command) {
        int count = fanDegrees.size();
        if (count == 0) {
            return;
        }

        State state = context.state(this, State::new);
        if (state.clearance == null || state.clearance.length != count) {
            state.clearance = new double[count];
            Arrays.fill(state.clearance, 1.0);
        }

        double headingAngle = context.headingAngle();
        if (--state.countdown <= 0) {
            state.countdown = Math.max(1, interval);
            castFan(context, state, headingAngle);
        }

        double desired = command.hasHeading ? command.desiredAngle : headingAngle;
        double chosen = desired;
        double chosenClearance = 1.0;
        double bestCost = Double.MAX_VALUE;
        double bestClearance = 0;
        double maxOffset = 0;
        int center = 0;
        double centerOffset = Double.MAX_VALUE;
        int chosenIndex = 0;
        int nearestIndex = 0;
        double nearestGap = Double.MAX_VALUE;

        for (int k = 0; k < count; k++) {
            double offset = Math.toRadians(fanDegrees.get(k));
            double candidate = headingAngle + offset;
            double cost = (1.0 - state.clearance[k]) * obstacleWeight + Math.abs(wrap(candidate - desired)) / Math.PI;
            double gap = Math.abs(wrap(candidate - desired));
            if (gap < nearestGap) {
                nearestGap = gap;
                nearestIndex = k;
            }
            if (cost < bestCost) {
                chosenIndex = k;
                bestCost = cost;
                chosen = candidate;
                chosenClearance = state.clearance[k];
            }
            bestClearance = Math.max(bestClearance, state.clearance[k]);
            maxOffset = Math.max(maxOffset, Math.abs(offset));
            if (Math.abs(offset) < centerOffset) {
                centerOffset = Math.abs(offset);
                center = k;
            }
        }

        if (chosenClearance >= CLEAR_ENOUGH && Math.abs(wrap(desired - headingAngle)) <= maxOffset) {
            chosen = desired;
        }

        if (command.hasHeading && chosenIndex != nearestIndex && !command.note.endsWith("(avoiding)")) {
            command.note = command.note + " (avoiding)";
        }
        command.hasHeading = true;
        command.desiredAngle = chosen;
        if (state.clearance[center] < 0.25 && bestClearance < 0.4) {
            command.cornered = true;
        }
    }

    private void castFan(DriveContext context, State state, double headingAngle) {
        Vec3 car = context.carPos();
        double hx = context.headingX();
        double hz = context.headingZ();
        double length = reach + Math.min(context.speed(), 2.5) * speedReach;
        double half = context.halfLength();
        Vec3 start = new Vec3(car.x + hx * half, car.y + 0.3, car.z + hz * half);

        List<AABB> targets = avoidPlayers ? nearbyPlayerBoxes(context, start, length) : List.of();

        for (int k = 0; k < fanDegrees.size(); k++) {
            double angle = headingAngle + Math.toRadians(fanDegrees.get(k));
            Vec3 end = start.add(Math.cos(angle) * length, 0, Math.sin(angle) * length);
            double clear = clearanceAlong(context.level(), context.pilot(), context.subLevel(), start, end, length);
            for (AABB box : targets) {
                Optional<Vec3> hit = box.clip(start, end);
                if (hit.isPresent()) {
                    clear = Math.min(clear, Mth.clamp(start.distanceTo(hit.get()) / length, 0.0, 1.0));
                }
            }
            state.clearance[k] = clear;
        }
    }

    private List<AABB> nearbyPlayerBoxes(DriveContext context, Vec3 start, double length) {
        Entity vehicle = context.pilot().getVehicle();
        double reachSqr = (length + playerMargin + 4.0) * (length + playerMargin + 4.0);
        List<AABB> boxes = new ArrayList<>();
        for (Player player : context.level().players()) {
            if (player.getVehicle() == vehicle || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double dx = player.getX() - start.x;
            double dz = player.getZ() - start.z;
            if (dx * dx + dz * dz > reachSqr || Math.abs(player.getY() - start.y) > 8.0) {
                continue;
            }
            AABB box = player.getBoundingBox();
            boxes.add(new AABB(box.minX - playerMargin, start.y - 4, box.minZ - playerMargin,
                    box.maxX + playerMargin, start.y + 4, box.maxZ + playerMargin));
        }
        return boxes;
    }

    private static double clearanceAlong(ServerLevel level, Entity pilot, SubLevel own, Vec3 from, Vec3 to, double length) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot));
        if (hit.getType() == HitResult.Type.MISS) {
            return 1.0;
        }
        var found = SubLevelInteraction.findAtRaw(level, hit.getBlockPos());
        if (found.isPresent() && found.get().subLevel().getUniqueId().equals(own.getUniqueId())) {
            return 1.0;
        }
        return Mth.clamp(from.distanceTo(hit.getLocation()) / length, 0.0, 1.0);
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