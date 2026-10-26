package com.eviesaurr.sablemobinteractions.command;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity;
import com.eviesaurr.sablemobinteractions.block.MobTransmitterBlockEntity.DriveInput;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public final class MobTransmitterCommand {
    private MobTransmitterCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuggestionProvider<CommandSourceStack> directionSuggestions = (ctx, builder) -> {
            for (DriveInput input : DriveInput.values()) {
                builder.suggest(input.name().toLowerCase());
            }
            return builder.buildFuture();
        };

        dispatcher.register(Commands.literal("sablemobinteractions")
                .then(Commands.literal("settransmitterfreq")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests(directionSuggestions)
                                        .executes(ctx -> execute(ctx.getSource(),
                                                BlockPosArgument.getBlockPos(ctx, "pos"),
                                                StringArgumentType.getString(ctx, "direction"))))))
                .then(Commands.literal("transmitterinfo")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> info(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"))))));
    }

    private static int info(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockPos lookupPos = SubLevelInteraction.findAt(level, Vec3.atCenterOf(pos))
                .map(found -> SubLevelInteraction.toRawBlockPos(found.subLevel(), found.localPos()))
                .orElse(pos);

        if (!(level.getBlockEntity(lookupPos) instanceof MobTransmitterBlockEntity transmitter)) {
            source.sendFailure(Component.literal("No Mob Transmitter block at " + pos.toShortString()
                    + " (resolved to " + lookupPos.toShortString() + ")"));
            return 0;
        }

        for (DriveInput input : DriveInput.values()) {
            var pair = transmitter.getChannelFrequency(input);
            String text = pair == null
                    ? "not set"
                    : pair.getFirst().getStack().getHoverName().getString() + " + " + pair.getSecond().getStack().getHoverName().getString();
            source.sendSuccess(() -> Component.literal(input.name().toLowerCase() + ": " + text), false);
        }
        return 1;
    }

    private static int execute(CommandSourceStack source, BlockPos pos, String directionArg) {
        DriveInput input;
        try {
            input = DriveInput.valueOf(directionArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown direction '" + directionArg
                    + "', expected forward, backward, left, or right"));
            return 0;
        }

        ServerLevel level = source.getLevel();

        BlockPos lookupPos = SubLevelInteraction.findAt(level, Vec3.atCenterOf(pos))
                .map(found -> SubLevelInteraction.toRawBlockPos(found.subLevel(), found.localPos()))
                .orElse(pos);

        if (!(level.getBlockEntity(lookupPos) instanceof MobTransmitterBlockEntity transmitter)) {
            source.sendFailure(Component.literal("No Mob Transmitter block at " + pos.toShortString()
                    + " (resolved to " + lookupPos.toShortString() + ")"));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run as a player, so it can read your held items"));
            return 0;
        }

        ItemStack first = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack last = player.getItemInHand(InteractionHand.OFF_HAND);
        transmitter.setChannelFrequency(input, first, last);

        source.sendSuccess(() -> Component.literal(
                "Set " + input.name().toLowerCase() + " frequency at " + pos.toShortString()
                        + " to (" + first.getHoverName().getString() + ", " + last.getHoverName().getString() + ")"), true);
        return 1;
    }
}