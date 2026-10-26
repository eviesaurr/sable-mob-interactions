package com.eviesaurr.sablemobinteractions.worldgen.vehicle.nodes;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class VehicleNodePiece extends TemplateStructurePiece {

    private static final int MAX_FOUNDATION_DEPTH = 12;
    private static final int CLEARANCE = 16;

    private final ResourceLocation tier;
    private final Rotation rotation;
    private final BlockPos center;

    public VehicleNodePiece(StructureTemplateManager manager, ResourceLocation templateId, BlockPos origin,
                            Rotation rotation, ResourceLocation tier, BlockPos center) {
        super(VehicleNodeRegistry.VEHICLE_NODE_PIECE.get(), 0, manager, templateId, templateId.toString(),
                settings(manager, templateId, rotation), origin);
        this.tier = tier;
        this.rotation = rotation;
        this.center = center;
    }

    public VehicleNodePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(VehicleNodeRegistry.VEHICLE_NODE_PIECE.get(), tag, context.structureTemplateManager(),
                id -> settings(context.structureTemplateManager(), id, Rotation.valueOf(tag.getString("NodeRotation"))));
        this.tier = ResourceLocation.parse(tag.getString("Tier"));
        this.rotation = Rotation.valueOf(tag.getString("NodeRotation"));
        this.center = new BlockPos(tag.getInt("CenterX"), tag.getInt("CenterY"), tag.getInt("CenterZ"));
    }

    public static StructurePlaceSettings settings(StructureTemplateManager manager, ResourceLocation templateId, Rotation rotation) {
        var size = manager.getOrCreate(templateId).getSize();
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2))
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Tier", tier.toString());
        tag.putString("NodeRotation", rotation.name());
        tag.putInt("CenterX", center.getX());
        tag.putInt("CenterY", center.getY());
        tag.putInt("CenterZ", center.getZ());
    }

    private void clearAbove(WorldGenLevel level, BoundingBox box) {
        int minX = Math.max(boundingBox.minX(), box.minX());
        int maxX = Math.min(boundingBox.maxX(), box.maxX());
        int minZ = Math.max(boundingBox.minZ(), box.minZ());
        int maxZ = Math.min(boundingBox.maxZ(), box.maxZ());
        int minY = boundingBox.minY();
        int maxY = Math.min(boundingBox.maxY() + CLEARANCE, level.getMaxBuildHeight() - 1);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos target = new BlockPos(x, y, z);
                    if (!level.getBlockState(target).isAir()) {
                        level.setBlock(target, air, 2);
                    }
                }
            }
        }
    }

    private String templateId() {
        return templateName;
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        if (template.getSize().equals(net.minecraft.core.Vec3i.ZERO)) {
            SableMobInteractions.LOGGER.warn("Vehicle node template {} is empty or missing, nothing to place", templateId());
        } else {
            SableMobInteractions.LOGGER.info("Placing vehicle node {} size {} bounds {} chunk {}", templateId(), template.getSize(), boundingBox, chunkPos);
        }
        clearAbove(level, box);
        if (box.isInside(center)) {
            SableMobInteractions.LOGGER.info("Vehicle node {} planned at y {}, surface at centre is y {}, bounds y {}-{}",
                    templateId(), center.getY(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, center.getX(), center.getZ()),
                    boundingBox.minY(), boundingBox.maxY());
        }
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pos);

        int minX = Math.max(boundingBox.minX(), box.minX());
        int maxX = Math.min(boundingBox.maxX(), box.maxX());
        int minZ = Math.max(boundingBox.minZ(), box.minZ());
        int maxZ = Math.min(boundingBox.maxZ(), box.maxZ());
        BlockState foundation = Blocks.COARSE_DIRT.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int depth = 1; depth <= MAX_FOUNDATION_DEPTH; depth++) {
                    BlockPos below = new BlockPos(x, boundingBox.minY() - depth, z);
                    BlockState state = level.getBlockState(below);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) {
                        level.setBlock(below, foundation, 2);
                    } else {
                        break;
                    }
                }
            }
        }

        ServerLevel serverLevel = level.getLevel();
        BlockPos nodePos = center;
        serverLevel.getServer().execute(() -> {
            VehicleSpawnData data = VehicleSpawnData.get(serverLevel);
            if (data.addNode(tier, nodePos)) {
                SableMobInteractions.LOGGER.info("Registered vehicle node for tier {} at {}", tier, nodePos);
            }
        });
    }
}