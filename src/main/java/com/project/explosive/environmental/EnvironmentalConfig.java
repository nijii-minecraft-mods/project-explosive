package com.project.explosive.environmental;

public class EnvironmentalConfig {
    public static double radiationDecayRate = 0.999;
    public static int radiationDurationFactor = 1;
    public static boolean enableRadiationEffects = true;
    public static boolean enableBlockContamination = true;
    public static double contaminationSpreadRate = 0.1;
    public static boolean enableBiomeEffects = true;
    public static boolean enableRadioactiveParticles = true;
    public static int radioactiveParticleDensity = 10;

    public static double getRadiationDecayRate() {
        return radiationDecayRate;
    }

    public static int getRadiationDurationFactor() {
        return radiationDurationFactor;
    }

    public static boolean areRadiationEffectsEnabled() {
        return enableRadiationEffects;
    }

    public static boolean isBlockContaminationEnabled() {
        return enableBlockContamination;
    }

    public static double getContaminationSpreadRate() {
        return contaminationSpreadRate;
    }

    public static boolean areBiomeEffectsEnabled() {
        return enableBiomeEffects;
    }

    public static boolean areRadioactiveParticlesEnabled() {
        return enableRadioactiveParticles;
    }

    public static int getRadioactiveParticleDensity() {
        return radioactiveParticleDensity;
    }
}
