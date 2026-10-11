package com.project.explosive.util;

import com.project.explosive.Config;
import com.project.explosive.particle.ModParticles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;

public class FirePropagationManager {
    private static final FirePropagationManager INSTANCE = new FirePropagationManager();
    private final Map<UUID, FirePropagationManager.FirePropagationSystem> activeSystems = new ConcurrentHashMap<>();
    private static final double HEAT_RISE_FACTOR = 2.0;
    private static final double WIND_SPEED_FACTOR = 0.5;
    private static final double LOG_AXIS_FACTOR = 1.3;
    private static final double DOWNWARD_TRANSFER_FACTOR = 0.3;
    private static final double MIN_HEAT_TRANSFER = 0.1;

    private FirePropagationManager() {
    }

    public static FirePropagationManager getInstance() {
        return INSTANCE;
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            FirePropagationSavedData data = FirePropagationSavedData.get(world);
            data.markDirty();
            onWorldTick(world);
        });
    }

    public void startFirePropagation(ServerWorld level, double x, double y, double z) {
        this.startFirePropagation(level, x, y, z, 2);
    }

    public void startFirePropagation(ServerWorld level, double x, double y, double z, int initialIntensity) {
        this.startFirePropagation(level, x, y, z, initialIntensity, 0.0);
    }

    public void startFirePropagation(ServerWorld level, double x, double y, double z, int initialIntensity, double radius) {
        if (Config.enableFirePropagation) {
            UUID systemId = UUID.randomUUID();
            FirePropagationManager.FirePropagationSystem system = new FirePropagationManager.FirePropagationSystem(systemId, level, x, y, z);
            system.setInitialIntensity(initialIntensity);
            if (radius > 0.5) {
                system.igniteRadius(radius, initialIntensity);
            }

            this.activeSystems.put(systemId, system);
            system.start();
        }
    }

    public void stopFirePropagation(UUID systemId) {
        FirePropagationManager.FirePropagationSystem system = this.activeSystems.get(systemId);
        if (system != null) {
            system.stop();
            this.activeSystems.remove(systemId);
        }
    }

    public void stopAllFirePropagations() {
        for (FirePropagationManager.FirePropagationSystem system : this.activeSystems.values()) {
            system.stop();
        }
        this.activeSystems.clear();
    }

    public int extinguishFiresInRadius(ServerWorld level, double x, double y, double z, double radius) {
        int extinguished = 0;
        double radiusSq = radius * radius;

        for (FirePropagationManager.FirePropagationSystem system : this.activeSystems.values()) {
            if (system.level == level && system.isActive) {
                extinguished += system.extinguishCellsInRadius(x, y, z, radiusSq);
            }
        }

        return extinguished;
    }

    public static void onWorldTick(ServerWorld serverLevel) {
        INSTANCE.activeSystems.entrySet().removeIf(entry -> !entry.getValue().isActive);
        int globalTickBudget = (int) (400.0F * Math.min(1.0F, Config.particleDensityMultiplier));
        if (Config.isPotatoMode()) {
            globalTickBudget = Math.min(globalTickBudget, 220);
        }

        int activeSystemCount = INSTANCE.activeSystems.size();
        if (activeSystemCount == 0) {
            return;
        }

        int budgetPerSystem = Math.max(5, globalTickBudget / activeSystemCount);

        for (FirePropagationManager.FirePropagationSystem system : INSTANCE.activeSystems.values()) {
            if (system.level == serverLevel) {
                system.tick(budgetPerSystem);
            }
        }
    }

    public void cleanupInactiveSystems() {
        Iterator<Entry<UUID, FirePropagationManager.FirePropagationSystem>> iterator = this.activeSystems.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<UUID, FirePropagationManager.FirePropagationSystem> entry = iterator.next();
            if (!entry.getValue().isActive) {
                iterator.remove();
            }
        }
    }

    public int getActiveSystemCount() {
        return this.activeSystems.size();
    }

    public Set<UUID> getActiveSystemIds() {
        return new HashSet<>(this.activeSystems.keySet());
    }

    private static int getFlammability(BlockState state) {
        FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
        return entry != null ? entry.getBurnChance() : 0;
    }

    public static class FirePropagationSavedData extends PersistentState {
        private final ServerWorld level;

        public FirePropagationSavedData(ServerWorld level) {
            this.level = level;
        }

        public FirePropagationSavedData(NbtCompound nbt, ServerWorld level) {
            this.level = level;
            if (nbt.contains("systems", 9)) {
                NbtList list = nbt.getList("systems", 10);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound systemNbt = list.getCompound(i);
                    try {
                        FirePropagationManager.FirePropagationSystem system = FirePropagationManager.FirePropagationSystem.load(systemNbt, level);
                        if (system != null) {
                            FirePropagationManager.INSTANCE.activeSystems.put(system.getId(), system);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            NbtList list = new NbtList();
            for (FirePropagationManager.FirePropagationSystem system : FirePropagationManager.INSTANCE.activeSystems.values()) {
                if (system.level == this.level && system.isActive) {
                    list.add(system.save());
                }
            }
            nbt.put("systems", list);
            return nbt;
        }

        public static FirePropagationSavedData get(ServerWorld level) {
            Type<FirePropagationSavedData> type = new Type<>(
                    () -> new FirePropagationSavedData(level),
                    (tag, registries) -> new FirePropagationSavedData(tag, level),
                    DataFixTypes.SAVED_DATA_COMMAND_STORAGE
            );
            return level.getPersistentStateManager().getOrCreate(type, "projectexplosive_fire");
        }
    }

    public static class FirePropagationSystem {
        private final UUID systemId;
        private final ServerWorld level;
        private final double startX;
        private final double startY;
        private final double startZ;
        private final Map<BlockPos, FirePropagationManager.FirePropagationSystem.CellState> cellStates = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> fireIntensity = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> thermalResistance = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> fuelAmount = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> heatLevel = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> lastHeatTransferTick = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> burnProgress = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> lastBurnEffectTick = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> lastEntityDamageTick = new ConcurrentHashMap<>();
        private final Queue<BlockPos> pendingQueue = new ArrayDeque<>();
        private final Queue<BlockPos> nextQueue = new ArrayDeque<>();
        private volatile boolean isActive = false;
        private volatile boolean shouldStop = false;
        private int currentTickCount = 0;
        private int emptyQueueCount = 0;
        private double windSpeed;
        private double windAngle;
        private Vec3d windDirection;
        private int initialIntensity = 2;
        private int propagationInterval;
        private static final int MAX_LIFETIME_TICKS = 3600;
        private static final double MAX_SPREAD_RADIUS = 36.0;
        private final SimpleParticleType cachedFireFlame;
        private final SimpleParticleType cachedSmallFire;
        private final SimpleParticleType cachedFireSmoke;
        private final SimpleParticleType cachedEmber;

        private void setWindAngle(double angle) {
            this.windAngle = angle;
            this.windDirection = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
        }

        public FirePropagationSystem(UUID id, ServerWorld level, double x, double y, double z) {
            this.systemId = id;
            this.level = level;
            this.startX = x;
            this.startY = y;
            this.startZ = z;
            this.propagationInterval = 25 + level.getRandom().nextInt(11);
            this.setWindAngle(Math.PI + (level.getRandom().nextDouble() - 0.5) * 0.9);
            this.windSpeed = 0.25 + level.getRandom().nextDouble() * 0.2;
            this.cachedFireFlame = ModParticles.FIRE_FLAME_PARTICLE.get();
            this.cachedSmallFire = ModParticles.SMALL_FIRE_PARTICLE.get();
            this.cachedFireSmoke = ModParticles.FIRE_SMOKE_PARTICLE.get();
            this.cachedEmber = ModParticles.EMBER_PARTICLE.get();
        }

        public UUID getId() {
            return this.systemId;
        }

        public NbtCompound save() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("id", this.systemId);
            nbt.putDouble("startX", this.startX);
            nbt.putDouble("startY", this.startY);
            nbt.putDouble("startZ", this.startZ);
            nbt.putInt("initialIntensity", this.initialIntensity);
            nbt.putInt("propagationInterval", this.propagationInterval);
            nbt.putInt("currentTickCount", this.currentTickCount);
            nbt.putInt("emptyQueueCount", this.emptyQueueCount);
            nbt.putBoolean("isActive", this.isActive);
            nbt.putDouble("windAngle", this.windAngle);
            nbt.putDouble("windSpeed", this.windSpeed);
            NbtList cellsList = new NbtList();

            for (Entry<BlockPos, FirePropagationManager.FirePropagationSystem.CellState> entry : this.cellStates.entrySet()) {
                BlockPos pos = entry.getKey();
                NbtCompound cellNbt = new NbtCompound();
                cellNbt.put("pos", NbtHelper.fromBlockPos(pos));
                cellNbt.putString("state", entry.getValue().name());
                if (this.fireIntensity.containsKey(pos)) {
                    cellNbt.putInt("intensity", this.fireIntensity.get(pos));
                }
                if (this.thermalResistance.containsKey(pos)) {
                    cellNbt.putInt("resistance", this.thermalResistance.get(pos));
                }
                if (this.fuelAmount.containsKey(pos)) {
                    cellNbt.putInt("fuel", this.fuelAmount.get(pos));
                }
                if (this.heatLevel.containsKey(pos)) {
                    cellNbt.putInt("heat", this.heatLevel.get(pos));
                }
                if (this.burnProgress.containsKey(pos)) {
                    cellNbt.putInt("burn", this.burnProgress.get(pos));
                }
                cellsList.add(cellNbt);
            }

            nbt.put("cells", cellsList);
            NbtList pendingList = new NbtList();
            for (BlockPos posx : this.pendingQueue) {
                pendingList.add(NbtHelper.fromBlockPos(posx));
            }
            nbt.put("pending", pendingList);

            NbtList nextList = new NbtList();
            for (BlockPos posx : this.nextQueue) {
                nextList.add(NbtHelper.fromBlockPos(posx));
            }
            nbt.put("next", nextList);
            return nbt;
        }

        public static FirePropagationManager.FirePropagationSystem load(NbtCompound nbt, ServerWorld level) {
            UUID id = nbt.getUuid("id");
            double x = nbt.getDouble("startX");
            double y = nbt.getDouble("startY");
            double z = nbt.getDouble("startZ");
            FirePropagationManager.FirePropagationSystem system = new FirePropagationManager.FirePropagationSystem(id, level, x, y, z);
            system.initialIntensity = nbt.getInt("initialIntensity");
            system.propagationInterval = nbt.getInt("propagationInterval");
            system.currentTickCount = nbt.getInt("currentTickCount");
            system.emptyQueueCount = nbt.getInt("emptyQueueCount");
            system.isActive = nbt.getBoolean("isActive");
            if (nbt.contains("windAngle")) {
                system.setWindAngle(nbt.getDouble("windAngle"));
                system.windSpeed = nbt.getDouble("windSpeed");
            }

            NbtList cellsList = nbt.getList("cells", 10);
            for (int i = 0; i < cellsList.size(); i++) {
                NbtCompound cellNbt = cellsList.getCompound(i);
                BlockPos pos = NbtHelper.toBlockPos(cellNbt, "pos").orElse(BlockPos.ORIGIN);
                system.cellStates.put(pos, FirePropagationManager.FirePropagationSystem.CellState.valueOf(cellNbt.getString("state")));
                if (cellNbt.contains("intensity")) {
                    system.fireIntensity.put(pos, cellNbt.getInt("intensity"));
                }
                if (cellNbt.contains("resistance")) {
                    system.thermalResistance.put(pos, cellNbt.getInt("resistance"));
                }
                if (cellNbt.contains("fuel")) {
                    system.fuelAmount.put(pos, cellNbt.getInt("fuel"));
                }
                if (cellNbt.contains("heat")) {
                    system.heatLevel.put(pos, cellNbt.getInt("heat"));
                }
                if (cellNbt.contains("burn")) {
                    system.burnProgress.put(pos, cellNbt.getInt("burn"));
                }
            }

            NbtList pendingList = nbt.getList("pending", 10);
            for (int i = 0; i < pendingList.size(); i++) {
                NbtCompound pCompound = pendingList.getCompound(i);
                NbtHelper.toBlockPos(pCompound, "").ifPresent(system.pendingQueue::add);
            }

            NbtList nextList = nbt.getList("next", 10);
            for (int ix = 0; ix < nextList.size(); ix++) {
                NbtCompound nCompound = nextList.getCompound(ix);
                NbtHelper.toBlockPos(nCompound, "").ifPresent(system.nextQueue::add);
            }

            return system;
        }

        public void setInitialIntensity(int intensity) {
            this.initialIntensity = intensity;
        }

        public void igniteRadius(double radius, int intensity) {
            int r = (int) Math.ceil(radius);
            BlockPos center = new BlockPos((int) this.startX, (int) this.startY, (int) this.startZ);

            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if ((double) (dx * dx + dy * dy + dz * dz) <= radius * radius) {
                            BlockPos target = center.add(dx, dy, dz);
                            if (this.getState(target) == FirePropagationManager.FirePropagationSystem.CellState.EMPTY && this.isBlockInflammable(target)) {
                                this.fireIntensity.put(target, intensity);
                                this.setState(target, FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE);
                                if (!this.pendingQueue.contains(target)) {
                                    this.pendingQueue.offer(target);
                                }
                            }
                        }
                    }
                }
            }
        }

        public void start() {
            if (!this.isActive) {
                this.isActive = true;
                this.shouldStop = false;
                this.currentTickCount = 0;
                BlockPos startPos = new BlockPos((int) Math.floor(this.startX), (int) Math.floor(this.startY), (int) Math.floor(this.startZ));
                this.fireIntensity.put(startPos, this.initialIntensity);
                this.setState(startPos, FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE);
                this.pendingQueue.offer(startPos);
            }
        }

        public void stop() {
            this.shouldStop = true;
            this.cellStates.clear();
            this.fireIntensity.clear();
            this.thermalResistance.clear();
            this.fuelAmount.clear();
            this.heatLevel.clear();
            this.lastHeatTransferTick.clear();
            this.lastEntityDamageTick.clear();
            this.burnProgress.clear();
            this.lastBurnEffectTick.clear();
            this.pendingQueue.clear();
            this.nextQueue.clear();
        }

        public int extinguishCellsInRadius(double x, double y, double z, double radiusSq) {
            int count = 0;
            List<BlockPos> toExtinguish = new ArrayList<>();

            for (Entry<BlockPos, FirePropagationManager.FirePropagationSystem.CellState> entry : this.cellStates.entrySet()) {
                if (entry.getValue() == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                    BlockPos pos = entry.getKey();
                    double dx = (double) pos.getX() + 0.5 - x;
                    double dy = (double) pos.getY() + 0.5 - y;
                    double dz = (double) pos.getZ() + 0.5 - z;
                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                        toExtinguish.add(pos);
                    }
                }
            }

            for (BlockPos pos : toExtinguish) {
                this.extinguishFire(pos, true);
                count++;
            }

            return count;
        }

        public void tick(int maxBlocksBudget) {
            if (this.shouldStop) {
                this.isActive = false;
            } else {
                this.currentTickCount++;
                if (this.currentTickCount > 3600) {
                    this.shouldStop = true;
                } else {
                    if (this.currentTickCount % 120 == 0) {
                        this.setWindAngle(this.windAngle + (this.level.getRandom().nextDouble() - 0.5) * 0.12);
                    }

                    if (this.currentTickCount % this.propagationInterval == 0) {
                        if (this.pendingQueue.isEmpty() && this.nextQueue.isEmpty()) {
                            this.emptyQueueCount++;
                            if (this.emptyQueueCount >= 3) {
                                this.shouldStop = true;
                            }
                        } else {
                            this.emptyQueueCount = 0;
                            this.pendingQueue.addAll(this.nextQueue);
                            this.nextQueue.clear();
                        }
                    }

                    if (!this.pendingQueue.isEmpty()) {
                        int ticksLeftInCycle = this.propagationInterval - this.currentTickCount % this.propagationInterval;
                        if (ticksLeftInCycle <= 0) {
                            ticksLeftInCycle = 1;
                        }

                        int cellsToProcess = (int) Math.ceil((double) this.pendingQueue.size() / (double) ticksLeftInCycle);
                        cellsToProcess = Math.min(cellsToProcess, maxBlocksBudget);

                        for (int i = 0; i < cellsToProcess && !this.pendingQueue.isEmpty(); i++) {
                            this.processSingleCell(this.pendingQueue.poll());
                        }
                    }

                    if (this.currentTickCount % 3 == 0) {
                        this.spawnParticlesForActiveFires(maxBlocksBudget);
                    }
                }
            }
        }

        private void processSingleCell(BlockPos firePos) {
            if (this.getState(firePos) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                if (this.checkExtinguish(firePos)) {
                    return;
                }

                if (this.level.getBlockState(firePos).isAir()) {
                    this.extinguishFire(firePos, false);
                    return;
                }

                if (this.level.getRandom().nextDouble() < 0.25) {
                    this.nextQueue.offer(firePos);
                    return;
                }

                this.transferHeatToNeighbors(firePos);
                this.consumeFuelAndCheckBurnout(firePos);
                this.damageNearbyEntities(firePos);
                this.launchWindEmbers(firePos);
                if (this.getState(firePos) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                    this.nextQueue.offer(firePos);
                }
            }
        }

        private void damageNearbyEntities(BlockPos firePos) {
            if (Config.enableFireDamage) {
                if (!(this.level.getRandom().nextDouble() > 0.25)) {
                    int lastDamageTick = this.lastEntityDamageTick.getOrDefault(firePos, 0);
                    if (this.currentTickCount - lastDamageTick >= 10) {
                        this.lastEntityDamageTick.put(firePos, this.currentTickCount);
                        int nearbyFires = this.countNearbyFires(firePos);
                        int heat = this.heatLevel.getOrDefault(firePos, 50);
                        int intensity = this.fireIntensity.getOrDefault(firePos, 2);
                        float intensityDmgScale = 0.5F + (float) intensity / 10.0F;
                        float baseDamage = 3.0F * intensityDmgScale;
                        float sizeFactor = 1.0F + (float) nearbyFires * 0.25F;
                        float heatFactor = Math.max(0.6F, (float) heat / 80.0F);
                        float totalDamage = baseDamage * sizeFactor * heatFactor;
                        double radius = 1.5 + (double) nearbyFires * 0.08 + (double) intensity * 0.02;
                        Box searchBox = new Box(
                                (double) firePos.getX() - radius,
                                (double) (firePos.getY() - 1),
                                (double) firePos.getZ() - radius,
                                (double) firePos.getX() + radius + 1.0,
                                (double) (firePos.getY() + 3),
                                (double) firePos.getZ() + radius + 1.0
                        );

                        for (LivingEntity entity : this.level.getEntitiesByClass(LivingEntity.class, searchBox, e -> true)) {
                            entity.setOnFireFor((float) (4 + nearbyFires / 2));
                            entity.damage(entity.getDamageSources().onFire(), totalDamage);
                            if (nearbyFires >= 3) {
                                int blindDuration = 40 + nearbyFires * 5;
                                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, blindDuration, 0, false, false));
                                float chokeDamage = 2.0F + (float) nearbyFires * 0.5F;
                                entity.damage(entity.getDamageSources().inWall(), chokeDamage);
                                if (nearbyFires >= 6) {
                                    entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0, false, false));
                                }
                            }

                            if (entity instanceof PathAwareEntity mob) {
                                Vec3d awayVec = mob.getPos()
                                        .subtract((double) firePos.getX() + 0.5, (double) firePos.getY(), (double) firePos.getZ() + 0.5)
                                        .normalize()
                                        .multiply(7.0);
                                awayVec = awayVec.add((this.level.getRandom().nextDouble() - 0.5) * 4.0, 0.0, (this.level.getRandom().nextDouble() - 0.5) * 4.0);
                                Vec3d targetPos = mob.getPos().add(awayVec);
                                mob.getNavigation().startMovingTo(targetPos.x, targetPos.y, targetPos.z, 1.5);
                            }
                        }
                    }
                }
            }
        }

        private void launchWindEmbers(BlockPos firePos) {
            int intensity = this.fireIntensity.getOrDefault(firePos, 0);
            if (intensity >= 5) {
                double emberChance = 0.008 * ((double) intensity / 10.0);
                if (!(this.level.getRandom().nextDouble() > emberChance)) {
                    double dist = 2.0 + this.level.getRandom().nextDouble() * 4.0;
                    double spread = 0.5;
                    double dx = this.windDirection.x * dist + (this.level.getRandom().nextDouble() - 0.5) * spread * dist;
                    double dy = this.windDirection.y * dist + this.level.getRandom().nextDouble() * 0.4 * dist;
                    double dz = this.windDirection.z * dist + (this.level.getRandom().nextDouble() - 0.5) * spread * dist;
                    BlockPos targetPos = firePos.add((int) dx, (int) dy, (int) dz);
                    double distFromStartSq = targetPos.getSquaredDistance(new BlockPos((int) this.startX, (int) this.startY, (int) this.startZ));
                    if (distFromStartSq <= 1296.0 && this.canIgniteBlock(targetPos)) {
                        this.applyHeatToNeighbor(targetPos, 40.0 * ((double) intensity / 10.0), intensity);
                        ParticleUtil.spawnLongDistanceParticles(
                                this.level,
                                this.cachedEmber,
                                (double) firePos.getX() + 0.5,
                                (double) firePos.getY() + 0.5,
                                (double) firePos.getZ() + 0.5,
                                1,
                                dx * 0.1,
                                dy * 0.1,
                                dz * 0.1
                        );
                    }
                }
            }
        }

        private boolean checkExtinguish(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            if (state.getFluidState().isIn(FluidTags.WATER)) {
                this.extinguishFire(pos, true);
                return true;
            } else {
                BlockPos[] adjacents = new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()};

                for (BlockPos adj : adjacents) {
                    if (this.level.getBlockState(adj).getFluidState().isIn(FluidTags.WATER)) {
                        this.extinguishFire(pos, true);
                        return true;
                    }
                }

                if (this.level.isRaining() && this.level.hasRain(pos.up()) && this.level.getRandom().nextDouble() < 0.85) {
                    this.extinguishFire(pos, true);
                    return true;
                } else {
                    return false;
                }
            }
        }

        private void extinguishFire(BlockPos pos, boolean playSteamEffects) {
            this.setState(pos, FirePropagationManager.FirePropagationSystem.CellState.EMPTY);
            if (playSteamEffects) {
                if (this.level.getRandom().nextDouble() < 0.6) {
                    SoundUtil.playAt(
                            this.level,
                            (double) pos.getX() + 0.5,
                            (double) pos.getY() + 0.5,
                            (double) pos.getZ() + 0.5,
                            SoundEvents.BLOCK_FIRE_EXTINGUISH,
                            SoundCategory.BLOCKS,
                            0.7F,
                            2.6F + (this.level.getRandom().nextFloat() - this.level.getRandom().nextFloat()) * 0.8F
                    );
                }

                this.level.spawnParticles(ParticleTypes.CLOUD, (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, 4, 0.2, 0.3, 0.2, 0.05);
            }

            this.burnProgress.remove(pos);
            this.lastBurnEffectTick.remove(pos);
            this.fuelAmount.remove(pos);
            this.heatLevel.remove(pos);
            this.lastHeatTransferTick.remove(pos);
            this.lastEntityDamageTick.remove(pos);
            this.thermalResistance.remove(pos);
            this.fireIntensity.remove(pos);
        }

        private void transferHeatToNeighbors(BlockPos heatSourcePos) {
            int lastTransferTick = this.lastHeatTransferTick.getOrDefault(heatSourcePos, -100);
            if (this.currentTickCount - lastTransferTick >= 8) {
                this.lastHeatTransferTick.put(heatSourcePos, this.currentTickCount);
                BlockPos[] selectedNeighbors = this.getRandomNeighborsWithBias(heatSourcePos);
                int intensity = this.fireIntensity.getOrDefault(heatSourcePos, 2);
                double intensityMultiplier = 0.5 + (double) intensity / 10.0;

                for (BlockPos neighborPos : selectedNeighbors) {
                    double dx = (double) neighborPos.getX() - this.startX;
                    double dy = (double) neighborPos.getY() - this.startY;
                    double dz = (double) neighborPos.getZ() - this.startZ;
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (!(distanceSq > 1296.0)) {
                        double heatTransfer = this.calculateHeatTransfer(heatSourcePos, neighborPos);
                        double distance = Math.sqrt(distanceSq);
                        double fatigue = Math.max(0.05, 1.0 - distance / 36.0);
                        heatTransfer *= fatigue;
                        heatTransfer *= intensityMultiplier;
                        this.applyHeatToNeighbor(neighborPos, heatTransfer, intensity);
                    }
                }
            }
        }

        private BlockPos[] getAllNeighbors(BlockPos pos) {
            BlockPos[] directNeighbors = new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()};
            BlockPos[] diagonalNeighbors = new BlockPos[]{
                    pos.north().east(),
                    pos.north().west(),
                    pos.south().east(),
                    pos.south().west(),
                    pos.up().north(),
                    pos.up().south(),
                    pos.up().east(),
                    pos.up().west(),
                    pos.down().north(),
                    pos.down().south(),
                    pos.down().east(),
                    pos.down().west()
            };
            BlockPos[] allNeighbors = new BlockPos[directNeighbors.length + diagonalNeighbors.length];
            System.arraycopy(directNeighbors, 0, allNeighbors, 0, directNeighbors.length);
            System.arraycopy(diagonalNeighbors, 0, allNeighbors, directNeighbors.length, diagonalNeighbors.length);
            return allNeighbors;
        }

        private void updateFireEffects(BlockPos pos) {
            int heat = this.heatLevel.getOrDefault(pos, 50);
            int fuel = this.fuelAmount.getOrDefault(pos, 0);
            if (fuel > 0 && heat > 0) {
                double adjustedIntensity = (double) heat / 100.0 * (double) fuel / 500.0;
                if (this.level.getRandom().nextDouble() < adjustedIntensity * 0.2) {
                    this.spawnFireParticles(pos);
                }
            }
        }

        private int countNearbyFires(BlockPos pos) {
            int count = 0;
            if (this.getState(pos.up()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                count++;
            }
            if (this.getState(pos.down()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                count++;
            }
            if (this.getState(pos.north()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                count++;
            }
            if (this.getState(pos.south()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                count++;
            }
            if (this.getState(pos.east()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                count++;
            }
            if (this.getState(pos.west()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                count++;
            }
            return count;
        }

        private double getHeatSourceTemperature(BlockPos pos) {
            int heat = this.heatLevel.getOrDefault(pos, 50);
            int fuel = this.fuelAmount.getOrDefault(pos, 0);
            return fuel > 0 ? (double) Math.min(100, heat + fuel / 2) : (double) Math.max(0, heat - 10);
        }

        private double calculateHeatTransfer(BlockPos sourcePos, BlockPos targetPos) {
            double baseHeat = this.getHeatSourceTemperature(sourcePos);
            double directionFactor = this.calculateDirectionFactor(sourcePos, targetPos);
            double materialFactor = this.getMaterialConductivityFactor(targetPos);
            double distanceFactor = this.calculateDistanceFactor(sourcePos, targetPos);
            double windFactor = this.calculateWindFactor(sourcePos, targetPos);
            double slopeFactor = this.calculateSlopeFactor(sourcePos, targetPos);
            double weatherFactor = this.calculateWeatherFactor(targetPos);
            double fuelDensityFactor = this.calculateFuelDensityFactor(targetPos);
            double chemicalFactor = this.getChemicalFactor(targetPos);
            if (chemicalFactor <= 0.0) {
                return 0.0;
            } else {
                double heatTransfer = baseHeat
                        * directionFactor
                        * materialFactor
                        * distanceFactor
                        * windFactor
                        * slopeFactor
                        * weatherFactor
                        * fuelDensityFactor
                        * chemicalFactor;
                double randomness = 0.4 + this.level.getRandom().nextDouble() * 1.2;
                return heatTransfer * randomness;
            }
        }

        private double calculateDirectionFactor(BlockPos sourcePos, BlockPos targetPos) {
            double factor = 1.0;
            if (targetPos.getY() > sourcePos.getY()) {
                factor *= 2.0;
            } else if (targetPos.getY() == sourcePos.getY()) {
                factor *= 1.0;
                double windEffect = this.calculateWindEffect(sourcePos, targetPos);
                factor *= 1.0 + windEffect * 0.5;
            } else {
                factor *= 0.3;
            }

            BlockState targetState = this.level.getBlockState(targetPos);
            if (this.isWoodBlock(targetState)) {
                factor *= this.calculateLogAxisFactor(sourcePos, targetPos);
            }

            return factor;
        }

        private double calculateWindEffect(BlockPos sourcePos, BlockPos targetPos) {
            double dx = (double) (targetPos.getX() - sourcePos.getX());
            double dz = (double) (targetPos.getZ() - sourcePos.getZ());
            double magnitude = Math.sqrt(dx * dx + dz * dz);
            if (magnitude == 0.0) {
                return 0.0;
            } else {
                double normalizedDx = dx / magnitude;
                double normalizedDz = dz / magnitude;
                double dotProduct = normalizedDx * this.windDirection.x + normalizedDz * this.windDirection.z;
                return Math.max(-0.8, Math.min(0.8, dotProduct * this.windSpeed));
            }
        }

        private double calculateLogAxisFactor(BlockPos sourcePos, BlockPos targetPos) {
            double dx = (double) Math.abs(targetPos.getX() - sourcePos.getX());
            double dz = (double) Math.abs(targetPos.getZ() - sourcePos.getZ());
            if (dx > dz) {
                return 1.3;
            } else {
                return dz > dx ? 1.3 : 1.0;
            }
        }

        private double getMaterialConductivityFactor(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            if (this.isWoodBlock(state)) {
                return 1.2;
            } else if (this.isLeafBlock(state)) {
                return 1.0;
            } else if (this.isPlantBlock(state)) {
                return 0.9;
            } else {
                int flammability = FirePropagationManager.getFlammability(state);
                return flammability > 0 ? 0.5 + (double) flammability / 200.0 : 0.2;
            }
        }

        private double calculateSlopeFactor(BlockPos sourcePos, BlockPos targetPos) {
            double heightDiff = (double) (targetPos.getY() - sourcePos.getY());
            if (heightDiff > 0.0) {
                return 1.0 + heightDiff * 0.15;
            } else {
                return heightDiff < 0.0 ? Math.max(0.3, 1.0 + heightDiff * 0.1) : 1.0;
            }
        }

        private double calculateWeatherFactor(BlockPos pos) {
            if (!this.level.isRaining()) {
                return 1.2;
            } else {
                boolean isRaining = this.level.hasRain(pos);
                return isRaining ? 0.4 : 0.7;
            }
        }

        private double calculateFuelDensityFactor(BlockPos pos) {
            int combustibleCount = 0;
            int totalCount = 0;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        BlockPos checkPos = pos.add(dx, 0, dz);
                        BlockState state = this.level.getBlockState(checkPos);
                        totalCount++;
                        if (this.isWoodBlock(state)
                                || this.isLeafBlock(state)
                                || this.isPlantBlock(state)
                                || this.isGrassBlock(state)
                                || FirePropagationManager.getFlammability(state) > 0) {
                            combustibleCount++;
                        }
                    }
                }
            }

            double fuelDensity = (double) combustibleCount / (double) totalCount;
            return 0.8 + fuelDensity * 0.8;
        }

        private BlockPos[] getRandomNeighborsWithBias(BlockPos pos) {
            BlockPos[] allNeighbors = this.getAllNeighbors(pos);
            List<BlockPos> selectedNeighbors = new ArrayList<>();

            for (BlockPos neighbor : allNeighbors) {
                double probability = this.getNeighborSelectionProbability(pos, neighbor);
                double randomFactor = 0.2 + this.level.getRandom().nextDouble() * 0.8;
                if (this.level.getRandom().nextDouble() < probability * randomFactor) {
                    selectedNeighbors.add(neighbor);
                }
            }

            if (selectedNeighbors.isEmpty() && allNeighbors.length > 0) {
                selectedNeighbors.add(allNeighbors[this.level.getRandom().nextInt(allNeighbors.length)]);
            }

            return selectedNeighbors.toArray(new BlockPos[0]);
        }

        private double getNeighborSelectionProbability(BlockPos source, BlockPos target) {
            double probability = 0.45;
            if (target.getY() > source.getY()) {
                probability += 0.25;
            }

            double windEffect = this.calculateWindEffect(source, target);
            if (windEffect > 0.0) {
                probability += windEffect * 0.5;
            }

            double directionBias = 0.7 + this.level.getRandom().nextDouble() * 0.8;
            probability *= directionBias;
            return Math.min(0.95, probability);
        }

        private double calculateDistanceFactor(BlockPos sourcePos, BlockPos targetPos) {
            double distance = Math.sqrt(
                    Math.pow((double) (targetPos.getX() - sourcePos.getX()), 2.0)
                            + Math.pow((double) (targetPos.getY() - sourcePos.getY()), 2.0)
                            + Math.pow((double) (targetPos.getZ() - sourcePos.getZ()), 2.0)
            );
            return Math.max(0.1, 1.0 / (1.0 + 0.5 * distance + 0.1 * distance * distance));
        }

        private double getChemicalFactor(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            if (state.getFluidState().isIn(FluidTags.WATER)) {
                return 0.0;
            } else if (state.isOf(Blocks.TNT)) {
                return 15.0;
            } else if (state.getFluidState().isIn(FluidTags.LAVA)) {
                return 2.0;
            } else {
                return !state.isOf(Blocks.MAGMA_BLOCK)
                        && !state.isOf(Blocks.NETHERRACK)
                        && !state.isOf(Blocks.SOUL_SAND)
                        && !state.isOf(Blocks.SOUL_SOIL)
                        ? 1.0
                        : 0.2;
            }
        }

        private void applyHeatToNeighbor(BlockPos neighborPos, double heatApplied, int sourceIntensity) {
            if (!(heatApplied <= 0.1)) {
                int currentResistance = this.thermalResistance.getOrDefault(neighborPos, this.getDefaultThermalResistance(neighborPos));
                int currentFuel = this.fuelAmount.getOrDefault(neighborPos, this.getDefaultFuelAmount(neighborPos));
                int heatReduction = Math.max(1, (int) (heatApplied * 0.5));
                int newResistance = Math.max(0, currentResistance - heatReduction);
                this.thermalResistance.put(neighborPos, newResistance);
                if (newResistance <= 0 && currentFuel > 0) {
                    this.fireIntensity.put(neighborPos, sourceIntensity);
                    this.setState(neighborPos, FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE);
                    this.thermalResistance.put(neighborPos, this.getDefaultThermalResistance(neighborPos) / 3);
                    this.nextQueue.offer(neighborPos);
                    this.spawnFireParticles(neighborPos);
                }
            }
        }

        private double calculateWindFactor(BlockPos sourcePos, BlockPos targetPos) {
            double windEffect = this.calculateWindEffect(sourcePos, targetPos);
            return 1.0 + windEffect * 0.5;
        }

        private boolean canIgniteBlock(BlockPos pos) {
            FirePropagationManager.FirePropagationSystem.CellState currentState = this.getState(pos);
            return (
                    currentState == FirePropagationManager.FirePropagationSystem.CellState.EMPTY
                            || currentState == FirePropagationManager.FirePropagationSystem.CellState.INFLAMMABLE
            )
                    && this.isBlockInflammable(pos);
        }

        private boolean isBlockInflammable(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            return state.isAir() ? true : state.getFluidState().isEmpty();
        }

        private int getDefaultThermalResistance(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir()) {
                return 0;
            } else if (this.isGrassBlock(state)) {
                return 20;
            } else if (this.isWoodBlock(state)) {
                return 45;
            } else if (this.isLeafBlock(state)) {
                return 25;
            } else if (this.isPlantBlock(state)) {
                return 35;
            } else {
                int flammability = FirePropagationManager.getFlammability(state);
                return flammability > 0 ? Math.max(3, 150 - flammability) : 200;
            }
        }

        private int getDefaultFuelAmount(BlockPos pos) {
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir()) {
                return 0;
            } else if (this.isGrassBlock(state)) {
                return 60;
            } else if (this.isWoodBlock(state)) {
                return 500;
            } else if (this.isLeafBlock(state)) {
                return 120;
            } else if (this.isPlantBlock(state)) {
                return 40;
            } else {
                int flammability = FirePropagationManager.getFlammability(state);
                return flammability > 0 ? Math.max(3, flammability / 3) : 20;
            }
        }

        private boolean isWoodBlock(BlockState state) {
            return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.PLANKS);
        }

        private boolean isLeafBlock(BlockState state) {
            return state.isIn(BlockTags.LEAVES);
        }

        private boolean isPlantBlock(BlockState state) {
            return state.isIn(BlockTags.FLOWERS)
                    || state.isIn(BlockTags.SAPLINGS)
                    || state.isIn(BlockTags.CROPS)
                    || state.isOf(Blocks.SHORT_GRASS)
                    || state.isOf(Blocks.TALL_GRASS)
                    || state.isOf(Blocks.FERN);
        }

        private boolean isGrassBlock(BlockState state) {
            return state.isOf(Blocks.GRASS_BLOCK);
        }

        private void consumeFuelAndCheckBurnout(BlockPos pos) {
            int currentFuel = this.fuelAmount.getOrDefault(pos, 0);
            if (currentFuel > 0) {
                int heat = this.heatLevel.getOrDefault(pos, 50);
                int intensity = this.fireIntensity.getOrDefault(pos, 2);
                double intensityBurn = 0.5 + (double) intensity / 10.0;
                BlockState state = this.level.getBlockState(pos);
                int fuelConsumptionRate = 1;
                if (this.isGrassBlock(state)) {
                    fuelConsumptionRate = Math.max(1, (int) ((double) (heat / 25) * intensityBurn));
                } else if (!this.isLeafBlock(state) && !this.isPlantBlock(state)) {
                    fuelConsumptionRate = Math.max(1, (int) ((double) (heat / 35) * intensityBurn));
                } else {
                    fuelConsumptionRate = Math.max(1, (int) ((double) (heat / 30) * intensityBurn));
                }

                int newFuel = Math.max(0, currentFuel - fuelConsumptionRate);
                this.fuelAmount.put(pos, newFuel);
                if (newFuel <= 0) {
                    this.setState(pos, FirePropagationManager.FirePropagationSystem.CellState.BURNED);
                    this.replaceBurnedBlock(pos, state);
                } else {
                    int currentHeat = this.heatLevel.getOrDefault(pos, 50);
                    int newHeat = (int) Math.max(20.0, (double) currentHeat - 0.1);
                    this.heatLevel.put(pos, newHeat);
                }
            }
        }

        private void updateBurningEffects(BlockPos pos) {
            int lastUpdateTick = this.lastBurnEffectTick.getOrDefault(pos, -100);
            if (this.currentTickCount - lastUpdateTick >= 16) {
                this.lastBurnEffectTick.put(pos, this.currentTickCount);
                BlockState currentState = this.level.getBlockState(pos);
                if (!currentState.isAir() && !(currentState.getBlock() instanceof FireBlock)) {
                    int currentProgress = this.burnProgress.getOrDefault(pos, 0);
                    if (this.getState(pos) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE || this.hasNearbyFire(pos)) {
                        currentProgress = Math.min(100, currentProgress + 15);
                    } else if (this.hasNearbyFire(pos)) {
                        currentProgress = Math.min(100, currentProgress + 6);
                    }

                    this.burnProgress.put(pos, currentProgress);
                    if (currentProgress > 0) {
                        this.applyBurningVisualEffect(pos, currentState, currentProgress);
                    }

                    if (currentProgress >= 100) {
                        this.burnProgress.put(pos, 99);
                    }
                }
            }
        }

        private void replaceBurnedBlock(BlockPos pos, BlockState state) {
            if (this.isGrassBlock(state)) {
                this.level.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
            } else if (this.isWoodBlock(state)) {
                BlockState deadState = ExplosionUtil.getDeadBlockVariant(state);
                this.level.setBlockState(pos, deadState, 3);
                ExplosionUtil.transformBlockToFallingBlock(this.level, (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, 1.0F);
            } else if (!this.isLeafBlock(state) && !this.isPlantBlock(state)) {
                this.level.breakBlock(pos, false);
            } else {
                this.level.removeBlock(pos, false);
            }
        }

        private boolean hasNearbyFire(BlockPos pos) {
            return this.getState(pos.up()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE
                    || this.getState(pos.down()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE
                    || this.getState(pos.north()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE
                    || this.getState(pos.south()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE
                    || this.getState(pos.east()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE
                    || this.getState(pos.west()) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE;
        }

        private void applyBurningVisualEffect(BlockPos pos, BlockState currentState, int progress) {
            ServerWorld serverLevel = this.level;
            if (progress > 0 && progress <= 33) {
                if (this.level.getRandom().nextDouble() < 0.1) {
                    this.spawnBurningParticles(serverLevel, pos, progress, "early");
                }
            } else if (progress > 33 && progress <= 66) {
                if (this.level.getRandom().nextDouble() < 0.15) {
                    this.spawnBurningParticles(serverLevel, pos, progress, "mid");
                }
            } else if (progress > 66 && progress < 100 && this.level.getRandom().nextDouble() < 0.2) {
                this.spawnBurningParticles(serverLevel, pos, progress, "late");
            }
        }

        private void spawnBurningParticles(ServerWorld serverLevel, BlockPos pos, int progress, String stage) {
            Random rnd = serverLevel.getRandom();
            double x = (double) pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.35;
            double y = (double) pos.getY() + 0.5 + rnd.nextDouble() * 0.25;
            double z = (double) pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.35;
            switch (stage) {
                case "early":
                    ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMALL_FIRE_PARTICLE.get(), x, y, z, 1, 0.025, 0.035, 0.025);
                    break;
                case "mid":
                    ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_FLAME_PARTICLE.get(), x, y, z, 1, 0.03, 0.04, 0.03);
                    ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMALL_FIRE_PARTICLE.get(), x, y, z, 1, 0.02, 0.03, 0.02);
                    if (serverLevel.getRandom().nextDouble() < 0.7) {
                        ParticleUtil.spawnLongDistanceParticles(
                                serverLevel, ModParticles.FIRE_SMOKE_PARTICLE.get(), x, y, z, 1, 0.025, 0.025, 0.025
                        );
                    }
                    break;
                case "late":
                    ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.FIRE_SMOKE_PARTICLE.get(), x, y, z, 1, 0.03, 0.035, 0.03);
                    if (serverLevel.getRandom().nextDouble() < 0.3) {
                        ParticleUtil.spawnLongDistanceParticles(serverLevel, ModParticles.SMALL_FIRE_PARTICLE.get(), x, y, z, 1, 0.02, 0.03, 0.02);
                    }
                    break;
            }
        }

        private FirePropagationManager.FirePropagationSystem.CellState getState(BlockPos pos) {
            return this.cellStates.getOrDefault(pos, FirePropagationManager.FirePropagationSystem.CellState.EMPTY);
        }

        private void setState(BlockPos pos, FirePropagationManager.FirePropagationSystem.CellState newState) {
            this.cellStates.put(pos, newState);
            if (newState == FirePropagationManager.FirePropagationSystem.CellState.INFLAMMABLE && !this.thermalResistance.containsKey(pos)) {
                this.thermalResistance.put(pos, this.getDefaultThermalResistance(pos));
                this.fuelAmount.put(pos, this.getDefaultFuelAmount(pos));
                BlockState state = this.level.getBlockState(pos);
                if (this.isGrassBlock(state)) {
                    this.level.setBlockState(pos, Blocks.DIRT.getDefaultState(), 3);
                }
            }

            if (newState == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                int currentIntensity = this.fireIntensity.getOrDefault(pos, 0);
                this.fireIntensity.put(pos, Math.max(currentIntensity, Math.min(20, currentIntensity + 2)));
                this.heatLevel.put(pos, 100);
                if (!this.fuelAmount.containsKey(pos)) {
                    this.fuelAmount.put(pos, this.getDefaultFuelAmount(pos));
                }

                if (!this.thermalResistance.containsKey(pos)) {
                    this.thermalResistance.put(pos, this.getDefaultThermalResistance(pos));
                }
            } else if (newState == FirePropagationManager.FirePropagationSystem.CellState.BURNED) {
                this.fireIntensity.remove(pos);
                this.heatLevel.remove(pos);
                this.thermalResistance.remove(pos);
                this.fuelAmount.remove(pos);
            }

            this.applyStateToWorld(pos, newState);
        }

        private void applyStateToWorld(BlockPos pos, FirePropagationManager.FirePropagationSystem.CellState state) {
            switch (state) {
                case PARTICLE_FIRE:
                    if (this.level.isChunkLoaded(pos)) {
                        this.spawnFireParticles(pos);
                    }
                case EMPTY:
                case INFLAMMABLE:
                case BURNED:
            }
        }

        private void spawnFireParticles(BlockPos pos) {
            double x = (double) pos.getX() + 0.5;
            double y = (double) pos.getY() + 0.5;
            double z = (double) pos.getZ() + 0.5;
            BlockState state = this.level.getBlockState(pos);
            double nearestPlayerDistSq = Double.MAX_VALUE;

            for (PlayerEntity p : this.level.getPlayers()) {
                double d = p.squaredDistanceTo(x, y, z);
                if (d < nearestPlayerDistSq) {
                    nearestPlayerDistSq = d;
                }
            }

            int lodTier;
            if (nearestPlayerDistSq < 2500.0) {
                lodTier = 0;
            } else if (nearestPlayerDistSq < 14400.0) {
                lodTier = 1;
            } else if (nearestPlayerDistSq < 57600.0) {
                lodTier = 2;
            } else {
                lodTier = 3;
            }

            int totalFires = this.pendingQueue.size() + this.nextQueue.size();
            double bigFireScale = totalFires > 300 ? Math.max(0.35, 300.0 / (double) totalFires) : 1.0;
            int nearbyFires = this.countNearbyFires(pos);
            boolean isMinorFuel = this.isGrassBlock(state) || this.isPlantBlock(state) || state.isAir();
            boolean isMajorFuel = this.isWoodBlock(state) || this.isLeafBlock(state);
            int intensity = this.fireIntensity.getOrDefault(pos, 2);
            double intensityNorm = (double) intensity / 20.0;
            double intensityBonus = (double) intensity * 0.12;
            double fireSizeFactor;
            if (isMinorFuel) {
                fireSizeFactor = Math.min(2.0, 0.45 + intensityBonus);
            } else if (isMajorFuel) {
                fireSizeFactor = 0.6 + (double) nearbyFires * 0.35 + intensityBonus * 0.5;
            } else {
                fireSizeFactor = 0.35 + (double) nearbyFires * 0.25 + intensityBonus * 0.3;
            }

            double probability = 0.04 + 0.06 * fireSizeFactor + intensityNorm * 0.15;
            if (isMinorFuel) {
                probability = Math.max(0.7, probability);
            } else {
                probability *= 0.5;
            }

            probability = Math.min(1.0, probability * (double) Config.particleDensityMultiplier);
            double lodProb = lodTier == 0 ? 1.0 : (lodTier == 1 ? 0.7 : (lodTier == 2 ? 0.4 : 0.18));
            probability *= lodProb * bigFireScale;
            if (this.level.getRandom().nextDouble() < probability) {
                try {
                    double densityFactor = Math.min(1.0, (double) nearbyFires / 20.0);
                    int baseIterations = isMinorFuel ? 2 : 1 + (int) (0.75 * densityFactor);
                    int intensityExtra = (int) (intensityNorm * 2.0);
                    int iterations = Math.min(5, baseIterations + intensityExtra);
                    if (Config.isPotatoMode()) {
                        iterations = Math.min(iterations, 2);
                    }

                    if (lodTier >= 2) {
                        iterations = Math.max(1, iterations / 2);
                    }

                    if (lodTier == 3) {
                        iterations = 1;
                    }

                    double spreadMultiplier = isMinorFuel ? 0.6 : 1.0 + 0.8 * densityFactor + intensityNorm * 0.5;
                    double upwardVelocity = isMinorFuel ? 0.06 : intensityNorm * 0.04;
                    if (lodTier >= 2 && !isMinorFuel) {
                        upwardVelocity += 0.08 + intensityNorm * 0.05;
                    }

                    for (int i = 0; i < iterations; i++) {
                        double heightOffset = (double) i * (isMinorFuel ? 0.1 : 0.25) * spreadMultiplier;
                        double offsetX = (this.level.getRandom().nextDouble() - 0.5) * (isMinorFuel ? 0.15 : 0.4) * spreadMultiplier;
                        double offsetY = this.level.getRandom().nextDouble() * (isMinorFuel ? 0.05 : 0.15) * spreadMultiplier + heightOffset;
                        double offsetZ = (this.level.getRandom().nextDouble() - 0.5) * (isMinorFuel ? 0.15 : 0.4) * spreadMultiplier;
                        SimpleParticleType particleType = isMinorFuel ? this.cachedSmallFire : this.cachedFireFlame;
                        ParticleUtil.spawnLongDistanceParticles(this.level, particleType, x + offsetX, y + offsetY, z + offsetZ, 1, 0.0, upwardVelocity, 0.0);
                        if (i > 0 && !isMinorFuel && lodTier <= 1) {
                            ParticleUtil.spawnLongDistanceParticles(
                                    this.level, this.cachedSmallFire, x + offsetX * 0.6, y + offsetY + 0.15, z + offsetZ * 0.6, 1, 0.0, 0.0, 0.0
                            );
                        }
                    }

                    if (isMajorFuel && lodTier == 0 && intensityNorm > 0.35 && this.level.getRandom().nextDouble() < 0.18 * fireSizeFactor) {
                        double crownH = 0.6 + this.level.getRandom().nextDouble() * 1.2;
                        ParticleUtil.spawnLongDistanceParticles(
                                this.level,
                                this.cachedFireFlame,
                                x + (this.level.getRandom().nextDouble() - 0.5) * 0.25,
                                y + crownH,
                                z + (this.level.getRandom().nextDouble() - 0.5) * 0.25,
                                1,
                                0.0,
                                0.15 + intensityNorm * 0.1,
                                0.0
                        );
                    }
                } catch (Exception var48) {
                    this.level.spawnParticles(ParticleTypes.FLAME, x, y, z, isMinorFuel ? 1 : 3, 0.1, 0.2, 0.1, 0.01);
                }
            }

            if (lodTier <= 1 && this.level.getRandom().nextDouble() < 0.05) {
                SoundUtil.playAt(
                        this.level,
                        (double) pos.getX() + 0.5,
                        (double) pos.getY() + 0.5,
                        (double) pos.getZ() + 0.5,
                        SoundEvents.BLOCK_FIRE_AMBIENT,
                        SoundCategory.BLOCKS,
                        1.8F,
                        0.8F + this.level.getRandom().nextFloat() * 0.4F
                );
            }

            double emberProb = 0.05 * fireSizeFactor * lodProb * bigFireScale;
            if (isMajorFuel && this.level.getRandom().nextDouble() < emberProb) {
                ParticleUtil.spawnLongDistanceParticles(
                        this.level,
                        this.cachedEmber,
                        x + (this.level.getRandom().nextDouble() - 0.5),
                        y + 0.1,
                        z + (this.level.getRandom().nextDouble() - 0.5),
                        1,
                        (this.level.getRandom().nextDouble() - 0.5) * 0.05,
                        0.05 + this.level.getRandom().nextDouble() * 0.1,
                        (this.level.getRandom().nextDouble() - 0.5) * 0.05
                );
            }

            double smokeProb = 0.015 * fireSizeFactor;
            if (lodTier >= 2) {
                smokeProb *= 0.55;
            }

            smokeProb *= bigFireScale;
            if (isMajorFuel && this.level.getRandom().nextDouble() < smokeProb) {
                double smokeHeight = 1.5 + this.level.getRandom().nextDouble() * 2.0;
                double smokeYd = 0.04 + this.level.getRandom().nextDouble() * 0.03 + (lodTier >= 2 ? 0.04 : 0.0);
                ParticleUtil.spawnLongDistanceParticles(
                        this.level,
                        this.cachedFireSmoke,
                        x + (this.level.getRandom().nextDouble() - 0.5) * 0.5,
                        y + smokeHeight,
                        z + (this.level.getRandom().nextDouble() - 0.5) * 0.5,
                        1,
                        0.0,
                        smokeYd,
                        0.0
                );
            }
        }

        private void spawnParticlesForActiveFires(int displayBudget) {
            if (!this.level.getPlayers().isEmpty()) {
                int maxBlocksPerTick = Math.min(displayBudget, 100);
                if (Config.isPotatoMode()) {
                    maxBlocksPerTick = Math.min(maxBlocksPerTick, 28);
                }

                maxBlocksPerTick = Math.max(1, (int) ((float) maxBlocksPerTick * Math.min(1.0F, Config.particleDensityMultiplier)));
                int qs = this.pendingQueue.size();
                int skip1 = qs == 0 ? 0 : this.currentTickCount * maxBlocksPerTick % qs;
                int used = this.spawnParticlesForQueueSlice(this.pendingQueue, maxBlocksPerTick, skip1);
                if (used < maxBlocksPerTick) {
                    int qn = this.nextQueue.size();
                    int skip2 = qn == 0 ? 0 : (this.currentTickCount * 13 + 7) * maxBlocksPerTick % qn;
                    this.spawnParticlesForQueueSlice(this.nextQueue, maxBlocksPerTick - used, skip2);
                }
            }
        }

        private int spawnParticlesForQueueSlice(Queue<BlockPos> queue, int maxBlocks, int skipCount) {
            if (maxBlocks > 0 && !queue.isEmpty()) {
                int queueSize = queue.size();
                skipCount %= Math.max(1, queueSize);
                Iterator<BlockPos> it = queue.iterator();

                for (int i = 0; i < skipCount && it.hasNext(); i++) {
                    it.next();
                }

                int processedCount = 0;

                while (it.hasNext() && processedCount < maxBlocks) {
                    BlockPos pos = it.next();
                    if (this.getState(pos) == FirePropagationManager.FirePropagationSystem.CellState.PARTICLE_FIRE) {
                        processedCount++;
                        if (this.level.getRandom().nextDouble() < 0.25) {
                            this.updateBurningEffects(pos);
                        }

                        if (this.level.getRandom().nextDouble() < 0.2) {
                            this.spawnFireParticles(pos);
                        }

                        if (this.level.getRandom().nextDouble() < 0.05) {
                            this.updateFireEffects(pos);
                        }
                    }
                }

                return processedCount;
            } else {
                return 0;
            }
        }

        private enum CellState {
            EMPTY,
            INFLAMMABLE,
            PARTICLE_FIRE,
            BURNED
        }
    }
}
