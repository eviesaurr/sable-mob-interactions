package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import com.eviesaurr.sablemobinteractions.driving.DriveGoal;
import com.eviesaurr.sablemobinteractions.driving.DriveGoals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.List;

public record VehicleTier(int minRadius, int maxRadius, int siteSpacing, double siteChance,
                          int carsPerSite, int siteRadius, int minSpacing,
                          PlacementMode placement, int fixedY, List<ResourceLocation> structures,
                          int despawnAfterTicks, List<DriveGoal> goals, List<PilotEntry> pilots, String front, double pilotYawOffset, double maxSpeed) {

    public enum PlacementMode implements StringRepresentable {
        TERRAIN("terrain"),
        FIXED_Y("fixed_y");

        public static final Codec<PlacementMode> CODEC = StringRepresentable.fromEnum(PlacementMode::values);

        private final String id;

        PlacementMode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public static final Codec<VehicleTier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_radius").forGetter(VehicleTier::minRadius),
            Codec.INT.fieldOf("max_radius").forGetter(VehicleTier::maxRadius),
            Codec.INT.optionalFieldOf("site_spacing", 600).forGetter(VehicleTier::siteSpacing),
            Codec.DOUBLE.optionalFieldOf("site_chance", 1.0).forGetter(VehicleTier::siteChance),
            Codec.INT.optionalFieldOf("cars_per_site", 10).forGetter(VehicleTier::carsPerSite),
            Codec.INT.optionalFieldOf("site_radius", 40).forGetter(VehicleTier::siteRadius),
            Codec.INT.optionalFieldOf("min_spacing", 8).forGetter(VehicleTier::minSpacing),
            PlacementMode.CODEC.optionalFieldOf("placement", PlacementMode.TERRAIN).forGetter(VehicleTier::placement),
            Codec.INT.optionalFieldOf("fixed_y", 128).forGetter(VehicleTier::fixedY),
            ResourceLocation.CODEC.listOf().optionalFieldOf("structures", List.of()).forGetter(VehicleTier::structures),
            Codec.INT.optionalFieldOf("despawn_after_ticks", 0).forGetter(VehicleTier::despawnAfterTicks),
            DriveGoals.CODEC.listOf().optionalFieldOf("goals", DriveGoals.defaults()).forGetter(VehicleTier::goals),
            PilotEntry.CODEC.listOf().optionalFieldOf("pilots", PilotEntry.defaults()).forGetter(VehicleTier::pilots),
            Codec.STRING.optionalFieldOf("front", "opposite").forGetter(VehicleTier::front),
            Codec.DOUBLE.optionalFieldOf("pilot_yaw_offset", 0.0).forGetter(VehicleTier::pilotYawOffset),
            Codec.DOUBLE.optionalFieldOf("max_speed", 0.0).forGetter(VehicleTier::maxSpeed)
    ).apply(instance, VehicleTier::new));
}