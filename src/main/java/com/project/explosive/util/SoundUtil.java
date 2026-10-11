package com.project.explosive.util;

import java.util.function.Supplier;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

public final class SoundUtil {
    private static final float JITTER = 0.04F;
    private static final float PITCH_MIN = 0.1F;
    private static final float PITCH_MAX = 2.0F;
    public static final double SPEED_OF_SOUND_MPS = 343.0;
    public static final double TICKS_PER_SECOND = 20.0;

    private SoundUtil() {
    }

    public static float jitter(World level, float pitch) {
        float p = pitch + (level.getRandom().nextFloat() - 0.5F) * 0.08F;
        if (p < PITCH_MIN) {
            return PITCH_MIN;
        } else {
            return p > PITCH_MAX ? PITCH_MAX : p;
        }
    }

    public static void playAt(World level, double x, double y, double z, SoundEvent sound, SoundCategory source, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, source, volume, jitter(level, pitch));
    }

    public static void playAt(World level, double x, double y, double z, Supplier<SoundEvent> sound, SoundCategory source, float volume, float pitch) {
        playAt(level, x, y, z, sound.get(), source, volume, pitch);
    }

    public static void playNotify(ServerPlayerEntity player, SoundEvent sound, SoundCategory source, float volume, float pitch) {
        player.playSoundToPlayer(sound, source, volume, jitter(player.getWorld(), pitch));
    }

    public static void playNotify(ServerPlayerEntity player, Supplier<SoundEvent> sound, SoundCategory source, float volume, float pitch) {
        playNotify(player, sound.get(), source, volume, pitch);
    }

    public static void playPositionedTo(ServerPlayerEntity player, double x, double y, double z, SoundEvent sound, SoundCategory source, float volume, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                RegistryEntry.of(sound),
                source,
                x,
                y,
                z,
                volume,
                jitter(player.getWorld(), pitch),
                player.getRandom().nextLong()
        ));
    }

    public static void playPositionedTo(ServerPlayerEntity player, double x, double y, double z, Supplier<SoundEvent> sound, SoundCategory source, float volume, float pitch) {
        playPositionedTo(player, x, y, z, sound.get(), source, volume, pitch);
    }

    public static long calculateDelayTicks(double distance) {
        return Math.round(distance * TICKS_PER_SECOND / SPEED_OF_SOUND_MPS);
    }

    public static void playDelayedToPlayer(ServerWorld level, ServerPlayerEntity player, double x, double y, double z, SoundEvent sound, SoundCategory source, float volume, float pitch) {
        double dist = Math.sqrt(player.squaredDistanceTo(x, y, z));
        long delayTicks = calculateDelayTicks(dist);
        if (delayTicks <= 0) {
            playPositionedTo(player, x, y, z, sound, source, volume, pitch);
        } else {
            DelayedTaskScheduler.schedule(level, delayTicks, () -> {
                if (player.isAlive() && player.getWorld() == level) {
                    playPositionedTo(player, x, y, z, sound, source, volume, pitch);
                }
            });
        }
    }

    public static void playDelayedToPlayer(ServerWorld level, ServerPlayerEntity player, double x, double y, double z, Supplier<SoundEvent> sound, SoundCategory source, float volume, float pitch) {
        playDelayedToPlayer(level, player, x, y, z, sound.get(), source, volume, pitch);
    }

    public static void playDelayed(ServerWorld level, double x, double y, double z, SoundEvent sound, SoundCategory source, float volume, float pitch) {
        for (ServerPlayerEntity player : level.getPlayers()) {
            playDelayedToPlayer(level, player, x, y, z, sound, source, volume, pitch);
        }
    }

    public static void playDelayed(ServerWorld level, double x, double y, double z, Supplier<SoundEvent> sound, SoundCategory source, float volume, float pitch) {
        playDelayed(level, x, y, z, sound.get(), source, volume, pitch);
    }
}
