package com.eviesaurr.sablemobinteractions;

import com.eviesaurr.sablemobinteractions.entity.SubLevelDummyTargetEntity;
import com.eviesaurr.sablemobinteractions.entity.SubLevelPilotZombie;
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

    public static final DeferredHolder<EntityType<?>, EntityType<SubLevelPilotZombie>> SUBLEVEL_PILOT_ZOMBIE =
            ENTITY_TYPES.register("sublevel_pilot_zombie", () -> EntityType.Builder.of(SubLevelPilotZombie::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.74f)
                    .passengerAttachments(2.0125f)
                    .ridingOffset(-0.7f)
                    .clientTrackingRange(64)
                    .build("sublevel_pilot_zombie"));
}