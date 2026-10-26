package com.eviesaurr.sablemobinteractions.command;

import com.eviesaurr.sablemobinteractions.ModEntityTypes;
import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.entity.SubLevelPilotZombie;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import com.mojang.brigadier.CommandDispatcher;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public final class PilotSeatTestCommand {
    private PilotSeatTestCommand() {}

    private static final double SEARCH_RADIUS = 8.0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sablemobinteractions")
                .then(Commands.literal("testseat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> execute(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"))))));
    }

    private static int execute(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();

        Optional<SubLevelInteraction.Found> found = SubLevelInteraction.findAt(level, Vec3.atCenterOf(pos));
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("That position isn't inside a sub-level."));
            return 0;
        }
        SubLevel subLevel = found.get().subLevel();
        BlockPos centerLocal = found.get().localPos();

        SeatLocation seat = findNearestSeat(level, subLevel, centerLocal);
        if (seat == null) {
            source.sendFailure(Component.literal(
                    "No Create seat block found within " + (int) SEARCH_RADIUS + " blocks. Place one first."));
            return 0;
        }

        // No hub lookup here anymore: SubLevelPilotZombie finds its own MobTransmitterBlock
        // automatically once seated, no need for this command to pre-check it.

        Vec3 spawnWorldPos = SubLevelInteraction.localToWorld(subLevel, seat.localPos());

        SubLevelPilotZombie pilot = ModEntityTypes.SUBLEVEL_PILOT_ZOMBIE.get().create(level);
        if (pilot == null) {
            source.sendFailure(Component.literal("Failed to create the pilot mob"));
            return 0;
        }
        pilot.setPos(spawnWorldPos.x, spawnWorldPos.y, spawnWorldPos.z);
        if (!level.addFreshEntity(pilot)) {
            source.sendFailure(Component.literal("Failed to add the pilot mob to the level"));
            return 0;
        }

        // Same delay(1)-then-sit split as CAD's spawnTraders -> sitTraders.
        queueSitDown(pilot, seat.rawPos());

        source.sendSuccess(() -> Component.literal(
                "Pilot spawned, will sit at " + seat.rawPos().toShortString() + " in 1 tick."), true);
        return 1;
    }

    private record SeatLocation(BlockPos rawPos, BlockPos localPos) {}

    private static SeatLocation findNearestSeat(ServerLevel level, SubLevel subLevel, BlockPos centerLocalPos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        SeatLocation nearest = null;
        double nearestDistSqr = Double.MAX_VALUE;
        int radius = (int) SEARCH_RADIUS;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centerLocalPos.getX() + dx, centerLocalPos.getY() + dy, centerLocalPos.getZ() + dz);
                    BlockPos rawPos = SubLevelInteraction.toRawBlockPos(subLevel, cursor);
                    if (!(level.getBlockState(rawPos).getBlock() instanceof SeatBlock)) continue;
                    double distSqr = cursor.distSqr(centerLocalPos);
                    if (distSqr < nearestDistSqr) {
                        nearestDistSqr = distSqr;
                        nearest = new SeatLocation(rawPos.immutable(), cursor.immutable());
                    }
                }
            }
        }
        return nearest;
    }

    private record PendingSitDown(UUID mobId, BlockPos seatPos, int ticksRemaining) {}

    private static final List<PendingSitDown> PENDING = new ArrayList<>();

    private static void queueSitDown(SubLevelPilotZombie mob, BlockPos seatPos) {
        PENDING.add(new PendingSitDown(mob.getUUID(), seatPos, 1));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;

        List<PendingSitDown> stillWaiting = new ArrayList<>();
        List<PendingSitDown> ready = new ArrayList<>();
        for (PendingSitDown pending : PENDING) {
            if (pending.ticksRemaining() <= 0) {
                ready.add(pending);
            } else {
                stillWaiting.add(new PendingSitDown(pending.mobId(), pending.seatPos(), pending.ticksRemaining() - 1));
            }
        }
        PENDING.clear();
        PENDING.addAll(stillWaiting);

        for (PendingSitDown pending : ready) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                var entity = level.getEntity(pending.mobId());
                if (entity instanceof SubLevelPilotZombie pilot && pilot.isAlive()) {
                    SeatBlock.sitDown(level, pending.seatPos(), pilot);
                }
            }
        }
    }
}