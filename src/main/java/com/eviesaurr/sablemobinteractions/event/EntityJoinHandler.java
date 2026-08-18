package com.eviesaurr.sablemobinteractions.event;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.config.Config;
import com.eviesaurr.sablemobinteractions.goal.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.*;

import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public class EntityJoinHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof com.eviesaurr.sablemobinteractions.api.SubLevelAttacker attacker
                && event.getEntity() instanceof Mob mob) {
            mob.goalSelector.addGoal(2, new SubLevelCustomAttackerGoal(mob, attacker, Config.GENERIC_MONSTER_SEARCH_RADIUS.get()));
        } else if (event.getEntity() instanceof AbstractSkeleton skeleton) {
            skeleton.goalSelector.addGoal(2, new SubLevelRangedAttackGoal(skeleton,
                    Config.SKELETON_SEARCH_RADIUS.get(), Config.SKELETON_ATTACK_RANGE.get(), Config.SKELETON_FIRE_COOLDOWN_TICKS.get()));
        } else if (event.getEntity() instanceof EnderMan enderman) {
            if (Config.ALLOW_ENDERMAN_STEALING.get()) {
            enderman.goalSelector.addGoal(3, new SubLevelMeleeAttackGoal(enderman,
                    Config.GENERIC_MONSTER_SEARCH_RADIUS.get(), Config.ZOMBIE_ATTACK_RANGE.get()));
        } else if (event.getEntity() instanceof Creeper creeper) {
            if (Config.ALLOW_CREEPER_GRIEFING.get()) {
                creeper.goalSelector.addGoal(2, new SubLevelCreeperExplodeGoal(creeper,
                        Config.GENERIC_MONSTER_SEARCH_RADIUS.get(), 3.0));
            }
        } else if (event.getEntity() instanceof Guardian guardian) {
            guardian.goalSelector.addGoal(2, new SubLevelGuardianAttackGoal(guardian,
                    Config.GENERIC_MONSTER_SEARCH_RADIUS.get(), Config.SKELETON_ATTACK_RANGE.get()));
        } else if (event.getEntity() instanceof Drowned drowned) {
            if (drowned.getMainHandItem().is(Items.TRIDENT)) {
                drowned.goalSelector.addGoal(2, new SubLevelDrownedTridentAttackGoal(drowned,
                        Config.SKELETON_SEARCH_RADIUS.get(), Config.SKELETON_ATTACK_RANGE.get(), 60));
            } else {
                drowned.goalSelector.addGoal(2, new SubLevelMeleeAttackGoal(drowned,
                        Config.ZOMBIE_SEARCH_RADIUS.get(), Config.ZOMBIE_ATTACK_RANGE.get()));
            }
        } else if (event.getEntity() instanceof Zombie zombie) {
            zombie.goalSelector.addGoal(2, new SubLevelMeleeAttackGoal(zombie,
                    Config.ZOMBIE_SEARCH_RADIUS.get(), Config.ZOMBIE_ATTACK_RANGE.get()));
        } else if (event.getEntity() instanceof Monster monster) {
            monster.goalSelector.addGoal(2, new SubLevelNativeTargetGoal(monster,
                    Config.GENERIC_MONSTER_SEARCH_RADIUS.get()));
        }
    }
}
}