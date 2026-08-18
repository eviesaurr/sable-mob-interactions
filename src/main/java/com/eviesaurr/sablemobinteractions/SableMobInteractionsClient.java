package com.eviesaurr.sablemobinteractions;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SableMobInteractions.MOD_ID, dist = Dist.CLIENT)

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID, value = Dist.CLIENT)
public class SableMobInteractionsClient {
    public SableMobInteractionsClient(ModContainer container) {

        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        SableMobInteractions.LOGGER.info("Sable Mob Interactions client setup complete");
    }
}