package com.stamperl.agesofsiege.defense;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public enum WorkbenchArmorTier {
	LEATHER("leather", "Militia Leathers", 1),
	CHAINMAIL("chainmail", "Trained Chainmail", 3),
	IRON("iron", "Veteran Iron", 5);

	private final String id;
	private final String displayName;
	private final int unlockLevel;

	WorkbenchArmorTier(String id, String displayName, int unlockLevel) {
		this.id = id;
		this.displayName = displayName;
		this.unlockLevel = unlockLevel;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public int unlockLevel() {
		return unlockLevel;
	}

	public boolean isUnlockedAt(int level) {
		return level >= unlockLevel;
	}

	public ItemStack createArmorPiece(EquipmentSlot slot) {
		Item item = switch (this) {
			case LEATHER -> switch (slot) {
				case HEAD -> Items.LEATHER_HELMET;
				case CHEST -> Items.LEATHER_CHESTPLATE;
				case LEGS -> Items.LEATHER_LEGGINGS;
				case FEET -> Items.LEATHER_BOOTS;
				default -> Items.AIR;
			};
			case CHAINMAIL -> switch (slot) {
				case HEAD -> Items.CHAINMAIL_HELMET;
				case CHEST -> Items.CHAINMAIL_CHESTPLATE;
				case LEGS -> Items.CHAINMAIL_LEGGINGS;
				case FEET -> Items.CHAINMAIL_BOOTS;
				default -> Items.AIR;
			};
			case IRON -> switch (slot) {
				case HEAD -> Items.IRON_HELMET;
				case CHEST -> Items.IRON_CHESTPLATE;
				case LEGS -> Items.IRON_LEGGINGS;
				case FEET -> Items.IRON_BOOTS;
				default -> Items.AIR;
			};
		};
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	public static WorkbenchArmorTier from(String value) {
		if (value == null || value.isBlank()) {
			return LEATHER;
		}
		for (WorkbenchArmorTier tier : values()) {
			if (tier.id.equalsIgnoreCase(value) || tier.name().equalsIgnoreCase(value)) {
				return tier;
			}
		}
		return LEATHER;
	}
}
