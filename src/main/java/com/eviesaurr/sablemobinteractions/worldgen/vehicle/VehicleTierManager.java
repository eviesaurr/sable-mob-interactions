package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public class VehicleTierManager extends SimpleJsonResourceReloadListener {

    public static final String FOLDER = "vehicle_tiers";

    private static Map<ResourceLocation, VehicleTier> tiers = Map.of();
    private static MinecraftServer server;

    public VehicleTierManager() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, VehicleTier> parsed = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            VehicleTier.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(error -> SableMobInteractions.LOGGER.error(
                            "Failed to parse vehicle tier {}: {}", entry.getKey(), error))
                    .ifPresent(tier -> parsed.put(entry.getKey(), tier));
        }
        tiers = Map.copyOf(parsed);
        if (server != null) {
            CustomCars.scan(server, tiers.keySet());
        }
    }

    public static Map<ResourceLocation, VehicleTier> getTiers() {
        return tiers;
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        server = event.getServer();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CustomCars.scan(event.getServer(), tiers.keySet());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
        CustomCars.clear();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VehicleTierManager());
    }
}