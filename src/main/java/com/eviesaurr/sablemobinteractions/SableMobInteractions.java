package com.eviesaurr.sablemobinteractions;

import com.eviesaurr.sablemobinteractions.config.Config;
import com.eviesaurr.sablemobinteractions.entity.SubLevelDummyTargetEntity;
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
        modEventBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.TARGET_DUMMY.get(), SubLevelDummyTargetEntity.createAttributes().build());
    }

}