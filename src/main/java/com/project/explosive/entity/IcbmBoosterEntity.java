package com.project.explosive.entity;

import com.project.explosive.particle.ModParticles;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class IcbmBoosterEntity extends Entity implements PolymerEntity {
    private static final int MAX_LIFE_TICKS = 600;
    private float yawSpeed = 12.0F;
    private float pitchSpeed = 8.0F;

    public IcbmBoosterEntity(EntityType<? extends IcbmBoosterEntity> type, World world) {
        super(type, world);
        if (world != null) {
            this.yawSpeed = (world.getRandom().nextFloat() - 0.5F) * 24.0F;
            this.pitchSpeed = (world.getRandom().nextFloat() - 0.5F) * 16.0F;
        }
    }

    @Override
    public EntityType<?> getPolymerEntityType(ServerPlayerEntity player) {
        return EntityType.FALLING_BLOCK;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        return new EntitySpawnS2CPacket(this, entityTrackerEntry, Block.getRawIdFromState(Blocks.HEAVY_CORE.getDefaultState()));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();

        Vec3d motion = this.getVelocity();
        this.setVelocity(motion.x * 0.99, motion.y - 0.045, motion.z * 0.99);
        this.move(MovementType.SELF, this.getVelocity());

        // Tumbling in flight
        this.setYaw(this.getYaw() + this.yawSpeed);
        this.setPitch(this.getPitch() + this.pitchSpeed);

        if (this.getWorld() instanceof ServerWorld serverLevel) {
            // Spawns smoke trail
            if (this.age % 3 == 0) {
                ParticleUtil.spawnLongDistanceParticles(
                        serverLevel,
                        ModParticles.SMOKE,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        1,
                        (serverLevel.getRandom().nextDouble() - 0.5) * 0.05,
                        0.02,
                        (serverLevel.getRandom().nextDouble() - 0.5) * 0.05
                );
            }

            if (this.isOnGround() || this.isTouchingWater() || this.age > MAX_LIFE_TICKS) {
                if (this.isOnGround() || this.isTouchingWater()) {
                    for (int i = 0; i < 14; i++) {
                        ParticleUtil.spawnLongDistanceParticles(
                                serverLevel,
                                ModParticles.SMOKE_BIG,
                                this.getX(),
                                this.getY() + 0.5,
                                this.getZ(),
                                1,
                                (serverLevel.getRandom().nextDouble() - 0.5) * 0.25,
                                0.1,
                                (serverLevel.getRandom().nextDouble() - 0.5) * 0.25
                        );
                    }
                    SoundUtil.playAt(serverLevel, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 2.0F, 0.6F);
                }
                this.discard();
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound tag) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound tag) {
    }
}
