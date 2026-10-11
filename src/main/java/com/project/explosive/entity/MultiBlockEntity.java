package com.project.explosive.entity;

import com.project.explosive.Config;
import com.project.explosive.util.ParticleUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class MultiBlockEntity extends Entity {
    private Map<BlockPos, BlockState> storedBlocks = new HashMap<>();
    private BlockPos centerOffset;
    private float rotationYaw;
    private float rotationPitch;
    private float rotationRoll;
    private float prevRotationYaw;
    private float prevRotationPitch;
    private float prevRotationRoll;
    private float yawSpeed;
    private float pitchSpeed;
    private float rollSpeed;
    private int bounceCount;
    private static final int MAX_BOUNCES = 2;
    private boolean hasLanded = false;
    private int extMinX;
    private int extMinY;
    private int extMinZ;
    private int extMaxX;
    private int extMaxY;
    private int extMaxZ;
    private boolean hasExtents = false;

    public MultiBlockEntity(EntityType<?> type, World level) {
        super(type, level);
        this.centerOffset = BlockPos.ORIGIN;
        this.ignoreCameraFrustum = true;
        Random rand = level != null ? level.getRandom() : Random.create();
        this.yawSpeed = rand.nextFloat() * 20.0F - 10.0F;
        this.pitchSpeed = rand.nextFloat() * 15.0F - 7.5F;
        this.rollSpeed = rand.nextFloat() * 12.0F - 6.0F;
        this.rotationYaw = rand.nextFloat() * 360.0F;
        this.rotationPitch = rand.nextFloat() * 360.0F;
        this.rotationRoll = rand.nextFloat() * 360.0F;
        this.bounceCount = 0;
        this.calculateAndSetBoundingBox();
    }

    public void setBlocks(Map<BlockPos, BlockState> blocks, BlockPos centerOffset) {
        this.storedBlocks = blocks;
        this.centerOffset = centerOffset;
        this.recalculateLocalExtents();
        this.calculateAndSetBoundingBox();
    }

    private void recalculateLocalExtents() {
        if (this.storedBlocks.isEmpty()) {
            this.hasExtents = false;
        } else {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (BlockPos pos : this.storedBlocks.keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            this.extMinX = minX;
            this.extMinY = minY;
            this.extMinZ = minZ;
            this.extMaxX = maxX;
            this.extMaxY = maxY;
            this.extMaxZ = maxZ;
            this.hasExtents = true;
        }
    }

    public Map<BlockPos, BlockState> getBlocks() {
        return this.storedBlocks;
    }

    public BlockPos getCenterOffset() {
        return this.centerOffset;
    }

    private void calculateAndSetBoundingBox() {
        if (!this.hasExtents) {
            this.setBoundingBox(new Box(this.getPos(), this.getPos().add(0.1, 0.1, 0.1)));
        } else {
            Vec3d entityPos = this.getPos();
            Box boundingBox = new Box(
                    entityPos.x + (double) this.extMinX - 0.2,
                    entityPos.y + (double) this.extMinY - 0.2,
                    entityPos.z + (double) this.extMinZ - 0.2,
                    entityPos.x + (double) this.extMaxX + 1.2,
                    entityPos.y + (double) this.extMaxY + 1.2,
                    entityPos.z + (double) this.extMaxZ + 1.2
            );
            this.setBoundingBox(boundingBox);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (Config.qualityMode == Config.QualityMode.POTATO) {
            this.simplifiedPhysics();
        } else {
            this.fullPhysics();
        }
        this.calculateAndSetBoundingBox();
    }

    private void simplifiedPhysics() {
        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
        this.prevRotationRoll = this.rotationRoll;
        this.rotationYaw = this.rotationYaw + this.yawSpeed * 0.7F;
        this.rotationPitch = this.rotationPitch + this.pitchSpeed * 0.7F;
        this.rotationRoll = this.rotationRoll + this.rollSpeed * 0.7F;

        if (!this.hasNoGravity() && !this.hasLanded) {
            this.setVelocity(this.getVelocity().add(0.0, -0.03, 0.0));
        }

        Vec3d motion = this.getVelocity();
        this.move(MovementType.SELF, motion);
        this.setVelocity(motion.multiply(0.96, 0.96, 0.96));

        if (this.isOnGround() && !this.hasLanded) {
            this.hasLanded = true;
            if (this.bounceCount < 1) {
                Vec3d bounceMotion = this.getVelocity().multiply(0.2, -0.3, 0.2);
                this.setVelocity(bounceMotion);
                this.hasLanded = false;
                this.bounceCount++;
                this.yawSpeed *= 0.55F;
                this.pitchSpeed *= 0.55F;
                this.rollSpeed *= 0.55F;
            } else {
                this.createShockwaveEffect();
                this.restoreBlocksToWorld();
                this.discard();
            }
        }

        if (this.age > 600) {
            this.restoreBlocksToWorld();
            this.discard();
        }
    }

    private void fullPhysics() {
        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
        this.prevRotationRoll = this.rotationRoll;
        Vec3d curMotion = this.getVelocity();
        double speedSq = curMotion.lengthSquared();
        float spinScale = (float) Math.min(1.0, 0.25 + speedSq * 2.5);
        this.rotationYaw = this.rotationYaw + this.yawSpeed * spinScale;
        this.rotationPitch = this.rotationPitch + this.pitchSpeed * spinScale;
        this.rotationRoll = this.rotationRoll + this.rollSpeed * spinScale;

        if (!this.hasNoGravity() && !this.hasLanded) {
            this.setVelocity(this.getVelocity().add(0.0, -0.04, 0.0));
        }

        Vec3d motion = this.getVelocity();
        this.move(MovementType.SELF, motion);
        this.setVelocity(motion.multiply(0.98, 0.98, 0.98));

        if (!this.getWorld().isClient && speedSq > 0.25 && this.age % 3 == 0 && !this.storedBlocks.isEmpty() && this.getWorld().getRandom().nextFloat() < 0.5F) {
            this.spawnMaterialDust(1, 0.15);
        }

        if (this.isOnGround() && !this.hasLanded) {
            this.hasLanded = true;
            if (this.bounceCount < 2) {
                Vec3d bounceMotion = this.getVelocity().multiply(0.3, -0.4, 0.3);
                this.setVelocity(bounceMotion);
                this.hasLanded = false;
                this.bounceCount++;
                this.yawSpeed *= 0.55F;
                this.pitchSpeed *= 0.55F;
                this.rollSpeed *= 0.55F;
                if (!this.getWorld().isClient) {
                    this.spawnMaterialDust(4 + this.storedBlocks.size() / 6, 0.25);
                    this.getWorld().playSound(
                            null,
                            this.getX(),
                            this.getY(),
                            this.getZ(),
                            SoundEvents.BLOCK_GRAVEL_HIT,
                            SoundCategory.BLOCKS,
                            0.8F,
                            0.55F + this.getWorld().getRandom().nextFloat() * 0.2F
                    );
                }

                Random r = this.getWorld().getRandom();
                double randomX = (r.nextDouble() - 0.5) * 0.2;
                double randomZ = (r.nextDouble() - 0.5) * 0.2;
                this.setVelocity(this.getVelocity().add(randomX, 0.0, randomZ));
            } else {
                this.createShockwaveEffect();
                this.restoreBlocksToWorld();
                this.discard();
            }
        }

        if (this.age > 600) {
            this.restoreBlocksToWorld();
            this.discard();
        }
    }

    private void spawnMaterialDust(int count, double spread) {
        if (this.getWorld() instanceof ServerWorld serverLevel && !this.storedBlocks.isEmpty()) {
            BlockState sample = this.storedBlocks.values().iterator().next();
            serverLevel.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, sample),
                    this.getX() + (double) this.centerOffset.getX(),
                    this.getY() + (double) this.centerOffset.getY(),
                    this.getZ() + (double) this.centerOffset.getZ(),
                    count,
                    spread + 0.3,
                    spread + 0.3,
                    spread + 0.3,
                    0.05
            );
        }
    }

    private BlockPos findAlternativePosition(BlockPos basePos, BlockPos relativePos) {
        BlockPos intendedPos = basePos.add(relativePos);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos testPos = intendedPos.add(dx, dy, dz);
                    if (this.canPlaceBlock(testPos)) {
                        return testPos;
                    }
                }
            }
        }
        return null;
    }

    private void restoreBlocksToWorld() {
        BlockPos origin = this.getBlockPos();
        BlockPos actualOrigin = origin.add(this.centerOffset);
        BlockPos placementPos = this.findSuitablePlacement(actualOrigin);
        int placedBlocks = 0;
        Random r = this.getWorld().getRandom();

        for (Entry<BlockPos, BlockState> entry : this.storedBlocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockPos realPos = placementPos.add(relativePos);
            boolean shouldBreakOnImpact = r.nextDouble() < 0.2;
            if (shouldBreakOnImpact) {
                if (!this.getWorld().isClient) {
                    ServerWorld serverLevel = (ServerWorld) this.getWorld();
                    BlockStateParticleEffect blockParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, entry.getValue());
                    serverLevel.spawnParticles(
                            blockParticle,
                            (double) realPos.getX() + 0.5,
                            (double) realPos.getY() + 0.5,
                            (double) realPos.getZ() + 0.5,
                            5,
                            0.3,
                            0.3,
                            0.3,
                            0.1
                    );
                }
            } else if (this.canPlaceBlock(realPos)) {
                this.createFallingBlock(realPos, entry.getValue());
                placedBlocks++;
            } else {
                BlockPos alternativePos = this.findAlternativePosition(placementPos, relativePos);
                if (alternativePos != null) {
                    this.createFallingBlock(alternativePos, entry.getValue());
                    placedBlocks++;
                }
            }
        }

        if (placedBlocks == 0 && !this.storedBlocks.isEmpty()) {
            Entry<BlockPos, BlockState> firstEntry = this.storedBlocks.entrySet().iterator().next();
            BlockPos firstPos = placementPos.add(firstEntry.getKey());
            this.createFallingBlock(firstPos, firstEntry.getValue());
        }
    }

    private void createFallingBlock(BlockPos pos, BlockState state) {
        Random r = this.getWorld().getRandom();
        BlockPos spawnPos = pos.up(2 + r.nextInt(3));
        this.getWorld().removeBlock(spawnPos, false);

        try {
            FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(this.getWorld(), spawnPos, state);
            if (fallingBlock != null) {
                double randomX = (r.nextDouble() - 0.5) * 0.2;
                double randomZ = (r.nextDouble() - 0.5) * 0.2;
                double verticalVelocity = -0.1 - r.nextDouble() * 0.1;
                fallingBlock.setVelocity(new Vec3d(randomX, verticalVelocity, randomZ));
                fallingBlock.setYaw(r.nextFloat() * 360.0F);
                fallingBlock.dropItem = false;
            }
        } catch (Exception var12) {
            this.getWorld().setBlockState(pos, state, 3);
        }
    }

    private BlockPos findSuitablePlacement(BlockPos idealPos) {
        if (this.hasEnoughSpace(idealPos)) {
            return idealPos;
        } else {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dy = -2; dy <= 8; dy++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        BlockPos testPos = idealPos.add(dx, dy, dz);
                        if (this.hasEnoughSpace(testPos)) {
                            return testPos;
                        }
                    }
                }
            }
            return idealPos;
        }
    }

    private boolean hasEnoughSpace(BlockPos centerPos) {
        int checkedBlocks = 0;
        int freeBlocks = 0;

        for (Entry<BlockPos, BlockState> entry : this.storedBlocks.entrySet()) {
            BlockPos relativePos = entry.getKey();
            BlockPos testPos = centerPos.add(relativePos);
            checkedBlocks++;
            if (this.canPlaceBlock(testPos)) {
                freeBlocks++;
            }
        }

        return (double) freeBlocks >= (double) checkedBlocks * 0.5;
    }

    private boolean canPlaceBlock(BlockPos pos) {
        BlockState state = this.getWorld().getBlockState(pos);
        return state.isAir()
                || (double) state.getHardness(this.getWorld(), pos) < 1.0
                || !state.isFullCube(this.getWorld(), pos)
                || state.isOf(Blocks.SNOW)
                || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.DEAD_BUSH)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.SEAGRASS)
                || state.isOf(Blocks.TALL_SEAGRASS)
                || state.isOf(Blocks.KELP)
                || state.isOf(Blocks.KELP_PLANT);
    }

    public float getPrevRotationYaw() {
        return this.prevRotationYaw;
    }

    public float getPrevRotationPitch() {
        return this.prevRotationPitch;
    }

    public float getPrevRotationRoll() {
        return this.prevRotationRoll;
    }

    public float getRotationYaw() {
        return this.rotationYaw;
    }

    public float getRotationPitch() {
        return this.rotationPitch;
    }

    public float getRotationRoll() {
        return this.rotationRoll;
    }

    private void createShockwaveEffect() {
        if (!this.getWorld().isClient) {
            BlockPos landingPos = this.getBlockPos();
            if (!this.storedBlocks.isEmpty()) {
                Entry<BlockPos, BlockState> firstBlock = this.storedBlocks.entrySet().iterator().next();
                BlockState blockState = firstBlock.getValue();
                ServerWorld serverLevel = (ServerWorld) this.getWorld();
                int particleCount = 30;
                double shockwaveRadius = 5.0;

                for (int i = 0; i < particleCount; i++) {
                    double angle = (Math.PI * 2) * (double) i / (double) particleCount;
                    double x = (double) landingPos.getX() + 0.5 + Math.cos(angle) * shockwaveRadius;
                    double z = (double) landingPos.getZ() + 0.5 + Math.sin(angle) * shockwaveRadius;
                    double y = (double) landingPos.getY() + 0.5;
                    BlockStateParticleEffect blockParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState);
                    ParticleUtil.spawnLongDistanceComplexParticles(serverLevel, blockParticle, x, y, z, 3, 0.1, 0.1, 0.1);
                }
            }

            this.applyShockwaveDamage();
        }
    }

    private void applyShockwaveDamage() {
        if (!this.getWorld().isClient) {
            Box area = new Box(this.getX() - 8.0, this.getY() - 3.0, this.getZ() - 8.0, this.getX() + 8.0, this.getY() + 3.0, this.getZ() + 8.0);
            for (LivingEntity entity : this.getWorld().getEntitiesByClass(LivingEntity.class, area, e -> true)) {
                if (entity.distanceTo(this) <= 6.0F) {
                    double distance = (double) entity.distanceTo(this);
                    float damage = (float) (10.0 - distance * 0.5);
                    if (damage > 0.0F) {
                        DamageSource damageSource = this.getWorld().getDamageSources().generic();
                        entity.damage(damageSource, damage);
                    }
                }
            }
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound tag) {
        NbtList blocksList = tag.getList("Blocks", 10);
        this.storedBlocks.clear();

        for (int i = 0; i < blocksList.size(); i++) {
            NbtCompound blockTag = blocksList.getCompound(i);
            BlockPos pos = BlockPos.fromLong(blockTag.getLong("Pos"));
            BlockState state = Block.STATE_IDS.get(blockTag.getInt("State"));
            if (state != null) {
                this.storedBlocks.put(pos, state);
            }
        }

        if (tag.contains("CenterOffset")) {
            this.centerOffset = BlockPos.fromLong(tag.getLong("CenterOffset"));
        } else {
            this.centerOffset = BlockPos.ORIGIN;
        }

        this.recalculateLocalExtents();
        this.calculateAndSetBoundingBox();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound tag) {
        NbtList blocksList = new NbtList();

        for (Entry<BlockPos, BlockState> entry : this.storedBlocks.entrySet()) {
            NbtCompound blockTag = new NbtCompound();
            blockTag.putLong("Pos", entry.getKey().asLong());
            blockTag.putInt("State", Block.STATE_IDS.getRawId(entry.getValue()));
            blocksList.add(blockTag);
        }

        tag.put("Blocks", blocksList);
        tag.putLong("CenterOffset", this.centerOffset.asLong());
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }
}
