package com.eviesaurr.sablemobinteractions;

import com.eviesaurr.sablemobinteractions.entity.SubLevelDummyTargetEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SableMobInteractions.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SubLevelDummyTargetEntity>> TARGET_DUMMY =
            ENTITY_TYPES.register("target_dummy", () -> EntityType.Builder.of(SubLevelDummyTargetEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .noSave()
                    .clientTrackingRange(0)
                    .build("target_dummy"));
}