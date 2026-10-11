package com.project.explosive;

public class Config {
    public static Config.QualityMode qualityMode = Config.QualityMode.NORMAL;
    public static boolean enableScreenShake = true;
    public static boolean enableFlashEffect = true;
    public static boolean enableExplosionShaders = true;
    public static boolean enableDistortion = true;
    public static float explosionShaderIntensity = 1.0F;
    public static boolean enableRadiationShaders = true;
    public static float radiationShaderIntensity = 1.0F;
    public static float flashIntensityMultiplier = 1.0F;
    public static float screenShakeIntensityMultiplier = 1.0F;
    public static int maxParticleDistance = 16384;
    public static float particleDensityMultiplier = 1.0F;
    public static boolean enableLongDistanceParticles = true;
    public static boolean enableSparks = true;
    public static boolean enableDust = true;
    public static boolean enableEmbers = true;
    public static float shockwaveSpeedMultiplier = 1.0F;
    public static float explosionDamageMultiplier = 1.0F;
    public static int craterDepthMultiplier = 1;
    public static boolean enableTerrainDeformation = true;
    public static boolean enableBlockDestruction = true;
    public static boolean enableFirePropagation = true;
    public static boolean enableFireDamage = true;
    public static float explosionVolumeMultiplier = 1.0F;
    public static boolean enableDistanceBasedAudio = true;
    public static int maxActiveBombs = 32;

    public static boolean isPotatoMode() {
        return qualityMode == Config.QualityMode.POTATO;
    }

    public static void applyPotatoModeOverrides() {
        particleDensityMultiplier = Math.min(particleDensityMultiplier, 0.3F);
        maxParticleDistance = Math.min(maxParticleDistance, 128);
        enableLongDistanceParticles = false;
        screenShakeIntensityMultiplier = Math.min(screenShakeIntensityMultiplier, 0.5F);
        enableExplosionShaders = false;
        enableRadiationShaders = false;
    }

    public enum QualityMode {
        POTATO,
        NORMAL
    }
}
