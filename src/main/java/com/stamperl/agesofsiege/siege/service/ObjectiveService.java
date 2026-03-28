package com.stamperl.agesofsiege.siege.service;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class ObjectiveService {
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

		if (state.isSiegeActive()) {
			spawner.despawnAttackers(world, state.getAttackerIds());
			spawner.despawnRams(world, state.getRamIds());
			state.endSiege(true, false);
			redeploySettlementStandard(world, pos, destroyedBannerState);
			world.getServer().getPlayerManager().broadcast(Text.literal("The Settlement Standard was destroyed. The siege is lost."), false);
			return;
		}

		state.clearBase();
		world.getServer().getPlayerManager().broadcast(
			Text.literal("The Settlement Standard was destroyed. This base is no longer claimed, but placed defenders remain bound to their owner and posts."),
			false
		);
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
