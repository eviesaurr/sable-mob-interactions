package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public class ForcedNodePlacement {

    private static volatile boolean forced = false;

    public static boolean isForced() {
        return forced;
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        if (input.startsWith("place structure")) {
            forced = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        forced = false;
    }
}