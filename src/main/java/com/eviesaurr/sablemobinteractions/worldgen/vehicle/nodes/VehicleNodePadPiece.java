package com.eviesaurr.sablemobinteractions.worldgen.vehicle.nodes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class VehicleNodePadPiece extends StructurePiece {

    private static final int CLEARANCE = 20;
    private static final int FOUNDATION_DEPTH = 14;
    private static final double WOBBLE = 0.28;
    private static final double PLATEAU = 0.65;

    private static final class Palette {
        final BlockState plateau;
        final BlockState slopeTop;
        final BlockState fill;

        Palette(Block plateau, Block slopeTop, Block fill) {
            this.plateau = plateau.defaultBlockState();
            this.slopeTop = slopeTop.defaultBlockState();
            this.fill = fill.defaultBlockState();
        }
    }

    private static final Palette DEFAULT_PALETTE = new Palette(Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT);
    private static final Palette DESERT_PALETTE = new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.SAND, Blocks.SANDSTONE);
    private static final Palette BADLANDS_PALETTE = new Palette(Blocks.PACKED_MUD, Blocks.RED_SAND, Blocks.TERRACOTTA);
    private static final Palette SNOW_PALETTE = new Palette(Blocks.COARSE_DIRT, Blocks.SNOW_BLOCK, Blocks.DIRT);
    private static final Palette STONY_PALETTE = new Palette(Blocks.GRAVEL, Blocks.GRAVEL, Blocks.STONE);

    private final int padY;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final double phaseA;
    private final double phaseB;

    public VehicleNodePadPiece(BlockPos center, int radius) {
        super(VehicleNodeRegistry.VEHICLE_NODE_PAD_PIECE.get(), 0, boxFor(center, radius));
        this.padY = center.getY();
        this.centerX = center.getX();
        this.centerZ = center.getZ();
        this.radius = radius;
        this.phaseA = phase(centerX, centerZ, 0);
        this.phaseB = phase(centerX, centerZ, 1);
    }

    public VehicleNodePadPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(VehicleNodeRegistry.VEHICLE_NODE_PAD_PIECE.get(), tag);
        this.padY = tag.getInt("PadY");
        this.centerX = tag.getInt("CenterX");
        this.centerZ = tag.getInt("CenterZ");
        this.radius = tag.getInt("Radius");
        this.phaseA = phase(centerX, centerZ, 0);
        this.phaseB = phase(centerX, centerZ, 1);
    }

    private static BoundingBox boxFor(BlockPos center, int radius) {
        int reach = (int) Math.ceil(radius * (1.0 + WOBBLE)) + 1;
        return new BoundingBox(center.getX() - reach, center.getY() - FOUNDATION_DEPTH, center.getZ() - reach,
                center.getX() + reach, center.getY() + CLEARANCE, center.getZ() + reach);
    }

    private static double phase(int x, int z, int salt) {
        long h = x * 341873128712L + z * 132897987541L + salt * 42317861L;
        h ^= h >>> 29;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return (h & 0xFFFF) / 65535.0 * Math.PI * 2;
    }

    private static Palette paletteFor(WorldGenLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return BADLANDS_PALETTE;
        }
        if (biome.is(Tags.Biomes.IS_DESERT)) {
            return DESERT_PALETTE;
        }
        if (biome.is(Tags.Biomes.IS_SNOWY)) {
            return SNOW_PALETTE;
        }
        if (biome.is(Biomes.STONY_SHORE)) {
            return STONY_PALETTE;
        }
        return DEFAULT_PALETTE;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("PadY", padY);
        tag.putInt("CenterX", centerX);
        tag.putInt("CenterZ", centerZ);
        tag.putInt("Radius", radius);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        int minX = Math.max(boundingBox.minX(), box.minX());
        int maxX = Math.min(boundingBox.maxX(), box.maxX());
        int minZ = Math.max(boundingBox.minZ(), box.minZ());
        int maxZ = Math.min(boundingBox.maxZ(), box.maxZ());
        int ceiling = level.getMaxBuildHeight() - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        Palette palette = paletteFor(level, new BlockPos(centerX, padY, centerZ));

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                double angle = Math.atan2(dz, dx);
                double wobble = 0.6 * Math.sin(2 * angle + phaseA) + 0.4 * Math.sin(3 * angle + phaseB);
                double outer = radius * (1.0 + WOBBLE * wobble);
                if (distance > outer) {
                    continue;
                }
                double inner = outer * PLATEAU;

                int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int target;
                boolean onPlateau = distance <= inner;
                if (onPlateau) {
                    target = padY;
                } else {
                    double t = Mth.clamp((distance - inner) / Math.max(1.0, outer - inner), 0.0, 1.0);
                    double smooth = t * t * (3 - 2 * t);
                    target = (int) Math.round(padY + (natural - padY) * smooth);
                }

                int clearTop = Math.min(onPlateau ? padY + CLEARANCE : Math.max(natural, target) + 4, ceiling);
                clearTop = Math.max(clearTop, Math.min(Math.max(natural, target) + 4, ceiling));
                for (int y = clearTop; y > target; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, 2);
                    }
                }
                level.setBlock(new BlockPos(x, target, z), onPlateau ? palette.plateau : palette.slopeTop, 2);
                for (int depth = 1; depth <= FOUNDATION_DEPTH; depth++) {
                    BlockPos below = new BlockPos(x, target - depth, z);
                    BlockState state = level.getBlockState(below);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()) {
                        level.setBlock(below, palette.fill, 2);
                    } else {
                        break;
                    }
                }
            }
        }
    }
}