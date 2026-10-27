package com.eviesaurr.sablemobinteractions.worldgen.vehicle.nodes;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.ForcedNodePlacement;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTier;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTierManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class VehicleNodeStructure extends Structure {

    private static final List<ResourceLocation> DEFAULT_TEMPLATES = List.of(
            ResourceLocation.fromNamespaceAndPath("sablemobinteractions", "vehicle_spawn_road_1"),
            ResourceLocation.fromNamespaceAndPath("sablemobinteractions", "vehicle_spawn_road_2"));

    public static final MapCodec<VehicleNodeStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Structure.settingsCodec(instance),
            ResourceLocation.CODEC.fieldOf("tier").forGetter(structure -> structure.tier),
            ResourceLocation.CODEC.listOf().optionalFieldOf("templates", DEFAULT_TEMPLATES).forGetter(structure -> structure.templates),
            Codec.INT.optionalFieldOf("y_offset", -1).forGetter(structure -> structure.yOffset),
            Codec.INT.optionalFieldOf("max_height_variation", 10).forGetter(structure -> structure.maxHeightVariation),
            Codec.INT.optionalFieldOf("clear_radius", 24).forGetter(structure -> structure.clearRadius)
    ).apply(instance, VehicleNodeStructure::new));

    private static final AtomicInteger REJECTIONS = new AtomicInteger();
    private static final int MAX_LOGGED_REJECTIONS = 30;

    private final ResourceLocation tier;
    private final List<ResourceLocation> templates;
    private final int yOffset;
    private final int maxHeightVariation;
    private final int clearRadius;

    public VehicleNodeStructure(StructureSettings settings, ResourceLocation tier, List<ResourceLocation> templates, int yOffset, int maxHeightVariation, int clearRadius) {
        super(settings);
        this.tier = tier;
        this.templates = templates;
        this.yOffset = yOffset;
        this.maxHeightVariation = maxHeightVariation;
        this.clearRadius = clearRadius;
    }

    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        if (ForcedNodePlacement.isForced()) {
            return findGenerationPoint(context);
        }
        return super.findValidGenerationPoint(context);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (templates.isEmpty()) {
            return reject("no templates");
        }
        boolean forced = ForcedNodePlacement.isForced();
        ChunkPos chunkPos = context.chunkPos();
        VehicleTier tierSettings = VehicleTierManager.getTiers().get(tier);
        if (tierSettings != null && !forced) {
            double distance = Math.hypot(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
            if (distance < tierSettings.minRadius() || distance > tierSettings.maxRadius()) {
                return reject("outside ring " + tierSettings.minRadius() + "-" + tierSettings.maxRadius() + " at distance " + (int) distance);
            }
        }
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        int height = context.chunkGenerator().getFirstOccupiedHeight(centerX, centerZ,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        if (!forced && height <= context.chunkGenerator().getSeaLevel()) {
            return reject("surface " + height + " is at or below sea level " + context.chunkGenerator().getSeaLevel());
        }

        ResourceLocation templateId = templates.get(context.random().nextInt(templates.size()));
        Rotation rotation = Rotation.getRandom(context.random());
        var size = context.structureTemplateManager().getOrCreate(templateId).getSize();
        int halfExtent = Math.max(size.getX(), size.getZ()) / 2;
        int lowest = height;
        int highest = height;
        long total = 0;
        int samples = 0;
        int step = Math.max(1, halfExtent / 2);
        for (int dx = -halfExtent; dx <= halfExtent; dx += step) {
            for (int dz = -halfExtent; dz <= halfExtent; dz += step) {
                int sample = context.chunkGenerator().getFirstOccupiedHeight(centerX + dx, centerZ + dz,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                total += sample;
                samples++;
                lowest = Math.min(lowest, sample);
                highest = Math.max(highest, sample);
            }
        }
        if (!forced && highest - lowest > maxHeightVariation) {
            return reject("terrain not flat, variation " + (highest - lowest) + " over " + (halfExtent * 2 + 1) + " blocks");
        }
        if (!forced && lowest <= context.chunkGenerator().getSeaLevel()) {
            return reject("footprint reaches sea level " + context.chunkGenerator().getSeaLevel());
        }
        height = (int) Math.round((double) total / Math.max(1, samples));

        BlockPos origin = new BlockPos(centerX - size.getX() / 2, height + yOffset, centerZ - size.getZ() / 2);
        BlockPos center = new BlockPos(centerX, height + yOffset, centerZ);

        return Optional.of(new GenerationStub(center, builder -> {
            if (clearRadius > 0) {
                builder.addPiece(new VehicleNodePadPiece(center, clearRadius));
            }
            builder.addPiece(new VehicleNodePiece(context.structureTemplateManager(), templateId, origin, rotation, tier, center));
        }));
    }

    private Optional<GenerationStub> reject(String reason) {
        if (REJECTIONS.incrementAndGet() <= MAX_LOGGED_REJECTIONS) {
            SableMobInteractions.LOGGER.info("Vehicle node for {} rejected: {}", tier, reason);
        }
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return VehicleNodeRegistry.VEHICLE_NODE.get();
    }
}