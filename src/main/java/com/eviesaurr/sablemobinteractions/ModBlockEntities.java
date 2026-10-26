package com.eviesaurr.sablemobinteractions;

import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SableMobInteractions.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobTransmitterBlockEntity>> MOB_TRANSMITTER =
            BLOCK_ENTITY_TYPES.register("mob_transmitter",
                    () -> new BlockEntityType<>(MobTransmitterBlockEntity::new, java.util.Set.of(ModBlocks.MOB_TRANSMITTER.get()), null));
}