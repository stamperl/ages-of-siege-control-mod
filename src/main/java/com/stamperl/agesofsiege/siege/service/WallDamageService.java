package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.block.Block;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class WallDamageService {
	public boolean damageWall(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos pos, int amount) {
		var blockState = world.getBlockState(pos);
		WallTier tier = WallTier.from(blockState);
		if (tier == WallTier.NONE) {
			return false;
		}

		int remaining = state.getTrackedWallHealthOrDefault(pos, tier.getHitPoints()) - amount;
		world.syncWorldEvent(2001, pos, Block.getRawIdFromState(blockState));
		if (remaining <= 0) {
			state.removeTrackedWallHealth(pos);
			world.breakBlock(pos, false);
			state.incrementBreachedWallBlocks();
			world.getServer().getPlayerManager().broadcast(
				Text.literal("A breacher smashed through a " + tier.name().toLowerCase() + " wall block."),
				false
			);
			state.markDirty();
			return true;
		}

		state.setTrackedWallHealth(pos, remaining);
		state.markDirty();
		return true;
	}
}
