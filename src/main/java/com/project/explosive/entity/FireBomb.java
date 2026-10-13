package com.project.explosive.entity;

import com.project.explosive.particle.ModParticles;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.DelayedTaskScheduler;
import com.project.explosive.util.FirePropagationManager;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
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

    public FireBomb(World world, LivingEntity thrower) {
        super(ModEntities.RAW_FIRE_BOMB, thrower, world);
    }

    public FireBomb(World world, double x, double y, double z) {
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

        if (this.getWorld().isClient()) {
            return;
        }

        ServerWorld serverLevel = (ServerWorld) this.getWorld();

        // Fiery flight trail
        if (this.fallTime % 2 == 0) {
            Vec3d v = this.getVelocity();
            for (int i = 0; i < 3; i++) {
                double ox = (serverLevel.getRandom().nextDouble() - 0.5) * 0.4;
                double oy = (serverLevel.getRandom().nextDouble() - 0.5) * 0.4;
                double oz = (serverLevel.getRandom().nextDouble() - 0.5) * 0.4;
                ParticleUtil.spawnLongDistanceParticles(
                        serverLevel, ModParticles.FIRE_FLAME,
                        this.getX() + ox, this.getY() + 0.2 + oy, this.getZ() + oz,
                        1, -v.x * 0.1, 0.05, -v.z * 0.1
                );
            }
            serverLevel.spawnParticles(
                    ParticleTypes.LAVA,
                    this.getX(), this.getY() + 0.2, this.getZ(),
                    1, 0.05, 0.05, 0.05, 0.0
            );
            ParticleUtil.spawnLongDistanceParticles(
                    serverLevel, ModParticles.FIRE_SMOKE,
                    this.getX() - v.x * 0.3, this.getY() + 0.2 - v.y * 0.3, this.getZ() - v.z * 0.3,
                    1, 0.02, 0.04, 0.02
            );
        }

        if (this.fallTime > 200 && !this.hasExploded) {
            this.explode();
            return;
        }

        if (!this.hasExploded) {
            if (this.isOnGround()) {
                this.explode();
            } else {
                int groundY = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(this.getX()), MathHelper.floor(this.getZ()));
                if (this.getY() - (double) groundY <= 1.5) {
                    this.explode();
                }
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

            // Layered impactful audio
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.NEUTRAL, 2.0F, 0.9F);
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 2.8F, 0.65F);
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.BLOCKS, 2.5F, 1.0F);
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 2.5F, 0.8F);
            SoundUtil.playDelayed(serverLevel, bx, by, bz, ModSounds.FIRE_BOMB_EXPLOSION, SoundCategory.BLOCKS, 4.0F, 0.9F);

            // 1. Spectacular volcanic visuals
            burstFlameParticles(serverLevel, bx, by, bz);

            // 2. Blanket ground in real Minecraft fire
            igniteGroundInRadius(serverLevel, bx, by, bz, 8);

            // 3. Arcing secondary napalm splashes (spatters burning liquid outward)
            launchNapalmSplashes(serverLevel, bx, by, bz);

            // 4. Start thermodynamic wildfire simulation
            FirePropagationManager.getInstance().startFirePropagation(serverLevel, bx, by, bz, 30, 8.0);

            // 5. Heavy burning damage and knockback to entities
            igniteNearbyEntities(serverLevel, bx, by, bz, 9.0);
        }

        this.discard();
    }

    private void igniteGroundInRadius(ServerWorld serverLevel, double cx, double cy, double cz, int radius) {
        BlockPos center = BlockPos.ofFloored(cx, cy, cz);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq <= radius * radius) {
                    int wx = center.getX() + dx;
                    int wz = center.getZ() + dz;
                    int topY = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, wx, wz);
                    BlockPos groundPos = new BlockPos(wx, topY - 1, wz);
                    BlockPos firePos = new BlockPos(wx, topY, wz);

                    BlockState groundState = serverLevel.getBlockState(groundPos);
                    BlockState fireState = serverLevel.getBlockState(firePos);

                    // If non-solid foliage (grass, flowers), consume it directly with fire
                    if (groundState.getBlock() instanceof PlantBlock) {
                        serverLevel.setBlockState(groundPos, Blocks.FIRE.getDefaultState(), 3);
                        continue;
                    }

                    if (fireState.isAir() && (groundState.isSolidBlock(serverLevel, groundPos)
                            || groundState.isIn(BlockTags.LOGS)
                            || groundState.isIn(BlockTags.LEAVES)
                            || groundState.isIn(BlockTags.PLANKS))) {
                        // 75% coverage at center, tapering outward
                        double prob = 0.85 - (Math.sqrt(distSq) / (double) radius) * 0.35;
                        if (serverLevel.getRandom().nextDouble() < prob) {
                            if (Blocks.FIRE.getDefaultState().canPlaceAt(serverLevel, firePos)) {
                                serverLevel.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 3);
                            }
                        }
                    }
                }
            }
        }
    }

    private void launchNapalmSplashes(ServerWorld serverLevel, double cx, double cy, double cz) {
        int splashes = 12;
        for (int i = 0; i < splashes; i++) {
            double angle = (Math.PI * 2) * (double) i / (double) splashes + (serverLevel.getRandom().nextDouble() - 0.5) * 0.3;
            double distance = 6.0 + serverLevel.getRandom().nextDouble() * 10.0;
            long delay = (long) (2 + serverLevel.getRandom().nextInt(8));

            double targetX = cx + Math.cos(angle) * distance;
            double targetZ = cz + Math.sin(angle) * distance;

            DelayedTaskScheduler.schedule(serverLevel, delay, () -> {
                int groundY = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(targetX), MathHelper.floor(targetZ));
                BlockPos firePos = new BlockPos(MathHelper.floor(targetX), groundY, MathHelper.floor(targetZ));

                // Splash effects at landing point
                serverLevel.spawnParticles(ParticleTypes.LAVA, targetX, (double) groundY + 0.3, targetZ, 4, 0.3, 0.3, 0.3, 0.0);
                serverLevel.spawnParticles(ParticleTypes.FLAME, targetX, (double) groundY + 0.2, targetZ, 8, 0.4, 0.2, 0.4, 0.05);

                if (serverLevel.getBlockState(firePos).isAir()) {
                    BlockPos below = firePos.down();
                    if (!serverLevel.getBlockState(below).isAir() && Blocks.FIRE.getDefaultState().canPlaceAt(serverLevel, firePos)) {
                        serverLevel.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 3);
                    }
                }
            });
        }
    }

    private void burstFlameParticles(ServerWorld serverLevel, double cx, double cy, double cz) {
        // 1. Initial blinding flash
        serverLevel.spawnParticles(ParticleTypes.FLASH, cx, cy + 1.0, cz, 4, 0.3, 0.3, 0.3, 0.0);

        // 2. Exploding napalm droplets (Lava sparks)
        serverLevel.spawnParticles(ParticleTypes.LAVA, cx, cy + 1.0, cz, 40, 1.8, 1.4, 1.8, 0.15);

        // 3. Central billowing firestorm
        serverLevel.spawnParticles(ParticleTypes.FLAME, cx, cy + 0.8, cz, 75, 2.5, 1.8, 2.5, 0.12);

        // 4. Volcanic smoke plume rising 25 blocks into sky
        for (int h = 0; h < 20; h += 2) {
            double spread = 0.8 + (double) h * 0.15;
            serverLevel.spawnParticles(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    cx, cy + (double) h + 1.0, cz,
                    3, spread, 0.6, spread, 0.05
            );
        }

        // 5. Radial shockwave ring of fire on ground
        int ringCount = 36;
        for (int i = 0; i < ringCount; i++) {
            double a = (Math.PI * 2) * (double) i / (double) ringCount;
            double rSpeed = 1.6 + serverLevel.getRandom().nextDouble() * 0.5;
            double rx = Math.cos(a) * rSpeed;
            double rz = Math.sin(a) * rSpeed;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_FLAME, cx, cy + 0.3, cz, 2, rx, 0.1, rz);
        }
    }

    private void igniteNearbyEntities(ServerWorld serverLevel, double cx, double cy, double cz, double radius) {
        Box box = new Box(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
        for (LivingEntity target : serverLevel.getEntitiesByClass(LivingEntity.class, box, e -> true)) {
            double dist = Math.sqrt(target.squaredDistanceTo(cx, cy, cz));
            if (dist <= radius) {
                float prox = (float) (1.0 - dist / radius);
                target.setFireTicks((int) (200 * prox + 80));
                target.damage(serverLevel.getDamageSources().onFire(), 8.0F + prox * 14.0F);

                // Knockback outward
                Vec3d dir = target.getPos().subtract(cx, cy, cz).normalize();
                target.addVelocity(dir.x * (0.8 + prox * 1.2), 0.35 + prox * 0.3, dir.z * (0.8 + prox * 1.2));
            }
        }
    }
}
