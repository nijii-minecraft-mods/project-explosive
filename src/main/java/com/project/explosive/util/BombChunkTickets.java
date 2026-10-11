package com.project.explosive.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

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

    private BombChunkTickets() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BombChunkTickets::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(BombChunkTickets::onServerStopped);
    }

    public static void forceAround(ServerWorld level, Entity owner, int radius) {
        ChunkPos center = new ChunkPos(owner.getBlockPos());
        BombChunkTickets.Area prev = ENTITY_AREAS.get(owner.getUuid());
        if (prev == null || prev.cx != center.x || prev.cz != center.z || prev.radius != radius) {
            if (prev != null) {
                level.getChunkManager().removeTicket(BOMB_ENTITY_TICKET, new ChunkPos(prev.cx, prev.cz), prev.radius, owner.getUuid());
            }

            level.getChunkManager().addTicket(BOMB_ENTITY_TICKET, center, radius, owner.getUuid());
            ENTITY_AREAS.put(owner.getUuid(), new BombChunkTickets.Area(center.x, center.z, radius));
        }
    }

    public static void release(ServerWorld level, Entity owner) {
        BombChunkTickets.Area prev = ENTITY_AREAS.remove(owner.getUuid());
        if (prev != null) {
            level.getChunkManager().removeTicket(BOMB_ENTITY_TICKET, new ChunkPos(prev.cx, prev.cz), prev.radius, owner.getUuid());
        }
    }

    public static void forceTemporary(ServerWorld level, BlockPos center, int radius, int ticks) {
        ChunkPos c = new ChunkPos(center);
        level.getChunkManager().addTicket(BOMB_POS_TICKET, c, radius, center);
        TEMP_AREAS.add(new BombChunkTickets.TempArea(level, center, c.x, c.z, radius, ticks));
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
    }

    private static void onServerStopped(MinecraftServer server) {
        ENTITY_AREAS.clear();
        TEMP_AREAS.clear();
    }

    private record Area(int cx, int cz, int radius) {
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
