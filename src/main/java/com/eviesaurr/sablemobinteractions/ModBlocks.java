package com.eviesaurr.sablemobinteractions;

import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SableMobInteractions.MOD_ID);

    public static final DeferredBlock<MobTransmitterBlock> MOB_TRANSMITTER = BLOCKS.register("mob_transmitter",
            () -> new MobTransmitterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .noOcclusion()));
}