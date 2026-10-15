package com.project.explosive.item;

import com.project.explosive.block.ModBlocks;
import com.project.explosive.entity.ModEntities;
import com.project.explosive.util.RegistrySupplier;
import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item RAW_SMALL_BOMB_ITEM = register("small_bomb", new BombItem(() -> ModEntities.SMALL_BOMB.get(), "small_bomb", new Item.Settings().maxCount(16)));
    public static final Item RAW_BIG_BOMB_ITEM = register("big_bomb", new BombItem(() -> ModEntities.BIG_BOMB.get(), "big_bomb", new Item.Settings().maxCount(16)));
    public static final Item RAW_FIRE_BOMB_ITEM = register("fire_bomb", new BombItem(() -> ModEntities.FIRE_BOMB.get(), "fire_bomb", new Item.Settings().maxCount(16)));
    public static final Item RAW_NUCLEAR_BOMB_ITEM = register("nuclear_bomb", new BombItem(() -> ModEntities.NUCLEAR_BOMB.get(), "nuclear_bomb", new Item.Settings().maxCount(16)));
    public static final Item RAW_ICBM_ITEM = register("icbm", new ICBMItem(new Item.Settings().maxCount(1)));
    public static final Item RAW_AIRSTRIKE_CALLER_ITEM = register("airstrike_caller", new AirstrikeCallerItem(new Item.Settings().maxCount(1)));
    public static final Item RAW_FLAMETHROWER_ITEM = register("flamethrower", new FlamethrowerItem(new Item.Settings().maxCount(1).maxDamage(200)));
    public static final Item RAW_EXTINGUISHER_ITEM = register("extinguisher", new ExtinguisherItem(new Item.Settings().maxCount(1).maxDamage(300)));
    public static final Item RAW_RADIATION_DETECTOR_ITEM = register("radiation_detector", new RadiationDetectorItem(new Item.Settings().maxCount(1)));

    // Intermediate Components and Raw Materials
    public static final Item RAW_RAW_URANIUM = register("raw_uranium", new ModItem(new Item.Settings(), net.minecraft.item.Items.RAW_IRON, "raw_uranium"));
    public static final Item RAW_URANIUM_INGOT = register("uranium_ingot", new ModItem(new Item.Settings(), net.minecraft.item.Items.IRON_INGOT, "uranium_ingot"));
    public static final Item RAW_SULFUR = register("sulfur", new ModItem(new Item.Settings(), net.minecraft.item.Items.GLOWSTONE_DUST, "sulfur"));
    public static final Item RAW_SALTPETER = register("saltpeter", new ModItem(new Item.Settings(), net.minecraft.item.Items.SUGAR, "saltpeter"));
    public static final Item RAW_SMALL_BOMB_HEAD = register("small_bomb_head", new ModItem(new Item.Settings(), net.minecraft.item.Items.IRON_NUGGET, "small_bomb_head"));
    public static final Item RAW_BIG_BOMB_HEAD = register("big_bomb_head", new ModItem(new Item.Settings(), net.minecraft.item.Items.IRON_INGOT, "big_bomb_head"));
    public static final Item RAW_BOMB_CASING = register("bomb_casing", new ModItem(new Item.Settings(), net.minecraft.item.Items.NETHERITE_SCRAP, "bomb_casing"));
    public static final Item RAW_ROCKET_ENGINE = register("rocket_engine", new ModItem(new Item.Settings(), net.minecraft.item.Items.FIREWORK_STAR, "rocket_engine"));
    public static final Item RAW_GUIDANCE_COMPUTER = register("guidance_computer", new ModItem(new Item.Settings(), net.minecraft.item.Items.RECOVERY_COMPASS, "guidance_computer"));
    public static final Item RAW_SOLID_FUEL = register("solid_fuel", new ModItem(new Item.Settings(), net.minecraft.item.Items.COAL, "solid_fuel"));
    public static final Item RAW_NUCLEAR_CORE = register("nuclear_core", new ModItem(new Item.Settings(), net.minecraft.item.Items.NETHER_STAR, "nuclear_core"));

    public static final RegistrySupplier<Item> SMALL_BOMB_ITEM = new RegistrySupplier<>(RAW_SMALL_BOMB_ITEM);
    public static final RegistrySupplier<Item> BIG_BOMB_ITEM = new RegistrySupplier<>(RAW_BIG_BOMB_ITEM);
    public static final RegistrySupplier<Item> FIRE_BOMB_ITEM = new RegistrySupplier<>(RAW_FIRE_BOMB_ITEM);
    public static final RegistrySupplier<Item> NUCLEAR_BOMB_ITEM = new RegistrySupplier<>(RAW_NUCLEAR_BOMB_ITEM);
    public static final RegistrySupplier<Item> ICBM_ITEM = new RegistrySupplier<>(RAW_ICBM_ITEM);
    public static final RegistrySupplier<Item> AIRSTRIKE_CALLER_ITEM = new RegistrySupplier<>(RAW_AIRSTRIKE_CALLER_ITEM);
    public static final RegistrySupplier<Item> FLAMETHROWER_ITEM = new RegistrySupplier<>(RAW_FLAMETHROWER_ITEM);
    public static final RegistrySupplier<Item> EXTINGUISHER_ITEM = new RegistrySupplier<>(RAW_EXTINGUISHER_ITEM);
    public static final RegistrySupplier<Item> RADIATION_DETECTOR_ITEM = new RegistrySupplier<>(RAW_RADIATION_DETECTOR_ITEM);

    public static final RegistrySupplier<Item> RAW_URANIUM_ITEM = new RegistrySupplier<>(RAW_RAW_URANIUM);
    public static final RegistrySupplier<Item> URANIUM_INGOT_ITEM = new RegistrySupplier<>(RAW_URANIUM_INGOT);
    public static final RegistrySupplier<Item> SULFUR_ITEM = new RegistrySupplier<>(RAW_SULFUR);
    public static final RegistrySupplier<Item> SALTPETER_ITEM = new RegistrySupplier<>(RAW_SALTPETER);
    public static final RegistrySupplier<Item> SMALL_BOMB_HEAD_ITEM = new RegistrySupplier<>(RAW_SMALL_BOMB_HEAD);
    public static final RegistrySupplier<Item> BIG_BOMB_HEAD_ITEM = new RegistrySupplier<>(RAW_BIG_BOMB_HEAD);
    public static final RegistrySupplier<Item> BOMB_CASING_ITEM = new RegistrySupplier<>(RAW_BOMB_CASING);
    public static final RegistrySupplier<Item> ROCKET_ENGINE_ITEM = new RegistrySupplier<>(RAW_ROCKET_ENGINE);
    public static final RegistrySupplier<Item> GUIDANCE_COMPUTER_ITEM = new RegistrySupplier<>(RAW_GUIDANCE_COMPUTER);
    public static final RegistrySupplier<Item> SOLID_FUEL_ITEM = new RegistrySupplier<>(RAW_SOLID_FUEL);
    public static final RegistrySupplier<Item> NUCLEAR_CORE_ITEM = new RegistrySupplier<>(RAW_NUCLEAR_CORE);

    // Block item suppliers referencing ModBlocks
    public static final RegistrySupplier<Item> DEAD_DIRT_ITEM = ModBlocks.DEAD_DIRT_ITEM;
    public static final RegistrySupplier<Item> DEAD_OAK_LOG_ITEM = ModBlocks.DEAD_OAK_LOG_ITEM;
    public static final RegistrySupplier<Item> DEAD_OAK_PLANKS_ITEM = ModBlocks.DEAD_OAK_PLANKS_ITEM;
    public static final RegistrySupplier<Item> RADIOACTIVE_ASH_ITEM = ModBlocks.RADIOACTIVE_ASH_ITEM;
    public static final RegistrySupplier<Item> DEBRIS_ITEM = ModBlocks.DEBRIS_ITEM;

    // Creative Tab
    public static final RegistryKey<ItemGroup> ITEM_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of("projectexplosive", "explosives"));
    public static final ItemGroup ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(RAW_NUCLEAR_BOMB_ITEM))
            .displayName(Text.translatable("itemGroup.projectexplosive"))
            .entries((context, entries) -> {
                entries.add(RAW_SMALL_BOMB_ITEM);
                entries.add(RAW_BIG_BOMB_ITEM);
                entries.add(RAW_FIRE_BOMB_ITEM);
                entries.add(RAW_NUCLEAR_BOMB_ITEM);
                entries.add(RAW_ICBM_ITEM);
                entries.add(RAW_AIRSTRIKE_CALLER_ITEM);
                entries.add(RAW_FLAMETHROWER_ITEM);
                entries.add(RAW_EXTINGUISHER_ITEM);
                entries.add(RAW_RADIATION_DETECTOR_ITEM);

                // Intermediate & Raw items
                entries.add(RAW_BOMB_CASING);
                entries.add(RAW_ROCKET_ENGINE);
                entries.add(RAW_GUIDANCE_COMPUTER);
                entries.add(RAW_SOLID_FUEL);
                entries.add(RAW_NUCLEAR_CORE);
                entries.add(RAW_SMALL_BOMB_HEAD);
                entries.add(RAW_BIG_BOMB_HEAD);
                entries.add(RAW_URANIUM_INGOT);
                entries.add(RAW_RAW_URANIUM);
                entries.add(RAW_SULFUR);
                entries.add(RAW_SALTPETER);

                // Blocks & Ores
                entries.add(ModBlocks.RAW_DEAD_DIRT_ITEM);
                entries.add(ModBlocks.RAW_DEAD_OAK_LOG_ITEM);
                entries.add(ModBlocks.RAW_DEAD_OAK_PLANKS_ITEM);
                entries.add(ModBlocks.RAW_RADIOACTIVE_ASH_ITEM);
                entries.add(ModBlocks.RAW_DEBRIS_ITEM);
                entries.add(ModBlocks.RAW_MUNITIONS_WORKBENCH_ITEM);
                entries.add(ModBlocks.RAW_URANIUM_ORE_ITEM);
                entries.add(ModBlocks.RAW_DEEPSLATE_URANIUM_ORE_ITEM);
                entries.add(ModBlocks.RAW_NETHER_SULFUR_ORE_ITEM);
                entries.add(ModBlocks.RAW_SALTPETER_ORE_ITEM);
            })
            .build();

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of("projectexplosive", name), item);
    }

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, ITEM_GROUP_KEY, ITEM_GROUP);
        PolymerItemGroupUtils.registerPolymerItemGroup(ITEM_GROUP_KEY, ITEM_GROUP);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(RAW_SMALL_BOMB_ITEM);
            entries.add(RAW_BIG_BOMB_ITEM);
            entries.add(RAW_FIRE_BOMB_ITEM);
            entries.add(RAW_NUCLEAR_BOMB_ITEM);
            entries.add(RAW_ICBM_ITEM);
            entries.add(RAW_AIRSTRIKE_CALLER_ITEM);
            entries.add(RAW_FLAMETHROWER_ITEM);
            entries.add(RAW_EXTINGUISHER_ITEM);
            entries.add(RAW_RADIATION_DETECTOR_ITEM);
        });
    }
}
