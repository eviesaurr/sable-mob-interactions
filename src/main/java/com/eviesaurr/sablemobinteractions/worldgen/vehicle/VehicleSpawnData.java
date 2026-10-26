package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VehicleSpawnData extends SavedData {

    private static final String ID = "sablemobinteractions_vehicle_spawn_data";

    public static final class ActiveVehicle {
        public final ResourceLocation tier;
        public final ResourceLocation structure;
        public final String siteKey;
        public BlockPos pos;
        public int sizeX;
        public int sizeY;
        public int sizeZ;
        public boolean assembled;
        public int assembleAttempts;
        public UUID subLevelId;
        public int unseenTicks;
        public UUID pilotId;
        public boolean pilotHandled;
        public int missingPasses;
        public int pilotAttempts;
        public boolean pilotDead;
        public boolean purgeOnLoad;
        public int pilotLostPasses;

        public ActiveVehicle(ResourceLocation tier, ResourceLocation structure, String siteKey, BlockPos pos,
                             int sizeX, int sizeY, int sizeZ, boolean assembled, int assembleAttempts,
                             UUID subLevelId, int unseenTicks) {
            this.tier = tier;
            this.structure = structure;
            this.siteKey = siteKey;
            this.pos = pos;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.assembled = assembled;
            this.assembleAttempts = assembleAttempts;
            this.subLevelId = subLevelId;
            this.unseenTicks = unseenTicks;
        }
    }

    public static final class PendingSite {
        public final String key;
        public final ResourceLocation tier;
        public final int cellX;
        public final int cellZ;
        public final boolean node;
        public int remaining;

        public PendingSite(String key, ResourceLocation tier, int cellX, int cellZ, boolean node, int remaining) {
            this.key = key;
            this.tier = tier;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.node = node;
            this.remaining = remaining;
        }
    }

    public static final class Node {
        public final ResourceLocation tier;
        public final BlockPos pos;

        public Node(ResourceLocation tier, BlockPos pos) {
            this.tier = tier;
            this.pos = pos;
        }

        public String key() {
            return "node|" + tier + "|" + pos.getX() + "|" + pos.getZ();
        }
    }

    public final List<ActiveVehicle> active = new ArrayList<>();
    public final Map<String, PendingSite> pending = new LinkedHashMap<>();
    public final List<Node> nodes = new ArrayList<>();

    public static VehicleSpawnData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VehicleSpawnData::new, VehicleSpawnData::load, null),
                ID);
    }

    public int countForSite(String siteKey) {
        int count = 0;
        for (ActiveVehicle v : active) {
            if (v.siteKey.equals(siteKey)) {
                count++;
            }
        }
        return count;
    }

    public boolean addNode(ResourceLocation tier, BlockPos pos) {
        Node candidate = new Node(tier, pos);
        for (Node existing : nodes) {
            if (existing.key().equals(candidate.key())) {
                return false;
            }
        }
        nodes.add(candidate);
        setDirty();
        return true;
    }

    public static VehicleSpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        VehicleSpawnData data = new VehicleSpawnData();

        ListTag list = tag.getList("Active", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation tier = ResourceLocation.parse(c.getString("Tier"));
            ResourceLocation structure = ResourceLocation.parse(c.getString("Structure"));
            String siteKey = c.getString("SiteKey");
            BlockPos pos = new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z"));
            UUID subLevelId = c.contains("SubLevel") ? UUID.fromString(c.getString("SubLevel")) : null;
            ActiveVehicle vehicle = new ActiveVehicle(tier, structure, siteKey, pos,
                    c.getInt("SizeX"), c.getInt("SizeY"), c.getInt("SizeZ"),
                    c.getBoolean("Assembled"), c.getInt("AssembleAttempts"),
                    subLevelId, c.getInt("UnseenTicks"));
            vehicle.pilotId = c.contains("Pilot") ? UUID.fromString(c.getString("Pilot")) : null;
            vehicle.pilotHandled = !c.contains("PilotHandled") || c.getBoolean("PilotHandled");
            vehicle.pilotDead = c.getBoolean("PilotDead");
            vehicle.purgeOnLoad = c.getBoolean("PurgeOnLoad");
            data.active.add(vehicle);
        }

        ListTag pendingList = tag.getList("Pending", 10);
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag c = pendingList.getCompound(i);
            String key = c.getString("Key");
            data.pending.put(key, new PendingSite(key, ResourceLocation.parse(c.getString("Tier")),
                    c.getInt("CellX"), c.getInt("CellZ"), c.getBoolean("Node"), c.getInt("Remaining")));
        }

        ListTag nodeList = tag.getList("Nodes", 10);
        for (int i = 0; i < nodeList.size(); i++) {
            CompoundTag c = nodeList.getCompound(i);
            data.nodes.add(new Node(ResourceLocation.parse(c.getString("Tier")),
                    new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z"))));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ActiveVehicle v : active) {
            CompoundTag c = new CompoundTag();
            c.putString("Tier", v.tier.toString());
            c.putString("Structure", v.structure.toString());
            c.putString("SiteKey", v.siteKey);
            c.putInt("X", v.pos.getX());
            c.putInt("Y", v.pos.getY());
            c.putInt("Z", v.pos.getZ());
            c.putInt("SizeX", v.sizeX);
            c.putInt("SizeY", v.sizeY);
            c.putInt("SizeZ", v.sizeZ);
            c.putBoolean("Assembled", v.assembled);
            c.putInt("AssembleAttempts", v.assembleAttempts);
            if (v.subLevelId != null) {
                c.putString("SubLevel", v.subLevelId.toString());
            }
            c.putInt("UnseenTicks", v.unseenTicks);
            c.putBoolean("PilotHandled", v.pilotHandled);
            c.putBoolean("PilotDead", v.pilotDead);
            c.putBoolean("PurgeOnLoad", v.purgeOnLoad);
            if (v.pilotId != null) {
                c.putString("Pilot", v.pilotId.toString());
            }
            list.add(c);
        }
        tag.put("Active", list);

        ListTag pendingList = new ListTag();
        for (PendingSite p : pending.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Key", p.key);
            c.putString("Tier", p.tier.toString());
            c.putInt("CellX", p.cellX);
            c.putInt("CellZ", p.cellZ);
            c.putBoolean("Node", p.node);
            c.putInt("Remaining", p.remaining);
            pendingList.add(c);
        }
        tag.put("Pending", pendingList);

        ListTag nodeList = new ListTag();
        for (Node n : nodes) {
            CompoundTag c = new CompoundTag();
            c.putString("Tier", n.tier.toString());
            c.putInt("X", n.pos.getX());
            c.putInt("Y", n.pos.getY());
            c.putInt("Z", n.pos.getZ());
            nodeList.add(c);
        }
        tag.put("Nodes", nodeList);

        return tag;
    }
}