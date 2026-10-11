package com.project.explosive.block;

import com.project.explosive.item.ModBlockItem;
import com.project.explosive.util.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block RAW_DEAD_DIRT = registerBlock("dead_dirt", new DeadDirt(AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_BLACK).strength(0.5F).sounds(BlockSoundGroup.GRAVEL)));
    public static final Block RAW_DEAD_OAK_LOG = registerBlock("dead_oak_log", new DeadOakLog(AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_GRAY).strength(2.0F).sounds(BlockSoundGroup.WOOD)));
    public static final Block RAW_DEAD_OAK_PLANKS = registerBlock("dead_oak_planks", new DeadOakPlanks(AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_GRAY).strength(2.0F).sounds(BlockSoundGroup.WOOD)));
    public static final Block RAW_RADIOACTIVE_ASH = registerBlock("radioactive_ash", new RadioactiveAshBlock(AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_GRAY).strength(0.1F).sounds(BlockSoundGroup.SAND).nonOpaque().pistonBehavior(PistonBehavior.DESTROY).ticksRandomly()));
    public static final Block RAW_DEBRIS = registerBlock("debris", new DebrisBlock(AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(1.0F).sounds(BlockSoundGroup.STONE).nonOpaque().noCollision()));

    public static final RegistrySupplier<Block> DEAD_DIRT = new RegistrySupplier<>(RAW_DEAD_DIRT);
    public static final RegistrySupplier<Block> DEAD_OAK_LOG = new RegistrySupplier<>(RAW_DEAD_OAK_LOG);
    public static final RegistrySupplier<Block> DEAD_OAK_PLANKS = new RegistrySupplier<>(RAW_DEAD_OAK_PLANKS);
    public static final RegistrySupplier<Block> RADIOACTIVE_ASH = new RegistrySupplier<>(RAW_RADIOACTIVE_ASH);
    public static final RegistrySupplier<Block> DEBRIS = new RegistrySupplier<>(RAW_DEBRIS);

    public static final Item RAW_DEAD_DIRT_ITEM = registerItem("dead_dirt", new ModBlockItem(RAW_DEAD_DIRT, new Item.Settings(), Items.COARSE_DIRT, "dead_dirt"));
    public static final Item RAW_DEAD_OAK_LOG_ITEM = registerItem("dead_oak_log", new ModBlockItem(RAW_DEAD_OAK_LOG, new Item.Settings(), Items.STRIPPED_OAK_LOG, "dead_oak_log"));
    public static final Item RAW_DEAD_OAK_PLANKS_ITEM = registerItem("dead_oak_planks", new ModBlockItem(RAW_DEAD_OAK_PLANKS, new Item.Settings(), Items.OAK_PLANKS, "dead_oak_planks"));
    public static final Item RAW_RADIOACTIVE_ASH_ITEM = registerItem("radioactive_ash", new ModBlockItem(RAW_RADIOACTIVE_ASH, new Item.Settings(), Items.BASALT, "radioactive_ash"));
    public static final Item RAW_DEBRIS_ITEM = registerItem("debris", new ModBlockItem(RAW_DEBRIS, new Item.Settings(), Items.COBBLESTONE, "debris"));

    public static final RegistrySupplier<Item> DEAD_DIRT_ITEM = new RegistrySupplier<>(RAW_DEAD_DIRT_ITEM);
    public static final RegistrySupplier<Item> DEAD_OAK_LOG_ITEM = new RegistrySupplier<>(RAW_DEAD_OAK_LOG_ITEM);
    public static final RegistrySupplier<Item> DEAD_OAK_PLANKS_ITEM = new RegistrySupplier<>(RAW_DEAD_OAK_PLANKS_ITEM);
    public static final RegistrySupplier<Item> RADIOACTIVE_ASH_ITEM = new RegistrySupplier<>(RAW_RADIOACTIVE_ASH_ITEM);
    public static final RegistrySupplier<Item> DEBRIS_ITEM = new RegistrySupplier<>(RAW_DEBRIS_ITEM);

    private static Block registerBlock(String name, Block block) {
        return Registry.register(Registries.BLOCK, Identifier.of("projectexplosive", name), block);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of("projectexplosive", name), item);
    }

    public static void init() {
    }
}
