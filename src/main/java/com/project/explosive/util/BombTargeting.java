package com.project.explosive.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;

public final class BombTargeting {
    private static final double STEP = 8.0;

    private BombTargeting() {
    }

    @Nullable
    public static BlockPos findGroundTarget(ServerWorld level, PlayerEntity player, double maxDistance) {
        Vec3d origin = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        int steps = (int) Math.ceil(maxDistance / STEP);
        long lastChunkKey = Long.MIN_VALUE;
        int minY = level.getBottomY() - 64;
        int maxY = level.getTopY() + 64;

        for (int i = 1; i <= steps; i++) {
            double d = (double) i * STEP;
            double x = origin.x + dir.x * d;
            double y = origin.y + dir.y * d;
            double z = origin.z + dir.z * d;
            if (y < (double) minY || y > (double) maxY) {
                break;
            }

            int bx = MathHelper.floor(x);
            int bz = MathHelper.floor(z);
            int cx = bx >> 4;
            int cz = bz >> 4;
            long chunkKey = (long) cx & 4294967295L | (long) cz << 32;
            if (chunkKey != lastChunkKey) {
                level.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, true);
                lastChunkKey = chunkKey;
            }

            int groundY = level.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (y <= (double) groundY) {
                return new BlockPos(bx, groundY, bz);
            }
        }

        return null;
    }
}
