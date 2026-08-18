package com.eviesaurr.sablemobinteractions.event;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.sublevel.BlockBreakTracker;
import com.eviesaurr.sablemobinteractions.sublevel.StuckProjectileCuller;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public class ProjectileImpactHandler {

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile().level() instanceof ServerLevel level)) return;
        if (!(event.getRayTraceResult() instanceof BlockHitResult blockHit)) return;

        var found = SubLevelInteraction.findAt(level, blockHit.getLocation());
        if (found.isEmpty()) {
            found = SubLevelInteraction.findAtRaw(level, blockHit.getBlockPos());
        }
        if (found.isEmpty()) return;

        BlockPos localPos = found.get().localPos();
        var subLevel = found.get().subLevel();

        var owner = event.getProjectile().getOwner();
        var source = owner != null ? owner : event.getProjectile();

        BlockBreakTracker.registerHit(subLevel, localPos, source);

        StuckProjectileCuller.markForCulling(event.getProjectile());
    }
}