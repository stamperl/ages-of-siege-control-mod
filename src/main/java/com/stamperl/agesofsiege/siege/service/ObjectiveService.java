package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.SiegeDirector;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.state.SharedTreasuryState;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class ObjectiveService {
	private static final Identifier BANK_BLOCK_ID = new Identifier("the_age_of_traders", "bank");
	private static final int BANK_SEARCH_RADIUS = 24;
	private static final int BANK_VERTICAL_RADIUS = 8;
	private final SiegeSpawner spawner = new SiegeSpawner();

	public boolean isObjectivePresent(ServerWorld world, SiegeSession session, BlockPos objectivePos) {
		return world.getBlockState(objectivePos).isIn(BlockTags.BANNERS);
	}

	public void damageObjective(ServerWorld world, SiegeBaseState state, SiegeSession session, int amount) {
		if (!state.hasBase()) {
			return;
		}
		state.setObjectiveHealthValue(Math.max(0, state.getObjectiveHealth() - amount));
		if (state.getObjectiveHealth() == 0) {
			BlockState destroyedBannerState = world.getBlockState(state.getBasePos());
			world.breakBlock(state.getBasePos(), false);
			handleObjectiveDestroyed(world, state, session, state.getBasePos(), destroyedBannerState);
			return;
		}

		if (state.isSiegeActive()) {
			world.getServer().getPlayerManager().broadcast(
				Text.literal("Settlement banner damaged: " + state.getObjectiveHealth() + "/" + state.getMaxObjectiveHealth() + " HP"),
				false
			);
		}

		state.markDirty();
	}

	public void handleObjectiveDestroyed(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos pos) {
		handleObjectiveDestroyed(world, state, session, pos, null);
	}

	public void handleObjectiveDestroyed(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos pos, BlockState destroyedBannerState) {
		if (!state.hasBase()
			|| !state.getBasePos().equals(pos)
			|| !world.getRegistryKey().getValue().toString().equals(state.getDimensionId())) {
			return;
		}

		if (state.getActiveSession() != null) {
			redeploySettlementStandard(world, pos, destroyedBannerState);
			SiegeDirector.handleCombatDefeat(world, state, session, "The Settlement Standard was destroyed. The siege is lost.");
			return;
		}

		state.clearBase();
		world.getServer().getPlayerManager().broadcast(
			Text.literal("The Settlement Standard was destroyed. This base is no longer claimed, but placed defenders remain bound to their owner and posts."),
			false
		);
	}

	public boolean isTrackedBankPresent(ServerWorld world, SiegeBaseState state) {
		BlockPos bankPos = trackedBankPos(world, state);
		return bankPos != null && isBankBlock(world.getBlockState(bankPos));
	}

	public BlockPos trackedBankPos(ServerWorld world, SiegeBaseState state) {
		adoptTrackedBankIfNeeded(world, state);
		if (!state.hasTrackedBank()) {
			return null;
		}
		String worldDimensionId = world.getRegistryKey().getValue().toString();
		if (!worldDimensionId.equals(state.getTrackedBankDimensionId())) {
			return null;
		}
		return state.getTrackedBankPos();
	}

	private void adoptTrackedBankIfNeeded(ServerWorld world, SiegeBaseState state) {
		if (!state.hasBase()) {
			return;
		}
		String worldDimensionId = world.getRegistryKey().getValue().toString();
		if (!worldDimensionId.equals(state.getDimensionId())) {
			return;
		}
		if (state.hasTrackedBank()) {
			if (!worldDimensionId.equals(state.getTrackedBankDimensionId())) {
				return;
			}
			if (isBankBlock(world.getBlockState(state.getTrackedBankPos()))) {
				return;
			}
			AgesOfSiegeMod.LOGGER.warn("Tracked guild bank at {} is missing. Clearing stale tracked state.", state.getTrackedBankPos().toShortString());
			state.clearTrackedBank();
		}

		BlockPos basePos = state.getBasePos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		int foundBanks = 0;
		for (int x = -BANK_SEARCH_RADIUS; x <= BANK_SEARCH_RADIUS; x++) {
			for (int y = -BANK_VERTICAL_RADIUS; y <= BANK_VERTICAL_RADIUS; y++) {
				for (int z = -BANK_SEARCH_RADIUS; z <= BANK_SEARCH_RADIUS; z++) {
					BlockPos candidate = basePos.add(x, y, z);
					if (!isBankBlock(world.getBlockState(candidate))) {
						continue;
					}
					foundBanks++;
					double distance = candidate.getSquaredDistance(basePos);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = candidate.toImmutable();
					}
				}
			}
		}
		if (best == null) {
			return;
		}
		state.setTrackedBank(best, worldDimensionId, state.getBankProtectionCap());
		if (foundBanks > 1) {
			AgesOfSiegeMod.LOGGER.warn(
				"Multiple guild banks found near settlement at {}. Auto-adopted nearest bank at {} and ignored {} duplicates.",
				basePos.toShortString(),
				best.toShortString(),
				foundBanks - 1
			);
		} else {
			AgesOfSiegeMod.LOGGER.info("Auto-adopted existing guild bank at {} for settlement at {}.", best.toShortString(), basePos.toShortString());
		}
	}

	public void damageBank(ServerWorld world, SiegeBaseState state, SiegeSession session, int amount) {
		BlockPos bankPos = trackedBankPos(world, state);
		if (bankPos == null || !isBankBlock(world.getBlockState(bankPos))) {
			return;
		}
		state.setBankHealthValue(Math.max(0, state.getBankHealth() - amount));
		if (state.getBankHealth() == 0) {
			handleBankDestroyed(world, state, session, bankPos, true);
			world.breakBlock(bankPos, false);
			return;
		}

		if (state.isSiegeActive()) {
			world.getServer().getPlayerManager().broadcast(
				Text.literal("Guild bank damaged: " + state.getBankHealth() + "/" + state.getMaxBankHealth() + " HP"),
				false
			);
		}
		state.markDirty();
	}

	public void handleBankDestroyed(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos pos, boolean siegeAttack) {
		if (!state.isTrackedBankAt(pos, world.getRegistryKey().getValue().toString())) {
			return;
		}
		int protectionCap = state.getBankProtectionCap();
		state.clearTrackedBank();
		if (!siegeAttack || session == null) {
			return;
		}
		int lossPercent = MathHelper.nextInt(world.random, 10, 50);
		long lostCoins = SharedTreasuryState.get(world.getServer()).applyProtectedLoss(protectionCap, lossPercent);
		if (lostCoins > 0) {
			world.getServer().getPlayerManager().broadcast(
				Text.literal("The guild bank was destroyed. " + lostCoins + " treasury coins were lost in the chaos."),
				false
			);
			return;
		}
		world.getServer().getPlayerManager().broadcast(
			Text.literal("The guild bank was destroyed, but the protected reserve held firm."),
			false
		);
	}

	private boolean isBankBlock(BlockState state) {
		return Registries.BLOCK.getId(state.getBlock()).equals(BANK_BLOCK_ID);
	}

	private void redeploySettlementStandard(ServerWorld world, BlockPos pos, BlockState destroyedBannerState) {
		BlockState respawnState = destroyedBannerState != null && destroyedBannerState.isIn(BlockTags.BANNERS)
			? destroyedBannerState
			: Blocks.WHITE_BANNER.getDefaultState();
		world.setBlockState(pos, respawnState);
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity != null) {
			blockEntity.markDirty();
		}
	}
}
