package com.project.explosive.sound;

import com.project.explosive.util.RegistrySupplier;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent EXPLOSION_DISTANT = register("nuclear_far");
    public static final SoundEvent NUCLEAR_EXPLOSION = register("nuclear");
    public static final SoundEvent BIG_BOMB_EXPLOSION = register("big_bomb");
    public static final SoundEvent SMALL_BOMB_EXPLOSION = register("small_bomb");
    public static final SoundEvent FIRE_BOMB_EXPLOSION = register("fire_bomb");
    public static final SoundEvent SHOCKWAVE = register("shockwave");
    public static final SoundEvent SHOCKWAVE_RUMBLE = register("shockwave_rumble");
    public static final SoundEvent GEIGER_CLICK_1 = register("geiger_click1");
    public static final SoundEvent GEIGER_CLICK_2 = register("geiger_click2");
    public static final SoundEvent GEIGER_CLICK_3 = register("geiger_click3");
    public static final SoundEvent ICBM_IGNITION = register("icbm_ignition");
    public static final SoundEvent ICBM_ENGINE = register("icbm_engine");
    public static final SoundEvent SHRAPNEL_WHIZZ = register("shrapnel_whizz1");
    public static final SoundEvent SHRAPNEL_IMPACT = register("shrapnel_impact1");
    public static final SoundEvent SHRAPNEL_RICOCHET = register("shrapnel_ricochet1");

    // Compatibility aliases and suppliers
    public static final SoundEvent SHOCKWAVE_SOUND_EVENT = register("shockwave.sound");
    public static final RegistrySupplier<SoundEvent> SHOCKWAVE_SOUND = new RegistrySupplier<>(SHOCKWAVE);
    public static final RegistrySupplier<SoundEvent> NUCLEAR_EXPLOSION_SUPPLIER = new RegistrySupplier<>(NUCLEAR_EXPLOSION);
    public static final RegistrySupplier<SoundEvent> BIG_BOMB_EXPLOSION_SUPPLIER = new RegistrySupplier<>(BIG_BOMB_EXPLOSION);
    public static final RegistrySupplier<SoundEvent> SMALL_BOMB_EXPLOSION_SUPPLIER = new RegistrySupplier<>(SMALL_BOMB_EXPLOSION);
    public static final RegistrySupplier<SoundEvent> FIRE_BOMB_EXPLOSION_SUPPLIER = new RegistrySupplier<>(FIRE_BOMB_EXPLOSION);
    public static final RegistrySupplier<SoundEvent> ICBM_IGNITION_SUPPLIER = new RegistrySupplier<>(ICBM_IGNITION);
    public static final RegistrySupplier<SoundEvent> ICBM_ENGINE_SUPPLIER = new RegistrySupplier<>(ICBM_ENGINE);

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of("projectexplosive", name);
        SoundEvent sound = SoundEvent.of(id);
        Registry.register(Registries.SOUND_EVENT, id, sound);
        RegistrySyncUtils.setServerEntry(Registries.SOUND_EVENT, sound);
        return sound;
    }

    public static void init() {
    }
}
