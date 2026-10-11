package com.project.explosive.particle;

import com.project.explosive.util.RegistrySupplier;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;

public class ModParticles {
    public static final SimpleParticleType SMOKE = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType EXPLOSION_SMOKE = ParticleTypes.EXPLOSION;
    public static final SimpleParticleType SMOKE2 = ParticleTypes.LARGE_SMOKE;
    public static final SimpleParticleType SMOKE2_DECREASING = ParticleTypes.SMOKE;
    public static final SimpleParticleType SMOKE3 = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType SECONDARY_SHOCKWAVE = ParticleTypes.POOF;
    public static final SimpleParticleType WATER_SPLASH = ParticleTypes.SPLASH;
    public static final SimpleParticleType MUSHROOM_CLOUD_STEM = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType MUSHROOM_CLOUD_CAP = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType SMOKE_BIG = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType SMOKE_BIG_SHORT = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType SMOKE3_BIG = ParticleTypes.LARGE_SMOKE;
    public static final SimpleParticleType SPARK = ParticleTypes.LAVA;
    public static final SimpleParticleType EMBER = ParticleTypes.FLAME;
    public static final SimpleParticleType DUST = ParticleTypes.POOF;
    public static final SimpleParticleType FIRE_SMOKE = ParticleTypes.SMOKE;
    public static final SimpleParticleType SMALL_FIRE = ParticleTypes.SMALL_FLAME;
    public static final SimpleParticleType FIRE_FLAME = ParticleTypes.FLAME;
    public static final SimpleParticleType CRATER_SMOKE = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType TRAIL_SMOKE = ParticleTypes.CAMPFIRE_COSY_SMOKE;
    public static final SimpleParticleType WHITE_SHORT = ParticleTypes.POOF;

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

    public static void init() {
    }
}
