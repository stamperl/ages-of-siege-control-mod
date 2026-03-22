package com.stamperl.agesofsiege.item;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
	public static final Item SETTLEMENT_STANDARD = register(
		"settlement_standard",
		new SettlementStandardItem(new Item.Settings().maxCount(16))
	);

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SETTLEMENT_STANDARD));
		AgesOfSiegeMod.LOGGER.info("Registered Ages Of Siege items.");
	}

	private static Item register(String path, Item item) {
		return Registry.register(Registries.ITEM, new Identifier(AgesOfSiegeMod.MOD_ID, path), item);
	}
}
