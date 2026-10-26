package com.eviesaurr.sablemobinteractions.block;

import com.eviesaurr.sablemobinteractions.ModBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class MobTransmitterBlockEntity extends BlockEntity {

    public enum DriveInput { FORWARD, BACKWARD, LEFT, RIGHT }

    private static final int INPUT_TIMEOUT_TICKS = 10;

    private final Map<DriveInput, Couple<Frequency>> channelFrequencies = new EnumMap<>(DriveInput.class);
    private final Map<DriveInput, Channel> channels = new EnumMap<>(DriveInput.class);
    private Set<DriveInput> activeInputs = EnumSet.noneOf(DriveInput.class);
    private boolean registeredWithNetwork = false;
    private long lastInputTime;

    public MobTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOB_TRANSMITTER.get(), pos, state);
    }

    public void setChannelFrequency(DriveInput input, ItemStack first, ItemStack last) {
        channelFrequencies.put(input, Couple.create(Frequency.of(first), Frequency.of(last)));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (level != null && !level.isClientSide && registeredWithNetwork) {
            Channel existing = channels.remove(input);
            if (existing != null) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, existing);
            }
            Channel channel = new Channel(input);
            channels.put(input, channel);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, channel);
        }
    }

    public int copyFromController(ItemStack controller) {
        ItemStackHandler items = LinkedControllerItem.getFrequencyItems(controller);
        int configured = 0;
        for (DriveInput input : DriveInput.values()) {
            ItemStack first = items.getStackInSlot(input.ordinal() * 2);
            ItemStack last = items.getStackInSlot(input.ordinal() * 2 + 1);
            if (first.isEmpty() && last.isEmpty()) {
                continue;
            }
            setChannelFrequency(input, first, last);
            configured++;
        }
        return configured;
    }

    public Couple<Frequency> getChannelFrequency(DriveInput input) {
        return channelFrequencies.get(input);
    }

    public void setActiveInputs(Set<DriveInput> inputs) {
        if (level != null) {
            lastInputTime = level.getGameTime();
        }
        Set<DriveInput> next = inputs.isEmpty() ? EnumSet.noneOf(DriveInput.class) : EnumSet.copyOf(inputs);
        if (this.activeInputs.equals(next)) return;
        this.activeInputs = next;
        if (level != null && !level.isClientSide) {
            for (Channel channel : channels.values()) {
                channel.notifySignalChange();
            }
        }
    }

    public void serverTick() {
        if (level == null || activeInputs.isEmpty()) {
            return;
        }
        if (level.getGameTime() - lastInputTime > INPUT_TIMEOUT_TICKS) {
            setActiveInputs(Set.of());
        }
    }

    public Set<DriveInput> getActiveInputs() {
        return activeInputs;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerWithNetwork();
    }

    @Override
    public void setRemoved() {
        unregisterFromNetwork();
        super.setRemoved();
    }

    private void registerWithNetwork() {
        if (registeredWithNetwork || level == null || level.isClientSide) return;
        for (DriveInput input : DriveInput.values()) {
            if (!channelFrequencies.containsKey(input)) continue;
            Channel channel = new Channel(input);
            channels.put(input, channel);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, channel);
        }
        registeredWithNetwork = true;
    }

    private void unregisterFromNetwork() {
        if (!registeredWithNetwork || level == null) return;
        for (Channel channel : channels.values()) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, channel);
        }
        channels.clear();
        registeredWithNetwork = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag freqTag = new CompoundTag();
        for (Map.Entry<DriveInput, Couple<Frequency>> entry : channelFrequencies.entrySet()) {
            CompoundTag pair = new CompoundTag();
            pair.put("First", entry.getValue().getFirst().getStack().saveOptional(registries));
            pair.put("Last", entry.getValue().getSecond().getStack().saveOptional(registries));
            freqTag.put(entry.getKey().name(), pair);
        }
        tag.put("ChannelFrequencies", freqTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channelFrequencies.clear();
        if (tag.contains("ChannelFrequencies", Tag.TAG_COMPOUND)) {
            CompoundTag freqTag = tag.getCompound("ChannelFrequencies");
            for (DriveInput input : DriveInput.values()) {
                String key = input.name();
                if (!freqTag.contains(key, Tag.TAG_COMPOUND)) continue;
                CompoundTag pair = freqTag.getCompound(key);
                ItemStack first = ItemStack.parseOptional(registries, pair.getCompound("First"));
                ItemStack last = ItemStack.parseOptional(registries, pair.getCompound("Last"));
                channelFrequencies.put(input, Couple.create(Frequency.of(first), Frequency.of(last)));
            }
        }
        if (level != null && !level.isClientSide) {
            unregisterFromNetwork();
            registerWithNetwork();
        }
    }

    private final class Channel implements IRedstoneLinkable {
        private final DriveInput input;

        private Channel(DriveInput input) {
            this.input = input;
        }

        void notifySignalChange() {
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public int getTransmittedStrength() {
            return activeInputs.contains(input) ? 15 : 0;
        }

        @Override
        public void setReceivedStrength(int networkPower) {
        }

        @Override
        public BlockPos getLocation() {
            return MobTransmitterBlockEntity.this.worldPosition;
        }

        @Override
        public boolean isAlive() {
            return !MobTransmitterBlockEntity.this.isRemoved();
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return channelFrequencies.getOrDefault(input, Couple.create(Frequency.EMPTY, Frequency.EMPTY));
        }
    }
}