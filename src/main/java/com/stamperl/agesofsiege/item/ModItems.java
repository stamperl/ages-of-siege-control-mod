package com.stamperl.agesofsiege.item;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.defense.DefenderRecallToolItem;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.defense.DefenderSpawnerService;
import com.stamperl.agesofsiege.defense.DefenderTokenItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
	private static final DefenderSpawnerService DEFENDER_SPAWNER = new DefenderSpawnerService();

	public static final Item SETTLEMENT_STANDARD = register(
		"settlement_standard",
		new SettlementStandardItem(new Item.Settings().maxCount(16))
	);
	public static final Item RAID_RALLY_BANNER = register(
		"raid_rally_banner",
		new RaidRallyBannerItem(new Item.Settings().maxCount(16))
	);
	public static final Item ARMY_LEDGER = register(
		"army_ledger",
		new ArmyLedgerItem(new Item.Settings().maxCount(1))
	);
	public static final Item ARCHER_TOKEN = register(
		"archer_token",
		new DefenderTokenItem(new Item.Settings().maxCount(16), DEFENDER_SPAWNER, DefenderRole.ARCHER)
	);
	public static final Item SOLDIER_TOKEN = register(
		"soldier_token",
		new DefenderTokenItem(new Item.Settings().maxCount(16), DEFENDER_SPAWNER, DefenderRole.SOLDIER)
	);
	public static final Item DEFENDER_RECALL_TOOL = register(
		"defender_recall_tool",
		new DefenderRecallToolItem(new Item.Settings().maxCount(1))
	);

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(SETTLEMENT_STANDARD);
			entries.add(RAID_RALLY_BANNER);
			entries.add(ARMY_LEDGER);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(ARCHER_TOKEN);
			entries.add(SOLDIER_TOKEN);
			entries.add(DEFENDER_RECALL_TOOL);
		});
		AgesOfSiegeMod.LOGGER.info("Registered Ages Of Siege items.");
	}

	private static Item register(String path, Item item) {
		return Registry.register(Registries.ITEM, new Identifier(AgesOfSiegeMod.MOD_ID, path), item);
	}
}
