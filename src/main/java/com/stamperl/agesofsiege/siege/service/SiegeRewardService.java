package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.siege.MedievalLoadouts;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

public final class SiegeRewardService {
	private final Random rewardRandom = Random.create();

	public void dropVictoryRewards(ServerWorld world, SiegeSession session, BlockPos objectivePos, int ageLevel) {
		spawnReward(world, objectivePos, new ItemStack(Items.BREAD, 4));
		spawnReward(world, objectivePos, new ItemStack(Items.IRON_INGOT, 6 + (ageLevel * 2)));
		spawnReward(world, objectivePos, new ItemStack(ModItems.WAR_SUPPLIES, 3 + ageLevel));
		for (ItemStack stack : MedievalLoadouts.getVictoryRewards(ageLevel, rewardRandom)) {
			spawnReward(world, objectivePos, stack);
		}

		if (ageLevel >= 1) {
			spawnReward(world, objectivePos, new ItemStack(Items.ARROW, 12));
			spawnReward(world, objectivePos, new ItemStack(Items.BOW, 1));
		}

		if (ageLevel >= 2) {
			spawnReward(world, objectivePos, new ItemStack(Items.REDSTONE, 8));
			spawnReward(world, objectivePos, new ItemStack(Items.BLAST_FURNACE, 1));
		}

		if (ageLevel >= 3) {
			spawnReward(world, objectivePos, new ItemStack(Items.LIGHTNING_ROD, 4));
			spawnReward(world, objectivePos, new ItemStack(Items.CROSSBOW, 1));
		}
	}

	private void spawnReward(ServerWorld world, BlockPos objectivePos, ItemStack stack) {
		ItemEntity reward = new ItemEntity(
			world,
			objectivePos.getX() + 0.5D,
			objectivePos.getY() + 1.0D,
			objectivePos.getZ() + 0.5D,
			stack
		);
		world.spawnEntity(reward);
	}
}
