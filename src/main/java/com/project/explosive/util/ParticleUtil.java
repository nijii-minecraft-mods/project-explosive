package com.project.explosive.util;

import com.project.explosive.Config;
import com.project.explosive.particle.ModParticles;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ParticleUtil {
    private ParticleUtil() {
    }

    public static void spawnLongDistanceParticles(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double velocityX, double velocityY, double velocityZ
    ) {
        ParticleEffect effect = particleType != null ? particleType : ParticleTypes.CAMPFIRE_COSY_SMOKE;
        int adjustedCount = Math.max(1, (int) ((float) count * Config.particleDensityMultiplier));

        // Use ServerWorld force particle broadcast: sends 1 combined packet to tracking players within range
        level.spawnParticles(effect, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.02);
    }

    public static void spawnUltraLongDistanceParticles(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double velocityX, double velocityY, double velocityZ
    ) {
        ParticleEffect effect = particleType != null ? particleType : ParticleTypes.EXPLOSION;
        int adjustedCount = Math.max(1, (int) ((float) count * Config.particleDensityMultiplier));
        level.spawnParticles(effect, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.05);
    }

    public static void spawnLongDistanceParticlesRandomized(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double spread, double velocityY
    ) {
        ParticleEffect effect = particleType != null ? particleType : ParticleTypes.CAMPFIRE_COSY_SMOKE;
        int adjustedCount = Math.max(1, (int) ((float) count * Config.particleDensityMultiplier));
        level.spawnParticles(effect, x, y + spread * 0.25, z, adjustedCount, spread * 0.5, spread * 0.25, spread * 0.5, velocityY);
    }

    public static void spawnLongDistanceComplexParticles(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double velocityX, double velocityY, double velocityZ
    ) {
        ParticleEffect effect = particleType != null ? particleType : ParticleTypes.POOF;
        int adjustedCount = Math.max(1, (int) ((float) count * Config.particleDensityMultiplier));
        level.spawnParticles(effect, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.03);
    }

    public static void spawnParticleRing(
            ServerWorld level, ParticleEffect particleType, double centerX, double centerY, double centerZ, double radius, int particleCount
    ) {
        int points = Math.max(8, Math.min(24, (int) ((float) particleCount * 0.4F * Config.particleDensityMultiplier)));
        double angleStep = (Math.PI * 2) / (double) points;

        for (int i = 0; i < points; i++) {
            double angle = (double) i * angleStep;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double vx = Math.cos(angle) * 0.08;
            double vz = Math.sin(angle) * 0.08;
            spawnLongDistanceParticles(level, particleType, x, centerY, z, 2, vx, 0.02, vz);
        }
    }

    public static void spawnParticleSphere(
            ServerWorld level, ParticleEffect particleType, double centerX, double centerY, double centerZ, double radius, int particleCount
    ) {
        int adjustedCount = Math.max(8, (int) ((float) particleCount * Config.particleDensityMultiplier));
        level.spawnParticles(particleType != null ? particleType : ParticleTypes.EXPLOSION, centerX, centerY, centerZ, adjustedCount, radius * 0.5, radius * 0.5, radius * 0.5, 0.05);
    }

    public static void spawnMushroomCloud(ServerWorld level, double x, double y, double z) {
        spawnMushroomCloud(level, x, y, z, 1.0);
    }

    public static void spawnMushroomCloud(ServerWorld level, double x, double y, double z, double scale) {
        double density = (double) Config.particleDensityMultiplier;
        double s = Math.max(0.2, scale);

        // 1. Initial blinding flash and core fireball
        level.spawnParticles(ParticleTypes.FLASH, x, y + 2.0, z, 2, 0.0, 0.0, 0.0, 0.0);
        level.spawnParticles(ParticleTypes.EXPLOSION, x, y + 3.0, z, Math.max(4, (int) (8 * s)), 2.0 * s, 2.0 * s, 2.0 * s, 0.1);
        level.spawnParticles(ModParticles.FIRE_FLAME, x, y + 2.0, z, Math.max(15, (int) (40 * density * s)), 3.0 * s, 2.5 * s, 3.0 * s, 0.12);
        level.spawnParticles(ModParticles.EMBER, x, y + 3.0, z, Math.max(10, (int) (25 * density * s)), 2.5 * s, 3.0 * s, 2.5 * s, 0.15);

        // 2. Stem: vertical volumetric column in 12 ascending slices
        double stemHeight = 55.0 * s;
        int stemSlices = 14;
        for (int i = 0; i < stemSlices; i++) {
            double h = ((double) i / (double) stemSlices) * stemHeight;
            double radiusFactor = 1.0 + (h / stemHeight) * 0.6;
            double spread = 2.5 * s * radiusFactor;
            int sliceCount = Math.max(4, (int) (12 * density));

            level.spawnParticles(ModParticles.MUSHROOM_CLOUD_STEM, x, y + h, z, sliceCount, spread, 1.5, spread, 0.06);
            if (i % 2 == 0) {
                level.spawnParticles(ModParticles.SMOKE_BIG, x, y + h, z, sliceCount / 2, spread * 0.8, 1.0, spread * 0.8, 0.04);
            }
        }

        // 3. Mushroom Cap: expanding flattened dome at the top of the column
        double capBaseY = y + stemHeight;
        double capRadius = 28.0 * s;
        int ringSteps = 14;
        for (int i = 0; i < ringSteps; i++) {
            double ang = ((Math.PI * 2) * (double) i) / (double) ringSteps;
            double rx = x + Math.cos(ang) * (capRadius * 0.65);
            double rz = z + Math.sin(ang) * (capRadius * 0.65);
            level.spawnParticles(ModParticles.MUSHROOM_CLOUD_CAP, rx, capBaseY, rz, Math.max(6, (int) (14 * density)), capRadius * 0.25, 3.0 * s, capRadius * 0.25, 0.08);
            level.spawnParticles(ModParticles.EXPLOSION_SMOKE, rx, capBaseY + 2.0 * s, rz, Math.max(3, (int) (8 * density)), capRadius * 0.2, 2.0 * s, capRadius * 0.2, 0.05);
        }
        // Dome center volume
        level.spawnParticles(ModParticles.MUSHROOM_CLOUD_CAP, x, capBaseY + 4.0 * s, z, Math.max(12, (int) (30 * density)), capRadius * 0.4, 4.0 * s, capRadius * 0.4, 0.05);

        // 4. Ground shockwave ring
        double groundRadius = 22.0 * s;
        int groundRingSteps = 12;
        for (int i = 0; i < groundRingSteps; i++) {
            double ang = ((Math.PI * 2) * (double) i) / (double) groundRingSteps;
            double gx = x + Math.cos(ang) * groundRadius;
            double gz = z + Math.sin(ang) * groundRadius;
            level.spawnParticles(ModParticles.SMOKE_BIG_SHORT, gx, y + 0.5, gz, Math.max(3, (int) (6 * density)), 1.5, 0.4, 1.5, 0.05);
        }
    }
}
