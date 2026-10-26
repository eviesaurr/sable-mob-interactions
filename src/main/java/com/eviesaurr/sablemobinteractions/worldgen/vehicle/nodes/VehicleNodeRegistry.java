package com.eviesaurr.sablemobinteractions.worldgen.vehicle.nodes;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class VehicleNodeRegistry {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, SableMobInteractions.MOD_ID);

    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, SableMobInteractions.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<VehicleNodeStructure>> VEHICLE_NODE =
            STRUCTURE_TYPES.register("vehicle_node", VehicleNodeRegistry::nodeType);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> VEHICLE_NODE_PIECE =
            PIECE_TYPES.register("vehicle_node", VehicleNodeRegistry::nodePieceType);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> VEHICLE_NODE_PAD_PIECE =
            PIECE_TYPES.register("vehicle_node_pad", VehicleNodeRegistry::padPieceType);

    private static StructurePieceType padPieceType() {
        return VehicleNodePadPiece::new;
    }

    private static StructureType<VehicleNodeStructure> nodeType() {
        return () -> VehicleNodeStructure.CODEC;
    }

    private static StructurePieceType nodePieceType() {
        return VehicleNodePiece::new;
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECE_TYPES.register(modEventBus);
    }
}