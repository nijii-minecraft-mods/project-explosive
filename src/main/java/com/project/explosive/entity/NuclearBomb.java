package com.project.explosive.entity;

import com.project.explosive.Config;
import com.project.explosive.environmental.EnvironmentalTransformer;
import com.project.explosive.environmental.ExplosionItemSuppressor;
import com.project.explosive.environmental.RadiationManager;
import com.project.explosive.particle.ModParticles;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.BlastCrumble;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.CraterUtil;
import com.project.explosive.util.DelayedTaskScheduler;
import com.project.explosive.util.FirePropagationManager;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
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

import java.util.HashSet;
import java.util.Set;

public class NuclearBomb extends ThrownEntity implements PolymerEntity {
    protected int fallTime = 0;
    protected boolean hasExploded = false;
    protected boolean launchSoundPlayed = false;
    protected boolean triggeredByBomb = false;
    protected float blastScale = 1.0F;

    private boolean shockwaveActive = false;
    private int shockwaveTicks = 0;
    private static final int MAX_SHOCKWAVE_AGE = 200;
    private BlockPos originPos;
    private final Set<Integer> entitiesHitByShockwave = new HashSet<>();

    public NuclearBomb(EntityType<? extends NuclearBomb> entityType, World world) {
        super(entityType, world);
    }

    public NuclearBomb(World world, LivingEntity owner) {
        super(ModEntities.RAW_NUCLEAR_BOMB, owner, world);
    }

    public NuclearBomb(World world, double x, double y, double z) {
        super(ModEntities.RAW_NUCLEAR_BOMB, x, y, z, world);
    }

    public NuclearBomb(World world, double x, double y, double z, boolean triggeredByBomb) {
        super(ModEntities.RAW_NUCLEAR_BOMB, x, y, z, world);
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

    protected int terrainBlastChunkRadius() {
        double maxBlocks = 250.0 * this.blastScale;
        return (int) Math.ceil(maxBlocks / 16.0) + 1;
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
            if (!this.shockwaveActive || this.age > 2400) {
                this.discard();
            }
            return;
        }

        this.fallTime++;
        if (this.fallTime == 1 && !this.launchSoundPlayed) {
            SoundUtil.playAt(this.getWorld(), this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
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

        if (this.isOnGround() && (this.fallTime > 5 || this instanceof ICBMEntity)) {
            this.explode();
        } else if (this.fallTime >= 100) {
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
        Box fleeBox = this.getBoundingBox().expand(15.0);
        for (LivingEntity living : this.getWorld().getEntitiesByClass(LivingEntity.class, fleeBox, e -> e.getId() != this.getId())) {
            Vec3d dir = living.getPos().subtract(this.getPos()).normalize().multiply(0.4);
            living.addVelocity(dir.x, 0.15, dir.z);
        }
    }

    private void checkForBombCollisions() {
        Box detectionBox = this.getBoundingBox().expand(2.0);
        for (Entity entity : this.getWorld().getOtherEntities(this, detectionBox)) {
            if (entity instanceof NuclearBomb && entity != this) {
                if (this.distanceTo(entity) < 2.0 && !this.hasExploded) {
                    this.explode();
                }
            } else if (entity instanceof BigBomb && entity != this) {
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
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);

        ServerWorld serverLevel = (ServerWorld) this.getWorld();
        BlockPos currentPos = this.getBlockPos();
        int groundY = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, currentPos.getX(), currentPos.getZ()) - 1;
        if (groundY > currentPos.getY() + 50 || groundY < currentPos.getY() - 100) {
            groundY = currentPos.getY();
        }
        this.originPos = new BlockPos(currentPos.getX(), groundY, currentPos.getZ());

        double boomX = this.getX();
        double boomY = this.getY();
        double boomZ = this.getZ();

        // 1. Blinding/flash effect: applies Darkness and Blindness and plays high-pitch whistle/flash ring to nearby players
        applyFlashAndBlindness(serverLevel, boomX, boomY, boomZ);

        // 2. Spawns hemispherical crater via CraterUtil.digCraterWithEjecta(...)
        int craterRadius = Math.max(16, (int) (32.0F * this.blastScale));
        CraterUtil.digCraterWithEjecta(serverLevel, boomX, boomY, boomZ, craterRadius, 0.4, 6, 1.8);

        // 3. Spawns mushroom cloud via ParticleUtil.spawnMushroomCloud(...)
        ParticleUtil.spawnMushroomCloud(serverLevel, boomX, boomY, boomZ, (double) this.blastScale);

        // 4. Applies structural collapse via BlastCrumble.blastAndCrumble(...)
        BlastCrumble.blastAndCrumble(serverLevel, boomX, boomY, boomZ, 32.0 * (double) this.blastScale);

        // 5. Spawns radiation field via RadiationManager.addRadiationSource(...)
        RadiationManager.addRadiationSource(this.originPos, 900.0 * (double) this.blastScale, 24000);

        // 6. Immediate sounds & delayed acoustic sound playback via SoundUtil.playDelayed(...) with ModSounds.NUCLEAR_EXPLOSION and vanilla thunder/explode sounds
        SoundUtil.playAt(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.BLOCKS, 6.0F, 1.6F);
        SoundUtil.playAt(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 8.0F, 0.7F);
        SoundUtil.playAt(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.BLOCKS, 7.0F, 0.42F);

        SoundUtil.playDelayed(serverLevel, boomX, boomY, boomZ, ModSounds.NUCLEAR_EXPLOSION, SoundCategory.BLOCKS, 10.0F, 0.7F);
        SoundUtil.playDelayed(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 9.0F, 0.5F);
        SoundUtil.playDelayed(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 8.0F, 0.6F);

        DelayedTaskScheduler.schedule(serverLevel, 15L, () -> {
            SoundUtil.playAt(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 9.0F, 0.45F);
            SoundUtil.playAt(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.BLOCKS, 5.0F, 0.32F);
        });
        DelayedTaskScheduler.schedule(serverLevel, 35L, () -> {
            SoundUtil.playAt(serverLevel, boomX, boomY, boomZ, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 6.0F, 0.3F);
        });

        // 7. Shockwave damage to living entities across distance
        applyShockwaveDamage(serverLevel, boomX, boomY, boomZ);

        // Environmental fire and item suppression
        this.setBlocksOnFire(serverLevel);
        this.killNearbyItems(serverLevel);
        ExplosionItemSuppressor.register(serverLevel, boomX, boomY, boomZ, 400.0, 1200);
        EnvironmentalTransformer.contaminateArea(this.originPos, serverLevel, 80.0 * this.blastScale);

        this.shockwaveActive = true;
        this.shockwaveTicks = 0;
        this.entitiesHitByShockwave.clear();
    }

    private void applyFlashAndBlindness(ServerWorld serverLevel, double x, double y, double z) {
        double maxFlashDistance = 500.0;
        for (ServerPlayerEntity player : serverLevel.getPlayers()) {
            double dist = Math.sqrt(player.squaredDistanceTo(x, y, z));
            if (dist <= maxFlashDistance) {
                double prox = 1.0 - (dist / maxFlashDistance);
                int darkDuration = (int) Math.max(100, 400 * prox);
                int blindDuration = (int) Math.max(60, 300 * prox);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, darkDuration, 0, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, blindDuration, 0, false, false, true));
                SoundUtil.playNotify(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0F, 2.0F);
                SoundUtil.playNotify(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.8F, 2.0F);
            }
        }
    }

    private void applyShockwaveDamage(ServerWorld serverLevel, double x, double y, double z) {
        double maxDist = 250.0 * (double) this.blastScale;
        Box area = new Box(x - maxDist, y - 60.0, z - maxDist, x + maxDist, y + 100.0, z + maxDist);

        for (LivingEntity entity : serverLevel.getEntitiesByClass(LivingEntity.class, area, e -> true)) {
            double dist = Math.sqrt(entity.squaredDistanceTo(x, y, z));
            if (dist <= maxDist) {
                float factor = (float) (1.0 - dist / maxDist);
                float damage = Math.max(12.0F, factor * 250.0F * this.blastScale);
                if (damage >= entity.getHealth()) {
                    entity.damage(serverLevel.getDamageSources().genericKill(), Float.MAX_VALUE);
                } else {
                    entity.damage(serverLevel.getDamageSources().explosion(this, this.getOwner()), damage);
                }

                int fireTicks = (int) (400.0F * factor);
                if (fireTicks > entity.getFireTicks()) {
                    entity.setFireTicks(fireTicks);
                }

                Vec3d dir = entity.getPos().subtract(this.getPos()).normalize();
                double knock = Math.max(0.4, factor * 4.0);
                entity.addVelocity(dir.x * knock, 1.2 * factor + 0.3, dir.z * knock);
            }
        }
    }

    private void processShockwave(ServerWorld serverLevel) {
        this.shockwaveTicks++;
        double currentRadius = (double) this.shockwaveTicks * 2.5;

        if (this.shockwaveTicks > 60) {
            this.shockwaveActive = false;
            return;
        }

        double maxListenDistSq = (currentRadius + 128.0) * (currentRadius + 128.0);
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

        if (this.shockwaveTicks % 3 == 0) {
            double cx = (double) this.originPos.getX() + 0.5;
            double cz = (double) this.originPos.getZ() + 0.5;
            int points = (int) MathHelper.clamp(currentRadius * 0.8, 12.0, 24.0);
            for (int i = 0; i < points; i++) {
                double ang = (Math.PI * 2) * (double) i / (double) points;
                double px = cx + Math.cos(ang) * currentRadius;
                double pz = cz + Math.sin(ang) * currentRadius;
                int py = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(px), MathHelper.floor(pz));
                ParticleUtil.spawnLongDistanceParticles(
                        serverLevel, ModParticles.SMOKE_BIG_SHORT, px, (double) py + 0.5, pz, 1, Math.cos(ang) * 0.2, 0.04, Math.sin(ang) * 0.2
                );
            }
        }
    }

    private void setBlocksOnFire(ServerWorld serverLevel) {
        BlockPos origin = this.originPos != null ? this.originPos : this.getBlockPos();
        int fireRadius = (int) (60.0 * this.blastScale);
        int samples = 500;

        for (int i = 0; i < samples; i++) {
            double ang = serverLevel.getRandom().nextDouble() * 2.0 * Math.PI;
            double r = Math.sqrt(serverLevel.getRandom().nextDouble()) * (double) fireRadius;
            int dx = (int) Math.round(Math.cos(ang) * r);
            int dz = (int) Math.round(Math.sin(ang) * r);
            int wx = origin.getX() + dx;
            int wz = origin.getZ() + dz;
            int wy = serverLevel.getTopY(Heightmap.Type.MOTION_BLOCKING, wx, wz);
            BlockPos groundPos = new BlockPos(wx, wy - 1, wz);
            BlockPos firePos = new BlockPos(wx, wy, wz);

            BlockState groundState = serverLevel.getBlockState(groundPos);
            BlockState fireState = serverLevel.getBlockState(firePos);
            if (fireState.isAir() && (groundState.isIn(BlockTags.LOGS) || groundState.isIn(BlockTags.PLANKS) || groundState.isIn(BlockTags.LEAVES) || groundState.isSolidBlock(serverLevel, groundPos))) {
                if (Blocks.FIRE.getDefaultState().canPlaceAt(serverLevel, firePos)) {
                    serverLevel.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 3);
                    if (i % 60 == 0) {
                        FirePropagationManager.getInstance().startFirePropagation(serverLevel, wx, wy, wz, 25);
                    }
                }
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
                BombChunkTickets.forceTemporary(serverLevel, this.originPos != null ? this.originPos : this.getBlockPos(), this.terrainBlastChunkRadius(), 600);
            }
        }
        super.remove(reason);
    }
}
