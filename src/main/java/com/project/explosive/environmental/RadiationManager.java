package com.project.explosive.environmental;

import com.project.explosive.block.RadioactiveAshBlock;
import com.project.explosive.network.RadiationSyncPacket;
import com.project.explosive.util.SoundUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

public class RadiationManager {
    private static final Map<BlockPos, RadiationManager.RadiationSource> radiationSources = new HashMap<>();
    private static final Map<UUID, RadiationManager.PlayerRadiationData> playerRadiationData = new HashMap<>();
    private static final Map<UUID, RadiationManager.EntityRadiationData> entityRadiationData = new HashMap<>();

    public static final double DOSE_NEGLIGIBLE = 0.1;
    public static final double DOSE_MILD = 0.5;
    public static final double DOSE_MODERATE = 1.0;
    public static final double DOSE_SEVERE = 2.0;
    public static final double DOSE_CRITICAL = 4.0;
    public static final double DOSE_LETHAL = 6.0;
    public static final double DOSE_SUPRALETHAL = 10.0;
    public static final int STAGE_HEALTHY = 0;
    public static final int STAGE_PRODROMAL = 1;
    public static final int STAGE_LATENT = 2;
    public static final int STAGE_MANIFEST = 3;
    public static final int STAGE_TERMINAL = 4;
    public static final double PROTECTION_NONE = 1.0;
    public static final double PROTECTION_BASIC = 0.7;
    public static final double PROTECTION_ADVANCED = 0.3;
    public static final double PROTECTION_FULL = 0.1;
    private static final double SPAWN_BLOCK_RADIATION = 1.0;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            RadiationSavedData data = RadiationSavedData.get(world);
            data.markDirty();
            onWorldTick(world);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            onLivingDeath(entity);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clearPlayerRadiation(handler.player);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            clearPlayerRadiation(player);
        });
    }

    public static RadiationManager.EntityRadiationData getEntityRadiationData(LivingEntity entity) {
        return entityRadiationData.computeIfAbsent(entity.getUuid(), k -> new RadiationManager.EntityRadiationData());
    }

    public static void addRadiationSource(BlockPos pos, double intensity, int duration) {
        if (EnvironmentalConfig.areRadiationEffectsEnabled()) {
            int adjustedDuration = duration * EnvironmentalConfig.getRadiationDurationFactor();
            radiationSources.put(
                    pos, new RadiationManager.RadiationSource((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), intensity, adjustedDuration)
            );
        }
    }

    public static double getRadiationAtPosition(World level, BlockPos pos) {
        double totalRadiation = 0.0;

        for (RadiationManager.RadiationSource source : radiationSources.values()) {
            if (source.isActive()) {
                double dx = (double) pos.getX() - source.getX();
                double dy = (double) pos.getY() - source.getY();
                double dz = (double) pos.getZ() - source.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double radiationAtPos = source.getIntensity() / (1.0 + distance * distance * 0.01);
                totalRadiation += radiationAtPos;
            }
        }

        return Math.min(totalRadiation, 100.0);
    }

    public static double getRadiationFromNearbyAsh(World level, BlockPos centerPos, int radius) {
        double totalRadiation = 0.0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = centerPos.add(x, y, z);
                    if (level.getBlockState(checkPos).getBlock() instanceof RadioactiveAshBlock) {
                        double distance = Math.sqrt((double) (x * x + y * y + z * z));
                        if (distance == 0.0) {
                            distance = 0.5;
                        }

                        int layers = level.getBlockState(checkPos).get(RadioactiveAshBlock.LAYERS);
                        double radiation = (double) layers * 2.0E-4 / (1.0 + distance * distance * 0.1);
                        totalRadiation += radiation;
                    }
                }
            }
        }

        return totalRadiation;
    }

    public static RadiationManager.PlayerRadiationData getPlayerRadiationData(PlayerEntity player) {
        return playerRadiationData.computeIfAbsent(player.getUuid(), uuid -> new RadiationManager.PlayerRadiationData());
    }

    public static double getPlayerProtectionFactor(PlayerEntity player) {
        double protection = 1.0;

        for (ItemStack stack : player.getArmorItems()) {
            if (stack.getItem().getTranslationKey().contains("iron")) {
                protection = Math.min(protection, 0.7);
            }
        }

        return protection;
    }

    public static void applyRadiationEffects(PlayerEntity player) {
        if (EnvironmentalConfig.areRadiationEffectsEnabled()) {
            BlockPos playerPos = player.getBlockPos();
            double radiationLevel = getRadiationAtPosition(player.getWorld(), playerPos);
            RadiationManager.PlayerRadiationData data = getPlayerRadiationData(player);
            if (radiationLevel > 0.1) {
                double dosePerTick = radiationLevel * 5.0E-4;
                data.addDose(dosePerTick);
            }

            int gameTick = (int) player.getWorld().getTime();
            if (gameTick % 10 == 0) {
                double ashRadiation = getRadiationFromNearbyAsh(player.getWorld(), playerPos, 5);
                if (ashRadiation > 0.0) {
                    data.addDose(ashRadiation * 10.0);
                }
            }

            int arsStage = data.getArsStage();
            if (radiationLevel > 0.5 && data.getDetectorTickCooldown() <= 0 && player.getWorld() instanceof ServerWorld serverLevel) {
                int clickDelay = Math.max(2, (int) (40.0 / radiationLevel));
                BlockPos bp = player.getBlockPos();
                SoundUtil.playAt(
                        serverLevel,
                        (double) bp.getX() + 0.5,
                        (double) bp.getY() + 1.0,
                        (double) bp.getZ() + 0.5,
                        SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,
                        SoundCategory.PLAYERS,
                        0.2F,
                        1.8F + (float) (radiationLevel * 0.01)
                );
                data.setDetectorTickCooldown(clickDelay);
            }

            if (arsStage == 1) {
                if (gameTick % 200 == 0) {
                    player.getHungerManager().setFoodLevel(Math.max(6, player.getHungerManager().getFoodLevel() - 1));
                }

                if (gameTick % 120 == 0) {
                    player.damage(player.getDamageSources().magic(), 0.5F);
                }
            }

            if (arsStage == 2 && gameTick % 400 == 0) {
                player.getHungerManager().setSaturationLevel(0.0F);
            }

            if (arsStage == 3) {
                if (data.hasBoneMarrowSyndrome() && gameTick % 60 == 0) {
                    player.damage(player.getDamageSources().magic(), 1.0F);
                    if (player.getWorld() instanceof ServerWorld serverLevel) {
                        serverLevel.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                    }
                }

                if (data.hasGastrointestinalSyndrome() && gameTick % 40 == 0) {
                    player.damage(player.getDamageSources().magic(), 2.0F);
                    player.getHungerManager().setFoodLevel(Math.max(0, player.getHungerManager().getFoodLevel() - 2));
                }
            }

            if (arsStage == 4) {
                if (data.hasCerebrovascularSyndrome()) {
                    if (gameTick % 10 == 0) {
                        player.damage(player.getDamageSources().magic(), 3.0F);
                    }
                } else if (gameTick % 20 == 0) {
                    player.damage(player.getDamageSources().magic(), 2.0F);
                }
            }
        }
    }

    public static void clearPlayerRadiation(PlayerEntity player) {
        playerRadiationData.remove(player.getUuid());
    }

    public static void applyRadiationToEntities(ServerWorld level) {
        if (EnvironmentalConfig.areRadiationEffectsEnabled()) {
            long gameTick = level.getTime();
            if (gameTick % 20L == 0L) {
                for (ServerPlayerEntity sp : level.getPlayers()) {
                    for (LivingEntity entity : level.getEntitiesByClass(LivingEntity.class, new Box(sp.getBlockPos()).expand(32.0), e -> true)) {
                        if (!(entity instanceof PlayerEntity)) {
                            BlockPos ep = entity.getBlockPos();
                            if (level.getBlockState(ep).getBlock() instanceof RadioactiveAshBlock || level.getBlockState(ep.down()).getBlock() instanceof RadioactiveAshBlock) {
                                RadiationManager.EntityRadiationData data = getEntityRadiationData(entity);
                                data.addDose(0.005);
                            }
                        }
                    }
                }
            }

            if (gameTick % 10L == 0L) {
                for (RadiationManager.RadiationSource source : radiationSources.values()) {
                    if (source.isActive()) {
                        double radius = Math.sqrt(source.getIntensity()) * 5.0;
                        double sx = source.getX();
                        double sy = source.getY();
                        double sz = source.getZ();
                        BlockPos sourcePos = BlockPos.ofFloored(sx, sy, sz);

                        for (LivingEntity entityx : level.getEntitiesByClass(LivingEntity.class, new Box(sourcePos).expand(radius), e -> true)) {
                            if (!(entityx instanceof PlayerEntity)) {
                                double dx = entityx.getX() - sx;
                                double dy = entityx.getY() - sy;
                                double dz = entityx.getZ() - sz;
                                double distSq = dx * dx + dy * dy + dz * dz;
                                double dosePerTick = source.getIntensity() * 1.0E-4 / (1.0 + distSq * 0.005);
                                RadiationManager.EntityRadiationData data = getEntityRadiationData(entityx);
                                data.addDose(dosePerTick);
                            }
                        }
                    }
                }
            }

            if (gameTick % 20L == 0L) {
                for (Entry<UUID, RadiationManager.EntityRadiationData> entry : new ArrayList<>(entityRadiationData.entrySet())) {
                    RadiationManager.EntityRadiationData data = entry.getValue();
                    if (data.isContaminated()) {
                        Entity e = level.getEntity(entry.getKey());
                        if (e instanceof LivingEntity entityxx) {
                            double spreadDose = data.getDose() * 0.001;
                            if (!(spreadDose < 1.0E-4)) {
                                for (LivingEntity nearby : level.getEntitiesByClass(LivingEntity.class, entityxx.getBoundingBox().expand(2.0), ent -> true)) {
                                    if (nearby != entityxx) {
                                        if (nearby instanceof PlayerEntity player) {
                                            RadiationManager.PlayerRadiationData playerData = getPlayerRadiationData(player);
                                            playerData.addDose(spreadDose * 0.5);
                                        } else {
                                            RadiationManager.EntityRadiationData nearbyData = getEntityRadiationData(nearby);
                                            nearbyData.addDose(spreadDose);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (gameTick % 20L == 0L) {
                Iterator<Entry<UUID, RadiationManager.EntityRadiationData>> iterator = entityRadiationData.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<UUID, RadiationManager.EntityRadiationData> entryx = iterator.next();
                    RadiationManager.EntityRadiationData data = entryx.getValue();
                    data.tick();
                    if (level.getEntity(entryx.getKey()) instanceof LivingEntity living && living.isAlive()) {
                        double dose = data.getDose();
                        if (dose < 0.01) {
                            iterator.remove();
                            continue;
                        }
                        float glow = data.getGlowIntensity();
                        if (data.isContaminated() && gameTick % 10L == 0L) {
                            level.spawnParticles(
                                    ParticleTypes.ASH, living.getX(), living.getY() + (double) living.getHeight() * 0.5, living.getZ(), 2, 0.3, 0.5, 0.3, 0.01
                            );
                            if (glow > 0.5F) {
                                level.spawnParticles(
                                        EntityEffectParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 0.3F, 1.0F, 0.3F),
                                        living.getX(),
                                        living.getY() + (double) living.getHeight() * 0.8,
                                        living.getZ(),
                                        1,
                                        0.4,
                                        0.2,
                                        0.4,
                                        0.0
                                );
                            }
                        }

                        if (dose >= 2.0) {
                            living.damage(living.getDamageSources().magic(), 1.0F + (float) (dose * 0.5));
                            if (gameTick % 100L == 0L) {
                                living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1, false, false));
                            }
                        }

                        if (dose >= 4.0) {
                            living.damage(living.getDamageSources().magic(), 2.0F);
                        }

                        if (dose >= 6.0) {
                            living.damage(living.getDamageSources().magic(), 4.0F);
                        }
                        continue;
                    }

                    iterator.remove();
                }
            }
        }
    }

    public static RadiationManager.RadiationSource getNearestSource(BlockPos pos) {
        RadiationManager.RadiationSource nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (RadiationManager.RadiationSource source : radiationSources.values()) {
            if (source.isActive()) {
                double dist = Math.sqrt(
                        Math.pow((double) pos.getX() - source.getX(), 2.0)
                                + Math.pow((double) pos.getY() - source.getY(), 2.0)
                                + Math.pow((double) pos.getZ() - source.getZ(), 2.0)
                );
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = source;
                }
            }
        }

        return nearest;
    }

    public static boolean isAreaRadioactive(BlockPos pos, double threshold) {
        return getRadiationAtPosition(null, pos) > threshold;
    }

    public static void onWorldTick(ServerWorld serverLevel) {
        applyRadiationToEntities(serverLevel);
        for (ServerPlayerEntity player : serverLevel.getPlayers()) {
            applyRadiationEffects(player);
        }

        if (serverLevel.getRegistryKey() == World.OVERWORLD) {
            Iterator<Entry<BlockPos, RadiationManager.RadiationSource>> iterator = radiationSources.entrySet().iterator();

            while (iterator.hasNext()) {
                RadiationManager.RadiationSource source = iterator.next().getValue();
                source.tick();
                if (!source.isActive()) {
                    iterator.remove();
                }
            }

            for (RadiationManager.PlayerRadiationData data : playerRadiationData.values()) {
                data.tick();
            }

            if (serverLevel.getServer().getTicks() % 10 == 0) {
                syncRadiationToClients(serverLevel.getServer());
            }
        }
    }

    private static void syncRadiationToClients(MinecraftServer server) {
        Map<UUID, Float> levels = new HashMap<>();

        for (Entry<UUID, RadiationManager.PlayerRadiationData> entry : playerRadiationData.entrySet()) {
            double dose = entry.getValue().getAcuteDose();
            float level = (float) Math.min(1.0, dose / 6.0);
            if (level > 0.001F) {
                levels.put(entry.getKey(), level);
            }
        }

        for (Entry<UUID, RadiationManager.EntityRadiationData> entryx : entityRadiationData.entrySet()) {
            double dose = entryx.getValue().getDose();
            if (dose > 0.1) {
                float level = (float) Math.min(1.0, dose / 6.0);
                levels.merge(entryx.getKey(), level, Math::max);
            }
        }

        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                BlockPos pos = player.getBlockPos();
                double field = getRadiationAtPosition(world, pos);
                double ash = getRadiationFromNearbyAsh(world, pos, 10);
                float ambient = (float) Math.min(1.0, field / 18.0 + ash * 10000.0);
                if (ambient > 0.01F) {
                    levels.merge(player.getUuid(), ambient, Math::max);
                }
            }
        }

        RadiationSyncPacket packet = new RadiationSyncPacket(levels);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, RadiationSyncPacket.ID)) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    public static void onLivingDeath(LivingEntity entity) {
        if (entity instanceof PlayerEntity player) {
            clearPlayerRadiation(player);
        } else {
            entityRadiationData.remove(entity.getUuid());
        }
    }

    public static void removeRadiationAt(BlockPos pos, double radius) {
        radiationSources.entrySet()
                .removeIf(
                        entry -> {
                            BlockPos sourcePos = entry.getKey();
                            double distance = Math.sqrt(
                                    Math.pow((double) (pos.getX() - sourcePos.getX()), 2.0)
                                            + Math.pow((double) (pos.getY() - sourcePos.getY()), 2.0)
                                            + Math.pow((double) (pos.getZ() - sourcePos.getZ()), 2.0)
                            );
                            return distance <= radius;
                        }
                );
    }

    public static int getActiveSourceCount() {
        return (int) radiationSources.values().stream().filter(RadiationManager.RadiationSource::isActive).count();
    }

    public static void clearAllRadiation() {
        radiationSources.clear();
        playerRadiationData.clear();
        entityRadiationData.clear();
    }

    public static class EntityRadiationData {
        private double dose = 0.0;
        private boolean isContaminated = false;

        public void addDose(double amount) {
            this.dose += amount;
            if (this.dose > 0.1) {
                this.isContaminated = true;
            }
        }

        public void tick() {
            this.dose *= 0.9995;
            if (this.dose < 0.01) {
                this.isContaminated = false;
            }
        }

        public double getDose() {
            return this.dose;
        }

        public boolean isContaminated() {
            return this.isContaminated;
        }

        public float getGlowIntensity() {
            if (this.dose < 0.1) {
                return 0.0F;
            } else {
                return this.dose >= 10.0 ? 1.0F : (float) Math.min(1.0, this.dose / 6.0);
            }
        }
    }

    public static class PlayerRadiationData {
        private double totalDose = 0.0;
        private double acuteDose = 0.0;
        private double currentExposureRate = 0.0;
        private int arsStage = 0;
        private int stageTicksRemaining = 0;
        private boolean hasBoneMarrowSyndrome = false;
        private boolean hasGastrointestinalSyndrome = false;
        private boolean hasCerebrovascularSyndrome = false;
        private int detectorTickCooldown = 0;
        private boolean isContaminated = false;
        private int contaminationLevel = 0;

        public void addDose(double dosePerTick) {
            this.currentExposureRate = dosePerTick * 20.0 * 60.0;
            this.acuteDose += dosePerTick;
            this.totalDose += dosePerTick;
            this.acuteDose = Math.min(this.acuteDose, 50.0);
            this.totalDose = Math.min(this.totalDose, 100.0);
            if (this.acuteDose > 0.1) {
                this.isContaminated = true;
                if (this.acuteDose > 6.0) {
                    this.contaminationLevel = 3;
                } else if (this.acuteDose > 2.0) {
                    this.contaminationLevel = 2;
                } else if (this.acuteDose > 0.5) {
                    this.contaminationLevel = 1;
                } else {
                    this.contaminationLevel = 0;
                }
            }

            this.updateSyndromes();
            if (this.arsStage == 0 && this.acuteDose >= 1.0) {
                this.enterProdromalStage();
            }
        }

        private void updateSyndromes() {
            if (this.acuteDose >= 1.0 && this.acuteDose < 10.0) {
                this.hasBoneMarrowSyndrome = true;
            }

            if (this.acuteDose >= 6.0) {
                this.hasGastrointestinalSyndrome = true;
            }

            if (this.acuteDose >= 10.0) {
                this.hasCerebrovascularSyndrome = true;
            }
        }

        private void enterProdromalStage() {
            this.arsStage = 1;
            int baseDuration = 2400;
            if (this.acuteDose > 4.0) {
                baseDuration = 1200;
            }

            this.stageTicksRemaining = baseDuration;
        }

        public void tick() {
            if (this.detectorTickCooldown > 0) {
                this.detectorTickCooldown--;
            }

            if (this.arsStage == 0) {
                this.acuteDose *= 0.998;
            } else {
                this.acuteDose *= 0.999;
            }

            if (this.stageTicksRemaining > 0) {
                this.stageTicksRemaining--;
                if (this.stageTicksRemaining <= 0) {
                    this.progressToNextStage();
                }
            }

            if (this.acuteDose < 0.1 && this.arsStage == 0) {
                this.isContaminated = false;
                this.contaminationLevel = 0;
                this.hasBoneMarrowSyndrome = false;
                this.hasGastrointestinalSyndrome = false;
                this.hasCerebrovascularSyndrome = false;
            }

            this.currentExposureRate *= 0.9;
        }

        private void progressToNextStage() {
            switch (this.arsStage) {
                case 1:
                    this.arsStage = 2;
                    if (this.acuteDose >= 6.0) {
                        this.stageTicksRemaining = 600;
                    } else if (this.acuteDose >= 4.0) {
                        this.stageTicksRemaining = 1800;
                    } else {
                        this.stageTicksRemaining = 3600;
                    }
                    break;
                case 2:
                    this.arsStage = 3;
                    if (this.acuteDose >= 6.0) {
                        this.stageTicksRemaining = 1200;
                    } else {
                        this.stageTicksRemaining = 6000;
                    }
                    break;
                case 3:
                    if (this.acuteDose >= 4.0) {
                        this.arsStage = 4;
                        this.stageTicksRemaining = 600;
                    } else {
                        this.arsStage = 0;
                        this.acuteDose *= 0.5;
                        this.hasBoneMarrowSyndrome = false;
                    }
                    break;
                case 4:
            }
        }

        public double getAcuteDose() {
            return this.acuteDose;
        }

        public double getTotalDose() {
            return this.totalDose;
        }

        public double getCurrentExposureRate() {
            return this.currentExposureRate;
        }

        public int getArsStage() {
            return this.arsStage;
        }

        public boolean isContaminated() {
            return this.isContaminated;
        }

        public int getContaminationLevel() {
            return this.contaminationLevel;
        }

        public boolean hasBoneMarrowSyndrome() {
            return this.hasBoneMarrowSyndrome;
        }

        public boolean hasGastrointestinalSyndrome() {
            return this.hasGastrointestinalSyndrome;
        }

        public boolean hasCerebrovascularSyndrome() {
            return this.hasCerebrovascularSyndrome;
        }

        public int getDetectorTickCooldown() {
            return this.detectorTickCooldown;
        }

        public void setDetectorTickCooldown(int cooldown) {
            this.detectorTickCooldown = cooldown;
        }

        public double getCurrentExposure() {
            return this.acuteDose;
        }

        public double getTotalExposure() {
            return this.totalDose;
        }

        public boolean isSick() {
            return this.arsStage > 0;
        }

        public int getSicknessLevel() {
            return this.arsStage;
        }
    }

    public static class RadiationSavedData extends PersistentState {
        public RadiationSavedData() {
        }

        public RadiationSavedData(NbtCompound nbt) {
            if (nbt.contains("sources", 9)) {
                NbtList list = nbt.getList("sources", 10);

                for (int i = 0; i < list.size(); i++) {
                    NbtCompound s = list.getCompound(i);
                    BlockPos pos = new BlockPos(s.getInt("x"), s.getInt("y"), s.getInt("z"));
                    RadiationManager.radiationSources.put(
                            pos,
                            new RadiationManager.RadiationSource(
                                    s.getDouble("posX"),
                                    s.getDouble("posY"),
                                    s.getDouble("posZ"),
                                    s.getDouble("intensity"),
                                    s.getDouble("initialIntensity"),
                                    s.getInt("ticksRemaining")
                            )
                    );
                }
            }
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            NbtList list = new NbtList();

            for (Entry<BlockPos, RadiationManager.RadiationSource> entry : RadiationManager.radiationSources.entrySet()) {
                RadiationManager.RadiationSource src = entry.getValue();
                if (src.isActive()) {
                    NbtCompound s = new NbtCompound();
                    s.putInt("x", entry.getKey().getX());
                    s.putInt("y", entry.getKey().getY());
                    s.putInt("z", entry.getKey().getZ());
                    s.putDouble("posX", src.getX());
                    s.putDouble("posY", src.getY());
                    s.putDouble("posZ", src.getZ());
                    s.putDouble("intensity", src.getIntensity());
                    s.putDouble("initialIntensity", src.getInitialIntensity());
                    s.putInt("ticksRemaining", src.getTicksRemaining());
                    list.add(s);
                }
            }

            nbt.put("sources", list);
            return nbt;
        }

        public static RadiationSavedData get(ServerWorld level) {
            Type<RadiationSavedData> type = new Type<>(
                    RadiationSavedData::new,
                    (tag, registries) -> new RadiationSavedData(tag),
                    DataFixTypes.SAVED_DATA_COMMAND_STORAGE
            );
            return level.getPersistentStateManager().getOrCreate(type, "projectexplosive_radiation");
        }
    }

    public static class RadiationSource {
        private double intensity;
        private int ticksRemaining;
        private final double x;
        private final double y;
        private final double z;
        private final double initialIntensity;
        private boolean isGroundZero;

        public RadiationSource(double x, double y, double z, double intensity, int duration) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.intensity = intensity;
            this.initialIntensity = intensity;
            this.ticksRemaining = duration;
            this.isGroundZero = intensity > 50.0;
        }

        public RadiationSource(double x, double y, double z, double intensity, double initialIntensity, int ticksRemaining) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.intensity = intensity;
            this.initialIntensity = initialIntensity;
            this.ticksRemaining = ticksRemaining;
            this.isGroundZero = intensity > 50.0;
        }

        public void tick() {
            this.ticksRemaining--;
            this.intensity = this.intensity * EnvironmentalConfig.getRadiationDecayRate();
        }

        public boolean isActive() {
            return this.ticksRemaining > 0 && this.intensity > 0.01;
        }

        public double getIntensity() {
            return this.intensity;
        }

        public double getInitialIntensity() {
            return this.initialIntensity;
        }

        public int getTicksRemaining() {
            return this.ticksRemaining;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        public boolean isGroundZero() {
            return this.isGroundZero;
        }
    }
}
