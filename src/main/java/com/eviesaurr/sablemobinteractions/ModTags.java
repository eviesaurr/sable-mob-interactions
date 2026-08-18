package com.eviesaurr.sablemobinteractions;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModTags {
    public static final TagKey<Block> ENDERMAN_STEALABLE = TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SableMobInteractions.MOD_ID, "enderman_stealable")
    );

    public static final TagKey<Block> SHIP_ESSENTIAL_BLOCKS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SableMobInteractions.MOD_ID, "ship_essential_blocks")
    );
}
