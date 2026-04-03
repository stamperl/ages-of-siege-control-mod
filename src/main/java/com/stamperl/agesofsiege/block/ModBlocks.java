package com.stamperl.agesofsiege.block;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Block REPAIR_CHEST = registerBlock(
		"repair_chest",
		new RepairChestBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.OAK_TAN)
			.strength(2.5F)
			.sounds(BlockSoundGroup.WOOD))
	);

	public static final Item REPAIR_CHEST_ITEM = registerItem(
		"repair_chest",
		new BlockItem(REPAIR_CHEST, new Item.Settings())
	);

	public static final BlockEntityType<RepairChestBlockEntity> REPAIR_CHEST_BLOCK_ENTITY = Registry.register(
		Registries.BLOCK_ENTITY_TYPE,
		new Identifier(AgesOfSiegeMod.MOD_ID, "repair_chest"),
		FabricBlockEntityTypeBuilder.create(RepairChestBlockEntity::new, REPAIR_CHEST).build()
	);

	private ModBlocks() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(REPAIR_CHEST_ITEM));
		AgesOfSiegeMod.LOGGER.info("Registered Ages Of Siege blocks.");
	}

	private static Block registerBlock(String path, Block block) {
		return Registry.register(Registries.BLOCK, new Identifier(AgesOfSiegeMod.MOD_ID, path), block);
	}

	private static Item registerItem(String path, Item item) {
		return Registry.register(Registries.ITEM, new Identifier(AgesOfSiegeMod.MOD_ID, path), item);
	}
}
