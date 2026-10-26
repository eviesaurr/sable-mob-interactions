package com.eviesaurr.sablemobinteractions.driving;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.function.Supplier;

public final class DriveContext {

    private final ServerLevel level;
    private final Entity pilot;
    private final SubLevel subLevel;
    private final Vec3 carPos;
    private final double headingX;
    private final double headingZ;
    private final double speed;
    private final double halfLength;
    private final RandomSource random;
    private final int ticksAlive;
    private final Map<DriveGoal, Object> states;

    public DriveContext(ServerLevel level, Entity pilot, SubLevel subLevel, Vec3 carPos, double headingX,
                        double headingZ, double speed, double halfLength, RandomSource random, int ticksAlive,
                        Map<DriveGoal, Object> states) {
        this.level = level;
        this.pilot = pilot;
        this.subLevel = subLevel;
        this.carPos = carPos;
        this.headingX = headingX;
        this.headingZ = headingZ;
        this.speed = speed;
        this.halfLength = halfLength;
        this.random = random;
        this.ticksAlive = ticksAlive;
        this.states = states;
    }

    public ServerLevel level() {
        return level;
    }

    public Entity pilot() {
        return pilot;
    }

    public SubLevel subLevel() {
        return subLevel;
    }

    public Vec3 carPos() {
        return carPos;
    }

    public double headingX() {
        return headingX;
    }

    public double headingZ() {
        return headingZ;
    }

    public double headingAngle() {
        return Math.atan2(headingZ, headingX);
    }

    public double speed() {
        return speed;
    }

    public double halfLength() {
        return halfLength;
    }

    public RandomSource random() {
        return random;
    }

    public int ticksAlive() {
        return ticksAlive;
    }

    @SuppressWarnings("unchecked")
    public <T> T state(DriveGoal goal, Supplier<T> factory) {
        return (T) states.computeIfAbsent(goal, g -> factory.get());
    }
}