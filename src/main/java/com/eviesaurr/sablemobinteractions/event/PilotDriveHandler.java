package com.eviesaurr.sablemobinteractions.event;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.driving.PilotController;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleStructureGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public class PilotDriveHandler {

    private static final Map<Mob, PilotController> CONTROLLERS = new WeakHashMap<>();

    private static boolean isPilot(Entity entity) {
        return entity instanceof Mob && entity.getPersistentData().contains(PilotController.TIER_KEY);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!isPilot(mob)) return;
        CONTROLLERS.computeIfAbsent(mob, PilotController::new).tick(level);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && isPilot(mob)) {
            if (mob.level() instanceof ServerLevel level) {
                VehicleStructureGenerator.onPilotDeath(level, mob.getUUID());
            }
            PilotController controller = CONTROLLERS.remove(mob);
            if (controller != null) {
                controller.stop();
            }
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob && isPilot(mob)) {
            PilotController controller = CONTROLLERS.remove(mob);
            if (controller != null) {
                controller.stop();
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!isPilot(event.getEntity())) return;
        var source = event.getSource();
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.CRAMMING)) {
            event.setCanceled(true);
        }
    }
}