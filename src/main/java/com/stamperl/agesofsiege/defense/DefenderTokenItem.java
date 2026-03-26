package com.stamperl.agesofsiege.defense;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

public class DefenderTokenItem extends Item {
	private final DefenderSpawnerService defenderSpawnerService;
	private final DefenderRole role;

	public DefenderTokenItem(Settings settings, DefenderSpawnerService defenderSpawnerService, DefenderRole role) {
		super(settings);
		this.defenderSpawnerService = defenderSpawnerService;
		this.role = role;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(context.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}

		SiegeBaseState state = SiegeBaseState.get(world.getServer());
		boolean spawned = defenderSpawnerService.spawnPlacedDefender(player, state, role, context, context.getStack());
		return spawned ? ActionResult.SUCCESS : ActionResult.FAIL;
	}
}
