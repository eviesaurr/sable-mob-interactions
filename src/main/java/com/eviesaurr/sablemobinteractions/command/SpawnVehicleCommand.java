package com.eviesaurr.sablemobinteractions.command;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.driving.PilotController;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.CustomCars;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleStructureGenerator;
import com.eviesaurr.sablemobinteractions.worldgen.vehicle.VehicleTierManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public final class SpawnVehicleCommand {

    private SpawnVehicleCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sablemobinteractions")
                .then(Commands.literal("vehicles")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            for (String line : VehicleStructureGenerator.describe(ctx.getSource().getLevel())) {
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("customcars")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            for (String line : CustomCars.rescanAndDescribe(ctx.getSource().getServer(), VehicleTierManager.getTiers().keySet())) {
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("clearvehicles")
                        .requires(source -> source.hasPermission(4))
                        .executes(ctx -> {
                            int purged = 0;
                            int queued = 0;
                            for (ServerLevel serverLevel : ctx.getSource().getServer().getAllLevels()) {
                                int[] result = VehicleStructureGenerator.clearRecords(serverLevel);
                                purged += result[0];
                                queued += result[1];
                            }
                            int purgedTotal = purged;
                            int queuedTotal = queued;
                            ctx.getSource().sendSuccess(() -> Component.literal("Purged " + purgedTotal + " enemy vehicle(s), "
                                    + queuedTotal + " more will be purged when their chunks load. Player builds are untouched"), true);
                            return purgedTotal;
                        }))
                .then(Commands.literal("debugdrive")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "enabled");
                                    PilotController.debug = on;
                                    ctx.getSource().sendSuccess(() -> Component.literal("Drive debug " + (on ? "on" : "off")), false);
                                    return 1;
                                }))));
    }
}