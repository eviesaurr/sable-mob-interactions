package com.eviesaurr.sablemobinteractions;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, SableMobInteractions.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> MOB_TRANSMITTER =
            ITEMS.register("mob_transmitter", () -> new BlockItem(ModBlocks.MOB_TRANSMITTER.get(), new Item.Properties()));
}