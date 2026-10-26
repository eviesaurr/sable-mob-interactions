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

    public static double getHitMultiplier(ServerLevel level, BlockState state, BlockPos pos) {
        var properties = BlockArmorPropertiesHandler.getProperties(state);
        long start = System.nanoTime();
        double hardness = properties.hardness(level, state, pos, false);
        double toughness = properties.toughness(level, state, pos, false);
        long elapsedMicros = (System.nanoTime() - start) / 1000;
        if (elapsedMicros > 500) {
            com.eviesaurr.sablemobinteractions.SableMobInteractions.LOGGER.warn(
                    "[PERF] hardness/toughness lookup for {} took {}µs", state.getBlock(), elapsedMicros);
        }
        return 1.0 + hardness + toughness;
    }
}
