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
        double maxDistSq = (double) Config.maxParticleDistance * (double) Config.maxParticleDistance;

        for (ServerPlayerEntity player : level.getPlayers()) {
            if (!Config.enableLongDistanceParticles && player.squaredDistanceTo(x, y, z) > 64.0 * 64.0) {
                continue;
            }
            if (player.squaredDistanceTo(x, y, z) <= maxDistSq) {
                try {
                    level.spawnParticles(player, effect, true, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.01);
                } catch (Exception e) {
                    level.spawnParticles(player, ParticleTypes.SMOKE, true, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.01);
                }
            }
        }
    }

    public static void spawnUltraLongDistanceParticles(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double velocityX, double velocityY, double velocityZ
    ) {
        ParticleEffect effect = particleType != null ? particleType : ParticleTypes.EXPLOSION;
        int adjustedCount = Math.max(1, (int) ((float) count * Config.particleDensityMultiplier));

        for (ServerPlayerEntity player : level.getPlayers()) {
            try {
                level.spawnParticles(player, effect, true, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.01);
            } catch (Exception e) {
                level.spawnParticles(player, ParticleTypes.EXPLOSION, true, x, y, z, adjustedCount, velocityX, velocityY, velocityZ, 0.01);
            }
        }
    }

    public static void spawnLongDistanceParticlesRandomized(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double spread, double velocityY
    ) {
        for (int i = 0; i < count; i++) {
            double offsetX = (level.getRandom().nextDouble() - 0.5) * spread;
            double offsetZ = (level.getRandom().nextDouble() - 0.5) * spread;
            double offsetY = level.getRandom().nextDouble() * spread * 0.5;
            spawnLongDistanceParticles(level, particleType, x + offsetX, y + offsetY, z + offsetZ, 1, 0.0, velocityY, 0.0);
        }
    }

    public static void spawnLongDistanceComplexParticles(
            ServerWorld level, ParticleEffect particleType, double x, double y, double z, int count, double velocityX, double velocityY, double velocityZ
    ) {
        ParticleEffect effect = particleType != null ? particleType : ParticleTypes.POOF;
        int adjustedCount = Math.max(1, (int) ((float) count * Config.particleDensityMultiplier));
        double maxDistSq = (double) Config.maxParticleDistance * (double) Config.maxParticleDistance;

        for (ServerPlayerEntity player : level.getPlayers()) {
            if (player.squaredDistanceTo(x, y, z) <= maxDistSq) {
                try {
                    level.spawnParticles(player, effect, true, x, y, z, adjustedCount * 2, velocityX, velocityY, velocityZ, 0.01);
                } catch (Exception e) {
                    level.spawnParticles(player, ParticleTypes.POOF, true, x, y, z, adjustedCount * 2, velocityX, velocityY, velocityZ, 0.01);
                }
            }
        }
    }

    public static void spawnParticleRing(
            ServerWorld level, ParticleEffect particleType, double centerX, double centerY, double centerZ, double radius, int particleCount
    ) {
        int adjustedCount = Math.max(4, (int) ((float) particleCount * Config.particleDensityMultiplier));
        double angleStep = (Math.PI * 2) / (double) adjustedCount;

        for (int i = 0; i < adjustedCount; i++) {
            double angle = (double) i * angleStep;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double vx = Math.cos(angle) * 0.05;
            double vz = Math.sin(angle) * 0.05;
            spawnLongDistanceParticles(level, particleType, x, centerY, z, 1, vx, 0.02, vz);
        }
    }

    public static void spawnParticleSphere(
            ServerWorld level, ParticleEffect particleType, double centerX, double centerY, double centerZ, double radius, int particleCount
    ) {
        int adjustedCount = Math.max(8, (int) ((float) particleCount * Config.particleDensityMultiplier));

        for (int i = 0; i < adjustedCount; i++) {
            double theta = level.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * level.getRandom().nextDouble() - 1.0);
            double x = centerX + radius * Math.sin(phi) * Math.cos(theta);
            double y = centerY + radius * Math.sin(phi) * Math.sin(theta);
            double z = centerZ + radius * Math.cos(phi);
            double vx = (x - centerX) / radius * 0.1;
            double vy = (y - centerY) / radius * 0.1;
            double vz = (z - centerZ) / radius * 0.1;
            spawnLongDistanceParticles(level, particleType, x, y, z, 1, vx, vy, vz);
        }
    }

    public static void spawnMushroomCloud(ServerWorld level, double x, double y, double z) {
        spawnMushroomCloud(level, x, y, z, 1.0);
    }

    public static void spawnMushroomCloud(ServerWorld level, double x, double y, double z, double scale) {
        double density = (double) Config.particleDensityMultiplier;
        double s = Math.max(0.2, scale);

        // Flash and central fireball
        level.spawnParticles(ParticleTypes.FLASH, x, y + 2.0, z, 2, 0.0, 0.0, 0.0, 0.0);
        int flameCount = Math.max(20, (int) (60.0 * density));
        for (int i = 0; i < flameCount; i++) {
            double ang = level.getRandom().nextDouble() * 2.0 * Math.PI;
            double r = level.getRandom().nextDouble() * 8.0 * s;
            double px = x + Math.cos(ang) * r;
            double pz = z + Math.sin(ang) * r;
            double py = y + 1.0 + level.getRandom().nextDouble() * 5.0 * s;
            spawnLongDistanceParticles(level, ModParticles.FIRE_FLAME, px, py, pz, 1, 0.0, 0.15, 0.0);
            if (i % 2 == 0) {
                spawnLongDistanceParticles(level, ModParticles.EMBER, px, py, pz, 1, (level.getRandom().nextDouble() - 0.5) * 0.2, 0.2, (level.getRandom().nextDouble() - 0.5) * 0.2);
            }
        }

        // Stem: rising column
        double stemHeight = 60.0 * s;
        int stemCount = Math.max(50, (int) (180.0 * density));
        for (int i = 0; i < stemCount; i++) {
            double h = level.getRandom().nextDouble() * stemHeight;
            double radiusFactor = 1.0 + (h / stemHeight) * 0.5;
            double r = Math.sqrt(level.getRandom().nextDouble()) * 4.5 * s * radiusFactor;
            double ang = level.getRandom().nextDouble() * 2.0 * Math.PI;
            double px = x + Math.cos(ang) * r;
            double py = y + h;
            double pz = z + Math.sin(ang) * r;
            double vy = 0.5 + level.getRandom().nextDouble() * 0.5;
            spawnLongDistanceParticles(level, ModParticles.MUSHROOM_CLOUD_STEM, px, py, pz, 1, 0.0, vy, 0.0);
            if (i % 3 == 0) {
                spawnLongDistanceParticles(level, ModParticles.SMOKE_BIG, px, py, pz, 1, 0.0, vy * 0.8, 0.0);
            }
            if (i % 4 == 0) {
                spawnLongDistanceParticles(level, ModParticles.FIRE_SMOKE, px, py, pz, 1, 0.0, vy * 0.6, 0.0);
            }
        }

        // Mushroom Cap: expanding flattened dome at top of stem
        double capBaseY = y + stemHeight;
        double capRadius = 32.0 * s;
        int capCount = Math.max(80, (int) (300.0 * density));
        for (int i = 0; i < capCount; i++) {
            double theta = level.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = level.getRandom().nextDouble() * Math.PI * 0.5;
            double r = Math.cbrt(level.getRandom().nextDouble()) * capRadius;
            double px = x + r * Math.sin(phi) * Math.cos(theta);
            double py = capBaseY + (r * Math.cos(phi) * 0.55);
            double pz = z + r * Math.sin(phi) * Math.sin(theta);
            double vx = Math.sin(phi) * Math.cos(theta) * 0.15;
            double vy = Math.cos(phi) * 0.05;
            double vz = Math.sin(phi) * Math.sin(theta) * 0.15;
            spawnLongDistanceParticles(level, ModParticles.MUSHROOM_CLOUD_CAP, px, py, pz, 1, vx, vy, vz);
            if (i % 2 == 0) {
                spawnLongDistanceParticles(level, ModParticles.SMOKE3_BIG, px, py, pz, 1, vx * 0.8, vy, vz * 0.8);
            }
            if (i % 3 == 0) {
                spawnLongDistanceParticles(level, ModParticles.EXPLOSION_SMOKE, px, py, pz, 1, vx * 0.5, vy, vz * 0.5);
            }
        }

        // Base shockwave ring on ground
        int ringCount = Math.max(30, (int) (100.0 * density));
        double groundRadius = 24.0 * s;
        for (int i = 0; i < ringCount; i++) {
            double ang = level.getRandom().nextDouble() * 2.0 * Math.PI;
            double r = 4.0 + level.getRandom().nextDouble() * groundRadius;
            double px = x + Math.cos(ang) * r;
            double pz = z + Math.sin(ang) * r;
            double py = y + 0.3 + level.getRandom().nextDouble() * 2.0;
            double vx = Math.cos(ang) * 0.35;
            double vz = Math.sin(ang) * 0.35;
            spawnLongDistanceParticles(level, ModParticles.SMOKE_BIG_SHORT, px, py, pz, 1, vx, 0.05, vz);
            if (i % 2 == 0) {
                spawnLongDistanceParticles(level, ModParticles.SMOKE2, px, py, pz, 1, vx * 0.8, 0.02, vz * 0.8);
            }
        }
    }
}
