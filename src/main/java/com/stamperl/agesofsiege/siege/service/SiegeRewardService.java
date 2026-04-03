package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.defense.DefenderTokenData;
import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

public final class SiegeRewardService {
	private static final String[][] FIRST_CLEAR_GEAR_POOLS = {
		{"magistuarmory:wood_roundshield", "magistuarmory:wood_shortsword", "minecraft:crossbow"},
		{"magistuarmory:bronze_buckler", "magistuarmory:bronze_shortsword", "minecraft:bow"},
		{"magistuarmory:iron_heatershield", "magistuarmory:iron_estoc", "minecraft:crossbow"},
		{"magistuarmory:steel_heatershield", "magistuarmory:steel_estoc", "minecraft:crossbow"}
	};
	private static final String[][] RERUN_GEAR_POOLS = {
		{"magistuarmory:wood_roundshield", "minecraft:stone_sword", "minecraft:crossbow"},
		{"magistuarmory:wood_roundshield", "magistuarmory:bronze_shortsword", "minecraft:crossbow"},
		{"magistuarmory:bronze_buckler", "magistuarmory:iron_shortsword", "minecraft:bow"},
		{"magistuarmory:iron_buckler", "magistuarmory:steel_shortsword", "minecraft:crossbow"}
	};

	public List<ItemStack> buildVictoryRewards(int ageLevel, String siegeId, RewardContext context) {
		RewardContext effectiveContext = context == null ? RewardContext.firstClear() : context;
		Random random = effectiveContext.randomSeed() == 0L ? Random.create() : Random.create(effectiveContext.randomSeed());
		List<ItemStack> rewards = new ArrayList<>();
		for (RewardStack rewardStack : rollRewardStacks(ageLevel, siegeId, effectiveContext.rerun(), random)) {
			if (rewardStack.stack() != null && !rewardStack.stack().isEmpty()) {
				rewards.add(rewardStack.stack().copy());
			}
		}
		for (ItemStack returnedToken : effectiveContext.returnedTokens()) {
			if (returnedToken != null && !returnedToken.isEmpty()) {
				rewards.add(returnedToken.copy());
			}
		}
		return List.copyOf(rewards);
	}

	public List<RewardPreviewEntry> buildRewardPreview(int ageLevel, String siegeId, boolean rerun, long previewSeed) {
		Random random = Random.create(previewSeed == 0L ? stableSeed(siegeId, ageLevel, rerun, 1L) : previewSeed);
		List<RewardPreviewEntry> preview = new ArrayList<>();
		for (RewardStack rewardStack : rollRewardStacks(ageLevel, siegeId, rerun, random)) {
			if (rewardStack.stack().isEmpty()) {
				continue;
			}
			preview.add(new RewardPreviewEntry(
				DefenderTokenData.itemId(rewardStack.stack()),
				DefenderTokenData.previewLabel(rewardStack.stack()),
				rewardStack.stack().getCount(),
				rewardStack.ageGear(),
				rerun,
				false
			));
		}
		return List.copyOf(preview);
	}

	public long previewSeed(int ageLevel, String siegeId, boolean rerun, long progressionSeed) {
		return stableSeed(siegeId, ageLevel, rerun, progressionSeed);
	}

	public void claimRewards(ServerPlayerEntity player, List<ItemStack> rewards) {
		if (rewards == null || rewards.isEmpty()) {
			return;
		}
		for (ItemStack stack : rewards) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			ItemStack attempt = stack.copy();
			player.getInventory().insertStack(attempt);
			if (!attempt.isEmpty()) {
				player.dropItem(attempt, false);
			}
		}
		player.currentScreenHandler.sendContentUpdates();
	}

	private List<RewardStack> rollRewardStacks(int ageLevel, String siegeId, boolean rerun, Random random) {
		SiegeCatalog.SiegeDefinition definition = SiegeCatalog.byId(siegeId);
		int tier = Math.max(0, Math.min(ageLevel, 3));
		int warSupplies = definition == null ? 3 + ageLevel : definition.warSuppliesReward();
		if (rerun) {
			warSupplies = Math.max(1, (int) Math.ceil(warSupplies * 0.5D));
		}

		List<RewardStack> rewards = new ArrayList<>();
		rewards.add(new RewardStack(new ItemStack(ModItems.WAR_SUPPLIES, warSupplies), false));
		for (ItemStack food : rollFood(tier, rerun, random)) {
			rewards.add(new RewardStack(food, false));
		}
		for (ItemStack material : rollMaterials(tier, rerun, random)) {
			rewards.add(new RewardStack(material, false));
		}

		ItemStack gear = rollGear(tier, rerun, random);
		if (!gear.isEmpty()) {
			rewards.add(new RewardStack(gear, true));
		}
		if (!rerun && tier >= 2) {
			ItemStack bonusGear = rollGear(Math.max(0, tier - 1), false, random);
			if (!bonusGear.isEmpty()) {
				rewards.add(new RewardStack(bonusGear, true));
			}
		}
		return List.copyOf(rewards);
	}

	private List<ItemStack> rollFood(int tier, boolean rerun, Random random) {
		List<ItemStack> food = new ArrayList<>();
		int breadBase = 4 + tier;
		food.add(new ItemStack(Items.BREAD, rerun ? Math.max(2, breadBase - 2) : breadBase + random.nextInt(2)));
		ItemStack bonus = switch (tier) {
			case 0 -> new ItemStack(Items.COOKED_CHICKEN, rerun ? 1 : 2 + random.nextInt(2));
			case 1 -> new ItemStack(Items.COOKED_MUTTON, rerun ? 2 : 3 + random.nextInt(2));
			case 2 -> new ItemStack(Items.COOKED_BEEF, rerun ? 2 : 4 + random.nextInt(2));
			default -> new ItemStack(Items.COOKED_PORKCHOP, rerun ? 3 : 5 + random.nextInt(2));
		};
		food.add(bonus);
		return List.copyOf(food);
	}

	private List<ItemStack> rollMaterials(int tier, boolean rerun, Random random) {
		List<ItemStack> materials = new ArrayList<>();
		int ironCount = switch (tier) {
			case 0 -> rerun ? 3 : 5 + random.nextInt(2);
			case 1 -> rerun ? 4 : 7 + random.nextInt(2);
			case 2 -> rerun ? 6 : 9 + random.nextInt(3);
			default -> rerun ? 7 : 11 + random.nextInt(3);
		};
		materials.add(new ItemStack(Items.IRON_INGOT, ironCount));
		if (tier >= 1) {
			materials.add(new ItemStack(Items.ARROW, rerun ? 8 : 14 + random.nextInt(4)));
		}
		if (tier >= 2) {
			materials.add(new ItemStack(Items.REDSTONE, rerun ? 4 : 7 + random.nextInt(3)));
		}
		if (tier >= 3) {
			materials.add(new ItemStack(Items.LIGHTNING_ROD, rerun ? 1 : 2 + random.nextInt(2)));
		}
		return List.copyOf(materials);
	}

	private ItemStack rollGear(int tier, boolean rerun, Random random) {
		String[][] pool = rerun ? RERUN_GEAR_POOLS : FIRST_CLEAR_GEAR_POOLS;
		String[] tierPool = pool[Math.max(0, Math.min(tier, pool.length - 1))];
		if (tierPool.length == 0) {
			return ItemStack.EMPTY;
		}
		for (int attempts = 0; attempts < tierPool.length; attempts++) {
			ItemStack stack = DefenderTokenData.optionalRegisteredStack(tierPool[random.nextInt(tierPool.length)], 1);
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private long stableSeed(String siegeId, int ageLevel, boolean rerun, long progressionSeed) {
		long seed = 1125899906842597L;
		String safeId = siegeId == null ? "" : siegeId;
		for (int i = 0; i < safeId.length(); i++) {
			seed = (seed * 131L) + safeId.charAt(i);
		}
		seed = (seed * 31L) + ageLevel;
		seed = (seed * 31L) + (rerun ? 1L : 0L);
		return seed ^ progressionSeed;
	}

	private record RewardStack(ItemStack stack, boolean ageGear) {
	}

	public record RewardPreviewEntry(
		String itemId,
		String displayName,
		int count,
		boolean ageGear,
		boolean rerunReduced,
		boolean tokenReturn
	) {
	}

	public record RewardContext(
		boolean rerun,
		List<ItemStack> returnedTokens,
		long randomSeed
	) {
		public RewardContext {
			returnedTokens = returnedTokens == null ? List.of() : returnedTokens.stream()
				.filter(stack -> stack != null && !stack.isEmpty())
				.map(ItemStack::copy)
				.toList();
		}

		public static RewardContext firstClear() {
			return new RewardContext(false, List.of(), 0L);
		}
	}
}
