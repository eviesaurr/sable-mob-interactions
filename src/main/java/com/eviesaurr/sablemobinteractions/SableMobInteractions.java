package com.eviesaurr.sablemobinteractions;

import com.eviesaurr.sablemobinteractions.config.Config;
import com.eviesaurr.sablemobinteractions.entity.SubLevelDummyTargetEntity;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.nodes.VehicleNodeRegistry;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(SableMobInteractions.MOD_ID)
public class SableMobInteractions {

    public static final String MOD_ID = "sablemobinteractions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SableMobInteractions(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("Sable Mob Interactions initializing");
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        VehicleNodeRegistry.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addToCreativeTabs);
    }

    private void addToCreativeTabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.MOB_TRANSMITTER.get());
        }
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.TARGET_DUMMY.get(), SubLevelDummyTargetEntity.createAttributes().build());
        event.put(ModEntityTypes.SUBLEVEL_PILOT_ZOMBIE.get(), net.minecraft.world.entity.monster.Zombie.createAttributes().build());
    }

}