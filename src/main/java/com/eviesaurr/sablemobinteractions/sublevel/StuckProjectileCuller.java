package com.eviesaurr.sablemobinteractions.sublevel;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "sablemobinteractions")
public class StuckProjectileCuller {

    private static final int LIFETIME_TICKS = 100;

    private static final Map<Entity, Integer> tracked = new WeakHashMap<>();

    public static void markForCulling(Entity projectile) {
        tracked.put(projectile, LIFETIME_TICKS);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (tracked.isEmpty()) return;

        var iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Entity entity = entry.getKey();

            if (!entity.isAlive()) {
                iterator.remove();
                continue;
            }

            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                entity.discard();
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }
}