package com.eviesaurr.sablemobinteractions.driving;

import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity.DriveInput;

import javax.annotation.Nullable;

public final class DriveCommand {

    public boolean hasHeading;
    public double desiredAngle;
    public double throttle = 1.0;
    public boolean override;
    @Nullable
    public DriveInput steer;
    public boolean cornered;
    public double speedLimit;
    public String note = "idle";

    public void limitSpeed(double limit) {
        this.speedLimit = this.speedLimit > 0 ? Math.min(this.speedLimit, limit) : limit;
    }

    public void setHeading(double angle, String note) {
        this.hasHeading = true;
        this.desiredAngle = angle;
        this.note = note;
    }
}