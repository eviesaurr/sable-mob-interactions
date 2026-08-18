package com.eviesaurr.sablemobinteractions.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;

public class BigCannonsCompat {

    private static final String MOD_ID = "createbigcannons";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Returns a hit-count multiplier reflecting Big Cannons' hardness+toughness for this block, or 1.0 if not present/not loaded. */
    public static double getHitMultiplier(ServerLevel level, BlockState state, BlockPos pos) {
        var properties = BlockArmorPropertiesHandler.getProperties(state);
        double hardness = properties.hardness(level, state, pos, false);
        double toughness = properties.toughness(level, state, pos, false);
        return 1.0 + hardness + toughness; // tune this formula to taste
    }
}