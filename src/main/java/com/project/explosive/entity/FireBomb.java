package com.project.explosive.entity;

import com.project.explosive.particle.ModParticles;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.FirePropagationManager;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class FireBomb extends ThrownEntity implements PolymerEntity {
    protected int fallTime = 0;
    protected boolean hasExploded = false;

    public FireBomb(EntityType<? extends FireBomb> entityType, World world) {
        super(entityType, world);
    }

    public FireBomb(World world, LivingEntity owner) {
        super(ModEntities.RAW_FIRE_BOMB, owner, world);
    }

    public FireBomb(World world, double x, double y, double z) {
        super(ModEntities.RAW_FIRE_BOMB, x, y, z, world);
    }

    public FireBomb(World world, double x, double y, double z, boolean triggeredByBomb) {
        super(ModEntities.RAW_FIRE_BOMB, x, y, z, world);
    }

    @Override
    public EntityType<?> getPolymerEntityType(ServerPlayerEntity player) {
        return EntityType.TNT;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected double getGravity() {
        return 0.08;
    }

    @Override
    public void tick() {
        super.tick();
        this.fallTime++;

        if (!this.getWorld().isClient()) {
            ServerWorld serverLevel = (ServerWorld) this.getWorld();

            if (this.fallTime % 2 == 0) {
                Vec3d v = this.getVelocity();
                for (int i = 0; i < 4; i++) {
                    double ox = (serverLevel.getRandom().nextDouble() - 0.5) * 0.55;
                    double oy = (serverLevel.getRandom().nextDouble() - 0.5) * 0.55;
                    double oz = (serverLevel.getRandom().nextDouble() - 0.5) * 0.55;
                    double sizeVy = 0.06 + serverLevel.getRandom().nextDouble() * 0.07;
                    ParticleUtil.spawnLongDistanceParticles(
                            serverLevel, ModParticles.FIRE_FLAME,
                            this.getX() + ox, this.getY() + 0.2 + oy, this.getZ() + oz,
                            1, -v.x * 0.08, sizeVy, -v.z * 0.08
                    );
                }

                ParticleUtil.spawnLongDistanceParticles(
                        serverLevel, ModParticles.FIRE_SMOKE,
                        this.getX() - v.x * 0.35, this.getY() + 0.2 - v.y * 0.35, this.getZ() - v.z * 0.35,
                        1, 0.02, 0.05, 0.02
                );

                for (int i = 0; i < 2; i++) {
                    ParticleUtil.spawnLongDistanceParticles(
                            serverLevel, ModParticles.EMBER,
                            this.getX(), this.getY() + 0.2, this.getZ(),
                            1, (serverLevel.getRandom().nextDouble() - 0.5) * 0.25, 0.1, (serverLevel.getRandom().nextDouble() - 0.5) * 0.25
                    );
                }
            }

            if (this.fallTime > 200 && !this.hasExploded) {
                this.explode();
            }

            if (!this.hasExploded && this.isOnGround()) {
                this.explode();
            }
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient() && !this.hasExploded) {
            this.explode();
        }
    }

    private void explode() {
        if (this.hasExploded) {
            return;
        }

        this.hasExploded = true;
        if (this.getWorld() instanceof ServerWorld serverLevel) {
            double bx = this.getX();
            double by = this.getY();
            double bz = this.getZ();

            // Sounds: ModSounds.FIRE_BOMB_EXPLOSION and vanilla sounds
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.NEUTRAL, 1.5F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.NEUTRAL, 2.0F, 0.6F);
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.NEUTRAL, 2.0F, 1.0F);
            SoundUtil.playDelayed(serverLevel, bx, by, bz, ModSounds.FIRE_BOMB_EXPLOSION, SoundCategory.BLOCKS, 3.5F, 1.0F);

            // Detonates with fire spread (FirePropagationManager)
            FirePropagationManager.getInstance().startFirePropagation(serverLevel, bx, by, bz, 28, 6.0);

            // Ignites ground in radius
            igniteGroundInRadius(serverLevel, bx, by, bz, 7);

            // Bursts flame particles
            burstFlameParticles(serverLevel, bx, by, bz);

            // Ignites nearby entities
            igniteNearbyEntities(serverLevel, bx, by, bz, 8.0);
        }

        this.discard();
    }

    private void igniteGroundInRadius(ServerWorld serverLevel, double cx, double cy, double cz, int radius) {
        BlockPos center = BlockPos.ofFloored(cx, cy, cz);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    int wx = center.getX() + dx;
                    int wz = center.getZ() + dz;
                    int topY = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, wx, wz);
                    BlockPos groundPos = new BlockPos(wx, topY - 1, wz);
                    BlockPos firePos = new BlockPos(wx, topY, wz);

                    BlockState groundState = serverLevel.getBlockState(groundPos);
                    BlockState fireState = serverLevel.getBlockState(firePos);

                    if (fireState.isAir() && (groundState.isSolidBlock(serverLevel, groundPos) || groundState.isIn(BlockTags.LOGS) || groundState.isIn(BlockTags.LEAVES))) {
                        if (serverLevel.getRandom().nextFloat() < 0.65F) {
                            serverLevel.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 3);
                        }
                    }
                }
            }
        }
    }

    private void burstFlameParticles(ServerWorld serverLevel, double cx, double cy, double cz) {
        // Flash
        serverLevel.spawnParticles(ParticleTypes.FLASH, cx, cy + 0.5, cz, 4, 0.2, 0.2, 0.2, 0.0);

        // Sparks
        for (int i = 0; i < 45; i++) {
            double theta = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * serverLevel.getRandom().nextDouble() - 1.0);
            double speed = 1.2 + serverLevel.getRandom().nextDouble() * 1.8;
            double vx = Math.sin(phi) * Math.cos(theta) * speed;
            double vy = Math.sin(phi) * Math.sin(theta) * speed * 0.6 + 0.3;
            double vz = Math.cos(phi) * speed;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SPARK, cx, cy + 0.4, cz, 1, vx, vy, vz);
        }

        // Flames burst
        for (int i = 0; i < 80; i++) {
            double angle = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = serverLevel.getRandom().nextDouble() * Math.PI;
            double speed = 0.5 + serverLevel.getRandom().nextDouble() * 1.8;
            double vx = Math.sin(phi) * Math.cos(angle) * speed;
            double vy = Math.cos(phi) * speed;
            double vz = Math.sin(phi) * Math.sin(angle) * speed;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_FLAME, cx, cy, cz, 1, vx, vy, vz);
            if (i % 2 == 0) {
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.EMBER, cx, cy, cz, 1, vx * 0.8, vy * 0.8, vz * 0.8);
            }
        }

        // Ring of fire
        int ringCount = 48;
        for (int i = 0; i < ringCount; i++) {
            double a = (Math.PI * 2) * (double) i / (double) ringCount;
            double rSpeed = 1.4 + serverLevel.getRandom().nextDouble() * 0.6;
            double rx = Math.cos(a) * rSpeed;
            double rz = Math.sin(a) * rSpeed;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_FLAME, cx, cy + 0.2, cz, 1, rx, 0.1, rz);
        }

        // Smoke column
        for (int i = 0; i < 30; i++) {
            double a = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double rr = serverLevel.getRandom().nextDouble() * 1.5;
            double px = cx + Math.cos(a) * rr;
            double pz = cz + Math.sin(a) * rr;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_SMOKE, px, cy + 0.5, pz, 1, 0.0, 0.25, 0.0);
        }
    }

    private void igniteNearbyEntities(ServerWorld serverLevel, double cx, double cy, double cz, double radius) {
        Box box = new Box(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        for (LivingEntity target : serverLevel.getEntitiesByClass(LivingEntity.class, box, e -> true)) {
            double dist = Math.sqrt(target.squaredDistanceTo(cx, cy, cz));
            if (dist <= radius) {
                float prox = (float) (1.0 - dist / radius);
                target.setFireTicks((int) (160 * prox + 60));
                target.damage(serverLevel.getDamageSources().onFire(), 5.0F + prox * 10.0F);
            }
        }
    }
}
