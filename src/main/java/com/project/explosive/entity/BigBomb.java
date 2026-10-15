package com.project.explosive.entity;

import com.project.explosive.Config;
import com.project.explosive.block.ModBlocks;
import com.project.explosive.particle.ModParticles;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.BlastCrumble;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.CraterUtil;
import com.project.explosive.util.DelayedTaskScheduler;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
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

import java.util.HashSet;
import java.util.Set;

public class BigBomb extends ThrownEntity implements PolymerEntity {
    protected int fallTime = 0;
    protected boolean hasExploded = false;
    protected boolean launchSoundPlayed = false;
    protected boolean triggeredByBomb = false;
    protected float blastScale = 0.7F;

    private boolean shockwaveActive = false;
    private int shockwaveTicks = 0;
    private static final int MAX_SHOCKWAVE_AGE = 40;
    private BlockPos originPos;
    private final Set<Integer> entitiesHitByShockwave = new HashSet<>();

    public BigBomb(EntityType<? extends BigBomb> entityType, World world) {
        super(entityType, world);
    }

    public BigBomb(World world, LivingEntity owner) {
        super(ModEntities.RAW_BIG_BOMB, owner, world);
    }

    public BigBomb(World world, double x, double y, double z) {
        super(ModEntities.RAW_BIG_BOMB, x, y, z, world);
    }

    public BigBomb(World world, double x, double y, double z, boolean triggeredByBomb) {
        super(ModEntities.RAW_BIG_BOMB, x, y, z, world);
        this.triggeredByBomb = triggeredByBomb;
    }

    @Override
    public EntityType<?> getPolymerEntityType(ServerPlayerEntity player) {
        return EntityType.TNT;
    }

    public void setBlastScale(float s) {
        this.blastScale = MathHelper.clamp(s, 0.05F, 1.0F);
    }

    public float getBlastScale() {
        return this.blastScale;
    }

    public boolean wasTriggeredByBomb() {
        return this.triggeredByBomb;
    }

    public void setTriggeredByBomb(boolean triggered) {
        this.triggeredByBomb = triggered;
    }

    private int terrainBlastChunkRadius() {
        double maxBlocks = Math.min(32.0, 20.0 * (double) (this.blastScale / 0.7F));
        return Math.min(3, (int) Math.ceil(maxBlocks / 16.0) + 1);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected double getGravity() {
        return 0.1;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        tag.putBoolean("HasExploded", this.hasExploded);
        tag.putInt("FallTime", this.fallTime);
        tag.putBoolean("LaunchSoundPlayed", this.launchSoundPlayed);
        tag.putBoolean("TriggeredByBomb", this.triggeredByBomb);
        tag.putFloat("BlastScale", this.blastScale);
        tag.putBoolean("ShockwaveActive", this.shockwaveActive);
        tag.putInt("ShockwaveTicks", this.shockwaveTicks);
        if (this.originPos != null) {
            tag.putInt("OriginX", this.originPos.getX());
            tag.putInt("OriginY", this.originPos.getY());
            tag.putInt("OriginZ", this.originPos.getZ());
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        if (tag.contains("HasExploded")) {
            this.hasExploded = tag.getBoolean("HasExploded");
        }
        if (tag.contains("FallTime")) {
            this.fallTime = tag.getInt("FallTime");
        }
        if (tag.contains("LaunchSoundPlayed")) {
            this.launchSoundPlayed = tag.getBoolean("LaunchSoundPlayed");
        }
        if (tag.contains("TriggeredByBomb")) {
            this.triggeredByBomb = tag.getBoolean("TriggeredByBomb");
        }
        if (tag.contains("BlastScale")) {
            this.blastScale = tag.getFloat("BlastScale");
        }
        if (tag.contains("ShockwaveActive")) {
            this.shockwaveActive = tag.getBoolean("ShockwaveActive");
        }
        if (tag.contains("ShockwaveTicks")) {
            this.shockwaveTicks = tag.getInt("ShockwaveTicks");
        }
        if (tag.contains("OriginX")) {
            this.originPos = new BlockPos(tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
        }
        if (this.hasExploded) {
            this.setNoGravity(true);
            this.setVelocity(Vec3d.ZERO);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) {
            return;
        }

        ServerWorld serverLevel = (ServerWorld) this.getWorld();
        BombChunkTickets.forceAround(serverLevel, this, this.hasExploded ? this.terrainBlastChunkRadius() : 1);

        if (this.hasExploded) {
            this.killNearbyItems(serverLevel);
            if (this.shockwaveActive) {
                this.processShockwave(serverLevel);
            }
            if (!this.shockwaveActive || this.age > 1200) {
                this.discard();
            }
            return;
        }

        this.fallTime++;
        if (!this.launchSoundPlayed) {
            SoundUtil.playAt(this.getWorld(), this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.HOSTILE, 1.0F, 1.0F);
            SoundUtil.playAt(this.getWorld(), this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 0.9F, 0.8F);
            this.launchSoundPlayed = true;
        }

        if (this.fallTime % 10 == 0) {
            this.makeNearbyEntitiesFlee();
        }

        if (this.fallTime % 5 == 0) {
            ParticleUtil.spawnLongDistanceParticles(
                    serverLevel, ModParticles.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.0, 0.0, 0.0
            );
        }

        if (!this.isOnGround()) {
            this.checkForBombCollisions();
        }

        if (this.isOnGround() || this.fallTime > 600) {
            this.explode();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient() && !this.hasExploded) {
            this.explode();
        }
    }

    private void makeNearbyEntitiesFlee() {
        Box fleeBox = this.getBoundingBox().expand(18.0);
        for (LivingEntity living : this.getWorld().getEntitiesByClass(LivingEntity.class, fleeBox, e -> e.getId() != this.getId())) {
            Vec3d dir = living.getPos().subtract(this.getPos()).normalize().multiply(0.4);
            living.addVelocity(dir.x, 0.15, dir.z);
        }
    }

    private void checkForBombCollisions() {
        Box detectionBox = this.getBoundingBox().expand(2.0);
        for (Entity entity : this.getWorld().getOtherEntities(this, detectionBox)) {
            if (entity instanceof BigBomb && entity != this) {
                if (this.distanceTo(entity) < 2.0 && !this.hasExploded) {
                    this.explode();
                    ((BigBomb) entity).setTriggeredByBomb(true);
                }
            } else if (entity instanceof SmallBomb && entity != this) {
                if (this.distanceTo(entity) < 2.0 && !this.hasExploded) {
                    this.explode();
                    ((SmallBomb) entity).setTriggeredByBomb(true);
                }
            }
        }
    }

    protected void explode() {
        if (this.getWorld().isClient() || this.hasExploded) {
            return;
        }

        this.hasExploded = true;
        this.originPos = this.getBlockPos();
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);

        ServerWorld serverLevel = (ServerWorld) this.getWorld();
        double bx = this.getX();
        double by = this.getY();
        double bz = this.getZ();

        double craterScale = (double) (this.blastScale / 0.7F);

        // Wide devastating bunker-buster crater (wide saucer, not a deep vertical pit)
        CraterUtil.digCraterWithEjecta(serverLevel, bx, by, bz, Math.min(20.0 * craterScale, 26.0), 0.35, 6, 1.5);

        // Structural crumble
        BlastCrumble.blastAndCrumble(serverLevel, bx, by, bz, Math.min(24.0 * craterScale, 30.0));

        // High damage & explosion
        serverLevel.createExplosion(this, bx, by, bz, 22.0F * (float) craterScale, World.ExplosionSourceType.TNT);

        // High damage & violent knockback to living entities across distance
        applyDamage(serverLevel, bx, by, bz);

        // Sounds: ModSounds.BIG_BOMB_EXPLOSION and vanilla sounds
        SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.BLOCKS, 4.0F, 1.45F);
        SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 6.0F, 0.9F);
        SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.BLOCKS, 3.5F, 0.7F);

        SoundUtil.playDelayed(serverLevel, bx, by, bz, ModSounds.BIG_BOMB_EXPLOSION, SoundCategory.BLOCKS, 6.0F, 1.0F);
        SoundUtil.playDelayed(serverLevel, bx, by, bz, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 4.0F, 0.6F);

        DelayedTaskScheduler.schedule(serverLevel, 12L, () -> {
            SoundUtil.playAt(serverLevel, bx, by, bz, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 4.0F, 0.6F);
        });

        // Visual effects
        createExplosionParticles(serverLevel, bx, by, bz);
        spawnDebrisOnGround(serverLevel, 20, 6);
        killNearbyItems(serverLevel);

        this.shockwaveActive = true;
        this.shockwaveTicks = 0;
        this.entitiesHitByShockwave.clear();
    }

    private void applyDamage(ServerWorld serverLevel, double x, double y, double z) {
        double maxDist = 45.0 * (double) (this.blastScale / 0.7F);
        Box area = new Box(x - maxDist, y - 12.0, z - maxDist, x + maxDist, y + 24.0, z + maxDist);

        for (LivingEntity entity : serverLevel.getEntitiesByClass(LivingEntity.class, area, e -> true)) {
            double dist = Math.sqrt(entity.squaredDistanceTo(x, y, z));
            if (dist <= maxDist) {
                float factor = (float) (1.0 - dist / maxDist);
                float damage = Math.max(15.0F, factor * 70.0F * (this.blastScale / 0.7F));
                entity.damage(serverLevel.getDamageSources().explosion(this, this.getOwner()), damage);

                Vec3d dir = entity.getPos().subtract(x, y, z).normalize();
                if (dir.lengthSquared() < 0.001) {
                    dir = new Vec3d(0.0, 1.0, 0.0);
                }

                // Violent shockwave impulse (hurls mobs through the air!)
                double knockback = 2.8 * factor;
                entity.addVelocity(dir.x * knockback, 0.6 * factor + 0.35, dir.z * knockback);
                entity.velocityModified = true;

                if (entity instanceof ServerPlayerEntity playerTarget) {
                    playerTarget.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(playerTarget));
                }

                // Shellshock / Concussion
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, (int) (180 * factor), 0, false, false, true));
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, (int) (140 * factor), 1, false, false, true));
                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, (int) (140 * factor), 1, false, false, true));
            }
        }

        // Tinnitus ringing
        SoundUtil.playAt(serverLevel, x, y, z, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 2.0F, 1.8F);
    }

    private void createExplosionParticles(ServerWorld serverLevel, double cx, double cy, double cz) {
        int sparkCount = 40;
        for (int i = 0; i < sparkCount; i++) {
            double theta = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * serverLevel.getRandom().nextDouble() - 1.0);
            double speed = 2.0 + serverLevel.getRandom().nextDouble() * 2.0;
            double vx = Math.sin(phi) * Math.cos(theta) * speed;
            double vy = Math.sin(phi) * Math.sin(theta) * speed * 0.6 + 0.4;
            double vz = Math.cos(phi) * speed;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SPARK, cx, cy + 1.0, cz, 1, vx, vy, vz);
        }

        int smokeCount = 50;
        for (int i = 0; i < smokeCount; i++) {
            double theta = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * serverLevel.getRandom().nextDouble() - 1.0);
            double r = Math.cbrt(serverLevel.getRandom().nextDouble()) * 4.0;
            double x = cx + r * Math.sin(phi) * Math.cos(theta);
            double y = cy + r * Math.sin(phi) * Math.sin(theta) * 0.8 + 1.0;
            double z = cz + r * Math.cos(phi);
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.EXPLOSION_SMOKE, x, y, z, 1, (x - cx) * 0.3, (y - cy) * 0.4 + 0.2, (z - cz) * 0.3);
        }

        for (int i = 0; i < 30; i++) {
            double angle = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double dist = 1.0 + serverLevel.getRandom().nextDouble() * 6.0;
            double x = cx + Math.cos(angle) * dist;
            double z = cz + Math.sin(angle) * dist;
            ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMOKE3, x, cy + 0.5, z, 1, Math.cos(angle) * 0.3, 0.05, Math.sin(angle) * 0.3);
        }
    }

    private void processShockwave(ServerWorld serverLevel) {
        this.shockwaveTicks++;
        double currentRadius = (double) this.shockwaveTicks * 3.4;

        if (this.shockwaveTicks > MAX_SHOCKWAVE_AGE) {
            this.shockwaveActive = false;
            return;
        }

        double maxListenDistSq = (currentRadius + 64.0) * (currentRadius + 64.0);
        boolean playerNearby = false;
        for (ServerPlayerEntity player : serverLevel.getPlayers()) {
            if (player.squaredDistanceTo(this.originPos.getX(), this.originPos.getY(), this.originPos.getZ()) <= maxListenDistSq) {
                playerNearby = true;
                break;
            }
        }
        if (!playerNearby) {
            return;
        }

        if (this.shockwaveTicks % 2 == 0) {
            double cx = (double) this.originPos.getX() + 0.5;
            double cz = (double) this.originPos.getZ() + 0.5;
            int points = (int) MathHelper.clamp(currentRadius * 1.5, 10.0, 24.0);
            for (int i = 0; i < points; i++) {
                double ang = (Math.PI * 2) * (double) i / (double) points;
                double px = cx + Math.cos(ang) * currentRadius;
                double pz = cz + Math.sin(ang) * currentRadius;
                int fx = MathHelper.floor(px);
                int fz = MathHelper.floor(pz);
                if (!serverLevel.isChunkLoaded(fx >> 4, fz >> 4)) {
                    continue;
                }
                int py = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, fx, fz);
                ParticleUtil.spawnLongDistanceParticles(
                        serverLevel, ModParticles.SMOKE, px, (double) py + 0.3, pz, 1, 0.0, 0.1, 0.0
                );
            }
        }
    }

    private void spawnDebrisOnGround(ServerWorld serverLevel, int radius, int minRadius) {
        int debrisCount = radius * 2;
        BlockPos origin = this.originPos != null ? this.originPos : this.getBlockPos();

        for (int i = 0; i < debrisCount; i++) {
            double angle = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double distance = (double) minRadius + Math.sqrt(serverLevel.getRandom().nextDouble()) * (double) (radius - minRadius);
            int tx = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int tz = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!serverLevel.isChunkLoaded(tx >> 4, tz >> 4)) {
                continue;
            }
            int ty = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, tx, tz);
            BlockPos groundPos = new BlockPos(tx, ty, tz);
            BlockState aboveState = serverLevel.getBlockState(groundPos);
            if ((aboveState.isAir() || aboveState.isReplaceable()) && serverLevel.getRandom().nextFloat() < 0.7F) {
                serverLevel.setBlockState(groundPos, ModBlocks.DEBRIS.get().getDefaultState(), 18);
            }
        }
    }

    private void killNearbyItems(ServerWorld serverLevel) {
        Box area = this.getBoundingBox().expand(40.0);
        for (ItemEntity item : serverLevel.getEntitiesByClass(ItemEntity.class, area, e -> true)) {
            item.discard();
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.getWorld() instanceof ServerWorld serverLevel) {
            BombChunkTickets.release(serverLevel, this);
            if (reason.shouldDestroy() && this.hasExploded) {
                BombChunkTickets.forceTemporary(serverLevel, this.originPos != null ? this.originPos : this.getBlockPos(), this.terrainBlastChunkRadius(), 200);
            }
        }
        super.remove(reason);
    }
}
