package com.stamperl.agesofsiege.item;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class RaidRallyBannerItem extends AbstractSiegeBannerItem {
	public RaidRallyBannerItem(Settings settings) {
		super(settings, Blocks.RED_BANNER, Blocks.RED_WALL_BANNER);
	}

	@Override
	protected void afterPlaced(ServerWorld world, ServerPlayerEntity player, BlockPos targetPos) {
		SiegeBaseState.get(world.getServer()).setRallyPoint(targetPos);
	}

	@Override
	protected String getPlacedMessage() {
		return "Raid rally banner placed. Future sieges will stage from here.";
	}
}
