package com.project.explosive.particle;

import com.project.explosive.util.RegistrySupplier;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final SimpleParticleType SMOKE = register("smoke");
    public static final SimpleParticleType EXPLOSION_SMOKE = register("explosion_smoke");
    public static final SimpleParticleType SMOKE2 = register("smoke2");
    public static final SimpleParticleType SMOKE2_DECREASING = register("smoke2_decreasing");
    public static final SimpleParticleType SMOKE3 = register("smoke3");
    public static final SimpleParticleType SECONDARY_SHOCKWAVE = register("secondary_shockwave");
    public static final SimpleParticleType WATER_SPLASH = register("water_splash");
    public static final SimpleParticleType MUSHROOM_CLOUD_STEM = register("mushroom_cloud_stem");
    public static final SimpleParticleType MUSHROOM_CLOUD_CAP = register("mushroom_cloud_cap");
    public static final SimpleParticleType SMOKE_BIG = register("smoke_big");
    public static final SimpleParticleType SMOKE_BIG_SHORT = register("smoke_big_short");
    public static final SimpleParticleType SMOKE3_BIG = register("smoke3_big");
    public static final SimpleParticleType SPARK = register("spark");
    public static final SimpleParticleType EMBER = register("ember");
    public static final SimpleParticleType DUST = register("dust");
    public static final SimpleParticleType FIRE_SMOKE = register("fire_smoke");
    public static final SimpleParticleType SMALL_FIRE = register("small_fire");
    public static final SimpleParticleType FIRE_FLAME = register("fire_flame");
    public static final SimpleParticleType CRATER_SMOKE = register("crater_smoke");
    public static final SimpleParticleType TRAIL_SMOKE = register("trail_smoke");
    public static final SimpleParticleType WHITE_SHORT = register("white_short");

    public static final RegistrySupplier<SimpleParticleType> SMOKE_PARTICLE = new RegistrySupplier<>(SMOKE);
    public static final RegistrySupplier<SimpleParticleType> EXPLOSION_SMOKE_PARTICLE = new RegistrySupplier<>(EXPLOSION_SMOKE);
    public static final RegistrySupplier<SimpleParticleType> SMOKE2_PARTICLE = new RegistrySupplier<>(SMOKE2);
    public static final RegistrySupplier<SimpleParticleType> SMOKE2_DECREASING_PARTICLE = new RegistrySupplier<>(SMOKE2_DECREASING);
    public static final RegistrySupplier<SimpleParticleType> SMOKE3_PARTICLE = new RegistrySupplier<>(SMOKE3);
    public static final RegistrySupplier<SimpleParticleType> SECONDARY_SHOCKWAVE_PARTICLE = new RegistrySupplier<>(SECONDARY_SHOCKWAVE);
    public static final RegistrySupplier<SimpleParticleType> WATER_SPLASH_PARTICLE = new RegistrySupplier<>(WATER_SPLASH);
    public static final RegistrySupplier<SimpleParticleType> MUSHROOM_CLOUD_STEM_PARTICLE = new RegistrySupplier<>(MUSHROOM_CLOUD_STEM);
    public static final RegistrySupplier<SimpleParticleType> MUSHROOM_CLOUD_CAP_PARTICLE = new RegistrySupplier<>(MUSHROOM_CLOUD_CAP);
    public static final RegistrySupplier<SimpleParticleType> SMOKE_BIG_PARTICLE = new RegistrySupplier<>(SMOKE_BIG);
    public static final RegistrySupplier<SimpleParticleType> SMOKE_BIG_SHORT_PARTICLE = new RegistrySupplier<>(SMOKE_BIG_SHORT);
    public static final RegistrySupplier<SimpleParticleType> SMOKE3_BIG_PARTICLE = new RegistrySupplier<>(SMOKE3_BIG);
    public static final RegistrySupplier<SimpleParticleType> SPARK_PARTICLE = new RegistrySupplier<>(SPARK);
    public static final RegistrySupplier<SimpleParticleType> EMBER_PARTICLE = new RegistrySupplier<>(EMBER);
    public static final RegistrySupplier<SimpleParticleType> DUST_PARTICLE = new RegistrySupplier<>(DUST);
    public static final RegistrySupplier<SimpleParticleType> FIRE_SMOKE_PARTICLE = new RegistrySupplier<>(FIRE_SMOKE);
    public static final RegistrySupplier<SimpleParticleType> SMALL_FIRE_PARTICLE = new RegistrySupplier<>(SMALL_FIRE);
    public static final RegistrySupplier<SimpleParticleType> FIRE_FLAME_PARTICLE = new RegistrySupplier<>(FIRE_FLAME);
    public static final RegistrySupplier<SimpleParticleType> CRATER_SMOKE_PARTICLE = new RegistrySupplier<>(CRATER_SMOKE);
    public static final RegistrySupplier<SimpleParticleType> TRAIL_SMOKE_PARTICLE = new RegistrySupplier<>(TRAIL_SMOKE);
    public static final RegistrySupplier<SimpleParticleType> WHITE_SHORT_PARTICLE = new RegistrySupplier<>(WHITE_SHORT);

    private static SimpleParticleType register(String name) {
        return Registry.register(
                Registries.PARTICLE_TYPE,
                Identifier.of("projectexplosive", name),
                FabricParticleTypes.simple(true)
        );
    }

    public static void init() {
    }
}
