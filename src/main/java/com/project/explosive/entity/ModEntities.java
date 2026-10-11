package com.project.explosive.entity;

import com.project.explosive.item.ICBMItem;
import com.project.explosive.util.RegistrySupplier;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<MultiBlockEntity> RAW_MULTI_BLOCK = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "multi_block"),
            FabricEntityTypeBuilder.<MultiBlockEntity>create(SpawnGroup.MISC, MultiBlockEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0F, 1.0F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<SmallBomb> RAW_SMALL_BOMB = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "small_bomb"),
            FabricEntityTypeBuilder.<SmallBomb>create(SpawnGroup.MISC, SmallBomb::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.8F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<BigBomb> RAW_BIG_BOMB = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "big_bomb"),
            FabricEntityTypeBuilder.<BigBomb>create(SpawnGroup.MISC, BigBomb::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.8F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<NuclearBomb> RAW_NUCLEAR_BOMB = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "nuclear_bomb"),
            FabricEntityTypeBuilder.<NuclearBomb>create(SpawnGroup.MISC, NuclearBomb::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.8F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<FireBomb> RAW_FIRE_BOMB = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "fire_bomb"),
            FabricEntityTypeBuilder.<FireBomb>create(SpawnGroup.MISC, FireBomb::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.8F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<ICBMEntity> RAW_ICBM = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "icbm"),
            FabricEntityTypeBuilder.<ICBMEntity>create(SpawnGroup.MISC, ICBMEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.8F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<IcbmBoosterEntity> RAW_ICBM_BOOSTER = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of("projectexplosive", "icbm_booster"),
            FabricEntityTypeBuilder.<IcbmBoosterEntity>create(SpawnGroup.MISC, IcbmBoosterEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 0.8F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final RegistrySupplier<EntityType<MultiBlockEntity>> MULTI_BLOCK = new RegistrySupplier<>(RAW_MULTI_BLOCK);
    public static final RegistrySupplier<EntityType<SmallBomb>> SMALL_BOMB = new RegistrySupplier<>(RAW_SMALL_BOMB);
    public static final RegistrySupplier<EntityType<BigBomb>> BIG_BOMB = new RegistrySupplier<>(RAW_BIG_BOMB);
    public static final RegistrySupplier<EntityType<NuclearBomb>> NUCLEAR_BOMB = new RegistrySupplier<>(RAW_NUCLEAR_BOMB);
    public static final RegistrySupplier<EntityType<FireBomb>> FIRE_BOMB = new RegistrySupplier<>(RAW_FIRE_BOMB);
    public static final RegistrySupplier<EntityType<ICBMEntity>> ICBM = new RegistrySupplier<>(RAW_ICBM);
    public static final RegistrySupplier<EntityType<IcbmBoosterEntity>> ICBM_BOOSTER = new RegistrySupplier<>(RAW_ICBM_BOOSTER);

    public static void init() {
        PolymerEntityUtils.registerType(
                RAW_NUCLEAR_BOMB,
                RAW_BIG_BOMB,
                RAW_SMALL_BOMB,
                RAW_FIRE_BOMB,
                RAW_ICBM,
                RAW_ICBM_BOOSTER
        );

        ICBMItem.LAUNCHER = (world, spawnPos, targetPos, explosionType) -> new ICBMEntity(world, spawnPos, targetPos, explosionType);
    }
}
