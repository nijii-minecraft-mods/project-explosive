package com.project.explosive.entity;

import com.project.explosive.particle.ModParticles;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class ICBMEntity extends NuclearBomb implements PolymerEntity {
    public enum FlightPhase {
        BOOST,
        SEPARATION,
        CRUISE,
        DIVE,
        EXPLODED
    }

    private FlightPhase phase = FlightPhase.BOOST;
    private int flightTicks = 0;
    private BlockPos icbmTarget;
    private BlockPos startPos;
    private double peakHeight = 500.0;
    private boolean sonicBoomPlayed = false;
    private boolean reentryStarted = false;
    private String explosionType = "nuclear";

    private static final TrackedData<Integer> DATA_WARHEAD = DataTracker.registerData(ICBMEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DATA_SEPARATED = DataTracker.registerData(ICBMEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private double speedMul = 0.78;
    private double descentReach = 70.0;

    public ICBMEntity(EntityType<? extends ICBMEntity> entityType, World world) {
        super(entityType, world);
    }

    public static String sanitizeExplosionType(String type) {
        if ("small".equalsIgnoreCase(type)) {
            return "small";
        } else if ("big".equalsIgnoreCase(type)) {
            return "big";
        } else {
            return "nuclear";
        }
    }

    public ICBMEntity(World world, BlockPos spawnPos, BlockPos target, String explosionType) {
        super(ModEntities.RAW_ICBM, world);
        this.setPosition((double) spawnPos.getX() + 0.5, (double) spawnPos.getY(), (double) spawnPos.getZ() + 0.5);
        this.startPos = spawnPos;
        this.icbmTarget = target;
        this.explosionType = sanitizeExplosionType(explosionType);
        this.speedMul = speedMulFor(this.explosionType);
        this.peakHeight = peakHeightFor(this.explosionType);
        this.descentReach = 70.0 + world.getRandom().nextDouble() * 50.0;

        this.phase = FlightPhase.BOOST;
        this.setVelocity(0.0, 0.5, 0.0);
        this.setNoGravity(true);
    }

    private static int warheadId(String type) {
        return switch (type) {
            case "big" -> 1;
            case "small" -> 2;
            default -> 0;
        };
    }

    private static double speedMulFor(String type) {
        return switch (type) {
            case "small" -> 1.35;
            case "big" -> 1.0;
            default -> 0.78;
        };
    }

    private static double peakHeightFor(String type) {
        return switch (type) {
            case "small" -> 160.0;
            case "big" -> 180.0;
            default -> 210.0;
        };
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(DATA_WARHEAD, 0);
        builder.add(DATA_SEPARATED, false);
    }

    public boolean isSeparated() {
        return this.getDataTracker().get(DATA_SEPARATED);
    }

    public int getWarheadId() {
        return this.getDataTracker().get(DATA_WARHEAD);
    }

    public FlightPhase getFlightPhase() {
        return this.phase;
    }

    public BlockPos getTargetPos() {
        return this.icbmTarget;
    }

    @Override
    public EntityType<?> getPolymerEntityType(ServerPlayerEntity player) {
        return EntityType.FIREWORK_ROCKET;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 2.5E7;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        tag.putString("ExplosionType", this.explosionType);
        tag.putInt("FlightPhase", this.phase.ordinal());
        tag.putBoolean("Separated", this.isSeparated());
        if (this.icbmTarget != null) {
            tag.putInt("TargetX", this.icbmTarget.getX());
            tag.putInt("TargetY", this.icbmTarget.getY());
            tag.putInt("TargetZ", this.icbmTarget.getZ());
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        if (tag.contains("ExplosionType")) {
            this.explosionType = sanitizeExplosionType(tag.getString("ExplosionType"));
            this.speedMul = speedMulFor(this.explosionType);
            this.peakHeight = peakHeightFor(this.explosionType);
            this.getDataTracker().set(DATA_WARHEAD, warheadId(this.explosionType));
        } else {
            this.explosionType = "nuclear";
        }
        if (tag.contains("FlightPhase")) {
            int phaseIdx = MathHelper.clamp(tag.getInt("FlightPhase"), 0, FlightPhase.values().length - 1);
            this.phase = FlightPhase.values()[phaseIdx];
        }
        if (tag.contains("Separated")) {
            this.getDataTracker().set(DATA_SEPARATED, tag.getBoolean("Separated"));
        }
        if (tag.contains("TargetX")) {
            this.icbmTarget = new BlockPos(tag.getInt("TargetX"), tag.getInt("TargetY"), tag.getInt("TargetZ"));
        }
    }

    private void separateBooster(ServerWorld serverLevel) {
        if (!this.isSeparated()) {
            this.getDataTracker().set(DATA_SEPARATED, true);
            IcbmBoosterEntity booster = new IcbmBoosterEntity(ModEntities.RAW_ICBM_BOOSTER, serverLevel);
            booster.setPosition(this.getX(), this.getY() - 2.0, this.getZ());
            Vec3d m = this.getVelocity();
            booster.setVelocity(
                    m.x * 0.4 + (serverLevel.getRandom().nextDouble() - 0.5) * 0.3,
                    m.y * 0.25 - 0.2,
                    m.z * 0.4 + (serverLevel.getRandom().nextDouble() - 0.5) * 0.3
            );
            serverLevel.spawnEntity(booster);

            for (int i = 0; i < 18; i++) {
                double a = (Math.PI * 2) * (double) i / 18.0;
                ParticleUtil.spawnLongDistanceParticles(
                        serverLevel,
                        ModParticles.SMOKE,
                        this.getX(),
                        this.getY() - 1.0,
                        this.getZ(),
                        1,
                        Math.cos(a) * 0.35,
                        -0.05,
                        Math.sin(a) * 0.35
                );
            }
        }
    }

    @Override
    public void tick() {
        if (this.hasExploded) {
            super.tick();
            return;
        }

        if (this.icbmTarget == null && !this.getWorld().isClient()) {
            this.discard();
            return;
        }

        if (!this.getWorld().isClient()) {
            this.flightTicks++;
            ServerWorld serverLevel = (ServerWorld) this.getWorld();

            // Launch effects on first tick
            if (this.flightTicks == 1) {
                double sx = this.getX();
                double sy = this.getY();
                double sz = this.getZ();

                SoundUtil.playAt(serverLevel, sx, sy, sz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0F, 0.5F);
                SoundUtil.playAt(serverLevel, sx, sy, sz, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0F, 0.5F);
                SoundUtil.playAt(serverLevel, sx, sy, sz, ModSounds.ICBM_IGNITION, SoundCategory.BLOCKS, 4.0F, 0.9F);
                SoundUtil.playAt(serverLevel, sx, sy, sz, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 4.0F, 0.5F);
            }

            // Engine sound during boost
            if (this.phase == FlightPhase.BOOST && this.flightTicks % 7 == 0) {
                SoundUtil.playAt(serverLevel, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 4.0F, 0.45F);
                SoundUtil.playAt(serverLevel, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 4.0F, 0.5F);
            }

            // Spawns rocket exhaust particles during flight
            spawnRocketExhaustParticles(serverLevel);

            double dx = (double) this.icbmTarget.getX() + 0.5 - this.getX();
            double dz = (double) this.icbmTarget.getZ() + 0.5 - this.getZ();
            double distToTarget2D = Math.sqrt(dx * dx + dz * dz);

            // 4-phase flight: BOOST, SEPARATION, CRUISE, DIVE
            switch (this.phase) {
                case BOOST -> {
                    Vec3d motion = this.getVelocity();
                    this.setVelocity(motion.x, Math.min(3.0 * this.speedMul, motion.y + 0.15 * this.speedMul), motion.z);
                    this.setPitch(-90.0F);

                    if (this.getY() >= this.peakHeight) {
                        this.phase = FlightPhase.SEPARATION;
                    }
                }
                case SEPARATION -> {
                    this.separateBooster(serverLevel);
                    this.phase = FlightPhase.CRUISE;
                }
                case CRUISE -> {
                    double speed = 4.0 * this.speedMul;
                    if (distToTarget2D < this.descentReach) {
                        this.phase = FlightPhase.DIVE;
                        if (!this.sonicBoomPlayed) {
                            this.sonicBoomPlayed = true;
                            SoundUtil.playAt(serverLevel, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 15.0F, 0.4F);

                            for (int ix = 0; ix < 60; ix++) {
                                double a = (Math.PI * 2) * (double) ix / 60.0;
                                ParticleUtil.spawnLongDistanceParticles(
                                        serverLevel, ModParticles.SMOKE, this.getX(), this.getY(), this.getZ(), 1, Math.cos(a) * 0.8, 0.0, Math.sin(a) * 0.8
                                );
                            }
                        }
                    } else {
                        double desiredVx = (dx / distToTarget2D) * speed;
                        double desiredVz = (dz / distToTarget2D) * speed;
                        double desiredVy = this.getY() > this.peakHeight ? -0.5 : 0.0;

                        Vec3d motion = this.getVelocity();
                        this.setVelocity(
                                motion.x + (desiredVx - motion.x) * 0.1,
                                motion.y + (desiredVy - motion.y) * 0.1,
                                motion.z + (desiredVz - motion.z) * 0.1
                        );

                        float yaw = (float) (Math.atan2(this.getVelocity().z, this.getVelocity().x) * (180.0 / Math.PI)) - 90.0F;
                        this.setYaw(yaw);
                        float pitch = (float) (-Math.atan2(this.getVelocity().y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * (180.0 / Math.PI));
                        this.setPitch(pitch);
                    }
                }
                case DIVE -> {
                    double ddy = (double) this.icbmTarget.getY() - this.getY();
                    double dist3D = Math.sqrt(dx * dx + ddy * ddy + dz * dz);
                    Vec3d motion = this.getVelocity();

                    if (dist3D > 0.001) {
                        double diveSpeed = Math.min(15.0 * this.speedMul, motion.length() + 0.8 * this.speedMul);
                        double desVx = (dx / dist3D) * diveSpeed;
                        double desVy = (ddy / dist3D) * diveSpeed;
                        double desVz = (dz / dist3D) * diveSpeed;
                        this.setVelocity(motion.x + (desVx - motion.x) * 0.25, motion.y + (desVy - motion.y) * 0.25, motion.z + (desVz - motion.z) * 0.25);
                    }

                    float yaw = (float) (Math.atan2(this.getVelocity().z, this.getVelocity().x) * (180.0 / Math.PI)) - 90.0F;
                    this.setYaw(yaw);
                    float pitch = (float) (-Math.atan2(this.getVelocity().y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * (180.0 / Math.PI));
                    this.setPitch(pitch);

                    double distToGround = this.getY() - (double) this.icbmTarget.getY();
                    if (distToGround < 80.0 && distToGround > 0.0 && !this.reentryStarted) {
                        this.reentryStarted = true;
                        SoundUtil.playAt(serverLevel, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 15.0F, 0.3F);
                    }
                }
                case EXPLODED -> {
                }
            }

            // Impact detection in DIVE phase
            boolean hitGround = this.isOnGround() || this.verticalCollision;
            if (!hitGround && this.phase == FlightPhase.DIVE) {
                Vec3d from = this.getPos();
                Vec3d to = from.add(this.getVelocity());
                RaycastContext ctx = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this);
                BlockHitResult hit = serverLevel.raycast(ctx);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    this.setPosition(hit.getPos().x, hit.getPos().y, hit.getPos().z);
                    this.setVelocity(Vec3d.ZERO);
                    hitGround = true;
                }
            }

            boolean hitTargetHeight = this.phase == FlightPhase.DIVE && this.getY() <= (double) this.icbmTarget.getY() + 1.0;
            if (this.phase == FlightPhase.DIVE && (hitTargetHeight || hitGround)) {
                this.phase = FlightPhase.EXPLODED;
                this.setNoGravity(false);
                this.setVelocity(Vec3d.ZERO);
                BombChunkTickets.release(serverLevel, this);
                BombChunkTickets.forceTemporary(serverLevel, this.getBlockPos(), 2, 200);

                if ("small".equals(this.explosionType)) {
                    SmallBomb smallBomb = new SmallBomb(serverLevel, this.getX(), this.getY(), this.getZ(), true);
                    serverLevel.spawnEntity(smallBomb);
                    smallBomb.explode();
                    this.discard();
                    return;
                } else if ("big".equals(this.explosionType)) {
                    BigBomb bigBomb = new BigBomb(serverLevel, this.getX(), this.getY(), this.getZ(), true);
                    serverLevel.spawnEntity(bigBomb);
                    bigBomb.explode();
                    this.discard();
                    return;
                } else {
                    // Nuclear detonation sequence
                    this.hasExploded = false;
                    this.fallTime = 100;
                    this.explode();
                    return;
                }
            }
        }

        if (!this.isRemoved()) {
            super.tick();
        }
    }

    private void spawnRocketExhaustParticles(ServerWorld serverLevel) {
        Vec3d vel = this.getVelocity();
        double velLen = Math.max(0.001, vel.length());
        double tailDx = -vel.x / velLen * 2.5;
        double tailDy = -vel.y / velLen * 2.5;
        double tailDz = -vel.z / velLen * 2.5;

        double prevX = this.lastRenderX;
        double prevY = this.lastRenderY;
        double prevZ = this.lastRenderZ;
        int steps = velLen > 5.0 ? 6 : (velLen > 2.0 ? 4 : 2);

        for (int i = 0; i < steps; i++) {
            double lerp = (double) i / (double) steps;
            double ix = prevX + (this.getX() - prevX) * lerp + tailDx;
            double iy = prevY + (this.getY() - prevY) * lerp + tailDy;
            double iz = prevZ + (this.getZ() - prevZ) * lerp + tailDz;

            if (this.phase == FlightPhase.BOOST) {
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_FLAME, ix, iy, iz, 1, 0.0, -0.05, 0.0);
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMOKE_BIG, ix, iy, iz, 1, (serverLevel.getRandom().nextDouble() - 0.5) * 0.1, -0.08, (serverLevel.getRandom().nextDouble() - 0.5) * 0.1);
                if (i % 2 == 0) {
                    ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.EMBER, ix, iy, iz, 1, (serverLevel.getRandom().nextDouble() - 0.5) * 0.1, -0.05, (serverLevel.getRandom().nextDouble() - 0.5) * 0.1);
                }
            } else if (this.phase == FlightPhase.CRUISE) {
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMOKE, ix, iy, iz, 1, 0.0, -0.02, 0.0);
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ParticleTypes.FLAME, ix, iy, iz, 1, 0.0, -0.02, 0.0);
            } else if (this.phase == FlightPhase.DIVE) {
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_FLAME, ix, iy, iz, 2, vel.x * 0.2, vel.y * 0.2, vel.z * 0.2);
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SPARK, ix, iy, iz, 1, vel.x * 0.3, vel.y * 0.3, vel.z * 0.3);
                ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMOKE_BIG, ix, iy, iz, 1, 0.0, -0.05, 0.0);
            }
        }
    }
}
