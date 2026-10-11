package com.project.explosive.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RadiationSyncPacket(Map<UUID, Float> radiationLevels) implements CustomPayload {
    public static final CustomPayload.Id<RadiationSyncPacket> ID = new CustomPayload.Id<>(Identifier.of("projectexplosive", "radiation_sync"));

    public static final PacketCodec<PacketByteBuf, RadiationSyncPacket> CODEC = PacketCodec.ofStatic(
            (buf, packet) -> packet.encode(buf),
            RadiationSyncPacket::decode
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void encode(PacketByteBuf buf) {
        buf.writeInt(radiationLevels.size());
        for (Map.Entry<UUID, Float> entry : radiationLevels.entrySet()) {
            buf.writeUuid(entry.getKey());
            buf.writeFloat(entry.getValue());
        }
    }

    public static RadiationSyncPacket decode(PacketByteBuf buf) {
        int size = buf.readInt();
        Map<UUID, Float> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(buf.readUuid(), buf.readFloat());
        }
        return new RadiationSyncPacket(map);
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
    }
}
