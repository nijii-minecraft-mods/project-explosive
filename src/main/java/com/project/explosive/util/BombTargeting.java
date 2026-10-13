package com.project.explosive.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

public final class BombTargeting {
    private static final double STEP = 4.0;
    private static final double MAX_TARGETING_DISTANCE = 384.0;

    private BombTargeting() {
    }

    @Nullable
    public static BlockPos findGroundTarget(ServerWorld level, PlayerEntity player, double maxDistance) {
        Vec3d origin = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        double safeDistance = Math.min(maxDistance, MAX_TARGETING_DISTANCE);
        int steps = (int) Math.ceil(safeDistance / STEP);
        int minY = level.getBottomY();
        int maxY = level.getTopY();

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

            // Only check loaded chunks - NEVER freeze main server thread with synchronous worldgen
            if (!level.isChunkLoaded(cx, cz)) {
                break;
            }

            int groundY = level.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (y <= (double) groundY) {
                return new BlockPos(bx, groundY, bz);
            }
        }

        return null;
    }
}
