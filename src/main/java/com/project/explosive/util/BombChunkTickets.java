package com.project.explosive.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public final class BombChunkTickets {
    public static final ChunkTicketType<UUID> BOMB_ENTITY_TICKET = ChunkTicketType.create(
            "projectexplosive_bomb_entity",
            UUID::compareTo
    );
    public static final ChunkTicketType<BlockPos> BOMB_POS_TICKET = ChunkTicketType.create(
            "projectexplosive_bomb_pos",
            BlockPos::compareTo
    );

    private static final Map<UUID, BombChunkTickets.Area> ENTITY_AREAS = new HashMap<>();
    private static final List<BombChunkTickets.TempArea> TEMP_AREAS = new ArrayList<>();
    private static final int MAX_TEMP_AREAS = 100;
    private static final int MAX_CHUNK_RADIUS = 4;

    private BombChunkTickets() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BombChunkTickets::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(BombChunkTickets::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(BombChunkTickets::onServerStopped);

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (world instanceof ServerWorld serverWorld) {
                release(serverWorld, entity);
            }
        });
    }

    public static void forceAround(ServerWorld level, Entity owner, int radius) {
        if (level == null || owner == null) {
            return;
        }
        int clampedRadius = Math.max(1, Math.min(radius, MAX_CHUNK_RADIUS));
        ChunkPos center = new ChunkPos(owner.getBlockPos());
        BombChunkTickets.Area prev = ENTITY_AREAS.get(owner.getUuid());
        if (prev == null || prev.cx != center.x || prev.cz != center.z || prev.radius != clampedRadius || !prev.worldKey.equals(level.getRegistryKey())) {
            if (prev != null) {
                ServerWorld targetWorld = level;
                if (!prev.worldKey.equals(level.getRegistryKey()) && level.getServer() != null) {
                    ServerWorld otherWorld = level.getServer().getWorld(prev.worldKey);
                    if (otherWorld != null) {
                        targetWorld = otherWorld;
                    }
                }
                targetWorld.getChunkManager().removeTicket(BOMB_ENTITY_TICKET, new ChunkPos(prev.cx, prev.cz), prev.radius, owner.getUuid());
            }

            level.getChunkManager().addTicket(BOMB_ENTITY_TICKET, center, clampedRadius, owner.getUuid());
            ENTITY_AREAS.put(owner.getUuid(), new BombChunkTickets.Area(level.getRegistryKey(), center.x, center.z, clampedRadius));
        }
    }

    public static void release(ServerWorld level, Entity owner) {
        if (owner == null) {
            return;
        }
        BombChunkTickets.Area prev = ENTITY_AREAS.remove(owner.getUuid());
        if (prev != null) {
            ServerWorld targetWorld = level;
            if (level != null && !prev.worldKey.equals(level.getRegistryKey()) && level.getServer() != null) {
                ServerWorld otherWorld = level.getServer().getWorld(prev.worldKey);
                if (otherWorld != null) {
                    targetWorld = otherWorld;
                }
            }
            if (targetWorld != null) {
                targetWorld.getChunkManager().removeTicket(BOMB_ENTITY_TICKET, new ChunkPos(prev.cx, prev.cz), prev.radius, owner.getUuid());
            }
        }
    }

    public static void forceTemporary(ServerWorld level, BlockPos center, int radius, int ticks) {
        if (level == null || center == null) {
            return;
        }
        int clampedRadius = Math.max(1, Math.min(radius, MAX_CHUNK_RADIUS));
        int clampedTicks = Math.max(1, Math.min(ticks, 1200));
        ChunkPos c = new ChunkPos(center);

        if (TEMP_AREAS.size() >= MAX_TEMP_AREAS) {
            BombChunkTickets.TempArea oldest = TEMP_AREAS.remove(0);
            try {
                oldest.level.getChunkManager().removeTicket(BOMB_POS_TICKET, new ChunkPos(oldest.cx, oldest.cz), oldest.radius, oldest.owner);
            } catch (Exception ignored) {
            }
        }

        level.getChunkManager().addTicket(BOMB_POS_TICKET, c, clampedRadius, center);
        TEMP_AREAS.add(new BombChunkTickets.TempArea(level, center, c.x, c.z, clampedRadius, clampedTicks));
    }

    private static void onServerTick(MinecraftServer server) {
        if (!TEMP_AREAS.isEmpty()) {
            Iterator<BombChunkTickets.TempArea> it = TEMP_AREAS.iterator();
            while (it.hasNext()) {
                BombChunkTickets.TempArea t = it.next();
                if (--t.ticksLeft <= 0) {
                    t.level.getChunkManager().removeTicket(BOMB_POS_TICKET, new ChunkPos(t.cx, t.cz), t.radius, t.owner);
                    it.remove();
                }
            }
        }

        if (server.getTicks() % 20 == 0 && !ENTITY_AREAS.isEmpty()) {
            ENTITY_AREAS.entrySet().removeIf(entry -> {
                UUID uuid = entry.getKey();
                for (ServerWorld world : server.getWorlds()) {
                    Entity ent = world.getEntity(uuid);
                    if (ent != null && ent.isAlive() && !ent.isRemoved()) {
                        return false;
                    }
                }
                BombChunkTickets.Area area = entry.getValue();
                ServerWorld targetWorld = server.getWorld(area.worldKey);
                if (targetWorld != null) {
                    targetWorld.getChunkManager().removeTicket(BOMB_ENTITY_TICKET, new ChunkPos(area.cx(), area.cz()), area.radius(), uuid);
                }
                return true;
            });
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        for (Entry<UUID, BombChunkTickets.Area> entry : ENTITY_AREAS.entrySet()) {
            ServerWorld targetWorld = server.getWorld(entry.getValue().worldKey);
            if (targetWorld != null) {
                targetWorld.getChunkManager().removeTicket(BOMB_ENTITY_TICKET, new ChunkPos(entry.getValue().cx(), entry.getValue().cz()), entry.getValue().radius(), entry.getKey());
            }
        }
        for (BombChunkTickets.TempArea t : TEMP_AREAS) {
            try {
                t.level.getChunkManager().removeTicket(BOMB_POS_TICKET, new ChunkPos(t.cx, t.cz), t.radius, t.owner);
            } catch (Exception ignored) {
            }
        }
        ENTITY_AREAS.clear();
        TEMP_AREAS.clear();
    }

    private static void onServerStopped(MinecraftServer server) {
        ENTITY_AREAS.clear();
        TEMP_AREAS.clear();
    }

    private record Area(RegistryKey<World> worldKey, int cx, int cz, int radius) {
    }

    private static final class TempArea {
        final ServerWorld level;
        final BlockPos owner;
        final int cx;
        final int cz;
        final int radius;
        int ticksLeft;

        TempArea(ServerWorld level, BlockPos owner, int cx, int cz, int radius, int ticks) {
            this.level = level;
            this.owner = owner;
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.ticksLeft = ticks;
        }
    }
}
