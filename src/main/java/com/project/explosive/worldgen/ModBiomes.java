package com.project.explosive.worldgen;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

public class ModBiomes {
    public static final RegistryKey<Biome> NUCLEAR_WASTELAND = RegistryKey.of(
            RegistryKeys.BIOME,
            Identifier.of("projectexplosive", "nuclear_wasteland")
    );
}
