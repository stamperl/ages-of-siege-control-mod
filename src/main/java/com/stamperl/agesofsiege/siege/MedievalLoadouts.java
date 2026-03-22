package com.stamperl.agesofsiege.siege;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

public final class MedievalLoadouts {
	public enum RaiderRole {
		RANGED,
		BREACHER
	}

	private static final String[][] REWARD_POOLS = {
		{"magistuarmory:wood_roundshield", "magistuarmory:wood_shortsword", "magistuarmory:wood_pike"},
		{"magistuarmory:bronze_buckler", "magistuarmory:bronze_heatershield", "magistuarmory:bronze_shortsword", "magistuarmory:bronze_pike"},
		{"magistuarmory:iron_buckler", "magistuarmory:iron_heatershield", "magistuarmory:iron_shortsword", "magistuarmory:iron_estoc", "magistuarmory:iron_pike"},
		{"magistuarmory:steel_buckler", "magistuarmory:steel_heatershield", "magistuarmory:steel_shortsword", "magistuarmory:steel_estoc", "magistuarmory:steel_pike"}
	};

	private static final String[][] VINDICATOR_WEAPONS = {
		{"magistuarmory:wood_shortsword", "magistuarmory:stone_shortsword"},
		{"magistuarmory:bronze_shortsword", "magistuarmory:bronze_morgenstern"},
		{"magistuarmory:iron_shortsword", "magistuarmory:iron_estoc", "magistuarmory:iron_morgenstern"},
		{"magistuarmory:steel_shortsword", "magistuarmory:steel_estoc", "magistuarmory:steel_morgenstern"}
	};

	private static final String[][] BREACHER_SHIELDS = {
		{"magistuarmory:wood_roundshield"},
		{"magistuarmory:bronze_buckler", "magistuarmory:bronze_heatershield"},
		{"magistuarmory:iron_heatershield", "magistuarmory:iron_kiteshield", "magistuarmory:iron_pavese"},
		{"magistuarmory:steel_heatershield", "magistuarmory:steel_kiteshield", "magistuarmory:steel_pavese"}
	};

	private static final String[][] RANGED_HELMETS = {
		{"magistuarmory:kettlehat"},
		{"magistuarmory:kettlehat", "magistuarmory:barbute"},
		{"magistuarmory:barbute", "magistuarmory:bascinet"},
		{"magistuarmory:bascinet", "magistuarmory:armet"}
	};

	private static final String[][] BREACHER_HELMETS = {
		{"magistuarmory:kettlehat"},
		{"magistuarmory:barbute", "magistuarmory:kettlehat"},
		{"magistuarmory:bascinet", "magistuarmory:barbute"},
		{"magistuarmory:armet", "magistuarmory:bascinet"}
	};

	private static final String[][] RANGED_CHESTS = {
		{"magistuarmory:gambeson_chestplate"},
		{"magistuarmory:chainmail_chestplate", "magistuarmory:brigandine_chestplate"},
		{"magistuarmory:brigandine_chestplate", "magistuarmory:halfarmor_chestplate"},
		{"magistuarmory:halfarmor_chestplate", "magistuarmory:knight_chestplate"}
	};

	private static final String[][] BREACHER_CHESTS = {
		{"magistuarmory:gambeson_chestplate", "magistuarmory:chainmail_chestplate"},
		{"magistuarmory:brigandine_chestplate", "magistuarmory:chainmail_chestplate"},
		{"magistuarmory:knight_chestplate", "magistuarmory:halfarmor_chestplate"},
		{"magistuarmory:maximilian_chestplate", "magistuarmory:platemail_chestplate"}
	};

	private static final String[][] RANGED_LEGS = {
		{"magistuarmory:chainmail_leggings"},
		{"magistuarmory:chainmail_leggings"},
		{"magistuarmory:crusader_leggings", "magistuarmory:knight_leggings"},
		{"magistuarmory:knight_leggings", "magistuarmory:kastenbrust_leggings"}
	};

	private static final String[][] BREACHER_LEGS = {
		{"magistuarmory:chainmail_leggings"},
		{"magistuarmory:chainmail_leggings", "magistuarmory:crusader_leggings"},
		{"magistuarmory:knight_leggings", "magistuarmory:kastenbrust_leggings"},
		{"magistuarmory:maximilian_leggings", "magistuarmory:platemail_leggings"}
	};

	private static final String[][] RANGED_BOOTS = {
		{"magistuarmory:chainmail_boots"},
		{"magistuarmory:chainmail_boots", "magistuarmory:crusader_boots"},
		{"magistuarmory:crusader_boots", "magistuarmory:knight_boots"},
		{"magistuarmory:knight_boots", "magistuarmory:kastenbrust_boots"}
	};

	private static final String[][] BREACHER_BOOTS = {
		{"magistuarmory:gambeson_boots", "magistuarmory:chainmail_boots"},
		{"magistuarmory:chainmail_boots", "magistuarmory:crusader_boots"},
		{"magistuarmory:knight_boots", "magistuarmory:kastenbrust_boots"},
		{"magistuarmory:maximilian_boots", "magistuarmory:platemail_boots"}
	};

	private MedievalLoadouts() {
	}

	public static List<ItemStack> getVictoryRewards(int ageLevel, Random random) {
		List<ItemStack> rewards = new ArrayList<>();
		rewards.add(optionalStack(pick(REWARD_POOLS, ageLevel, random), 1));

		if (ageLevel >= 1) {
			rewards.add(optionalStack("magistuarmory:bronze_ingot", 3));
		}
		if (ageLevel >= 2) {
			rewards.add(optionalStack("magistuarmory:brigandine_chestplate", 1));
		}
		if (ageLevel >= 3) {
			rewards.add(optionalStack("magistuarmory:steel_ingot", 4));
			rewards.add(optionalStack("magistuarmory:armet", 1));
		}

		rewards.removeIf(ItemStack::isEmpty);
		return rewards;
	}

	public static void equipAttacker(HostileEntity attacker, RaiderRole role, int ageLevel, Random random) {
		int tier = clampTier(ageLevel);

		if (attacker instanceof PillagerEntity pillager) {
			pillager.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		}

		if (attacker instanceof VindicatorEntity vindicator) {
			ItemStack weapon = optionalStack(pick(VINDICATOR_WEAPONS, tier, random), 1);
			if (!weapon.isEmpty()) {
				vindicator.equipStack(EquipmentSlot.MAINHAND, weapon);
			}
			equipOptional(attacker, EquipmentSlot.OFFHAND, pick(BREACHER_SHIELDS, tier, random));
		}

		String[][] helmets = role == RaiderRole.BREACHER ? BREACHER_HELMETS : RANGED_HELMETS;
		String[][] chests = role == RaiderRole.BREACHER ? BREACHER_CHESTS : RANGED_CHESTS;
		String[][] legs = role == RaiderRole.BREACHER ? BREACHER_LEGS : RANGED_LEGS;
		String[][] boots = role == RaiderRole.BREACHER ? BREACHER_BOOTS : RANGED_BOOTS;

		equipOptional(attacker, EquipmentSlot.HEAD, pick(helmets, tier, random));
		if (ageLevel >= 1) {
			equipOptional(attacker, EquipmentSlot.CHEST, pick(chests, tier, random));
		}
		if (ageLevel >= 2) {
			equipOptional(attacker, EquipmentSlot.LEGS, pick(legs, tier, random));
		}
		if (ageLevel >= 3) {
			equipOptional(attacker, EquipmentSlot.FEET, pick(boots, tier, random));
		}
	}

	private static void equipOptional(HostileEntity attacker, EquipmentSlot slot, String itemId) {
		ItemStack stack = optionalStack(itemId, 1);
		if (!stack.isEmpty()) {
			attacker.equipStack(slot, stack);
		}
	}

	private static ItemStack optionalStack(String itemId, int count) {
		if (itemId == null || itemId.isBlank()) {
			return ItemStack.EMPTY;
		}

		Identifier id = Identifier.tryParse(itemId);
		if (id == null || !Registries.ITEM.containsId(id)) {
			return ItemStack.EMPTY;
		}

		Item item = Registries.ITEM.get(id);
		return new ItemStack(item, count);
	}

	private static String pick(String[][] pool, int ageLevel, Random random) {
		String[] tierPool = pool[clampTier(ageLevel)];
		if (tierPool.length == 0) {
			return null;
		}
		return tierPool[random.nextInt(tierPool.length)];
	}

	private static int clampTier(int ageLevel) {
		return Math.max(0, Math.min(ageLevel, 3));
	}
}
