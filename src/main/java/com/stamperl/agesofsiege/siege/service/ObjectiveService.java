package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.siege.SiegeDebug;
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
			SiegeDebug.log("damageObjective skipped no_base amount={}", amount);
			return;
		}

		SiegeDebug.log("damageObjective apply amount={} hpBefore={} phase={} objective={}", amount, state.getObjectiveHealth(), session == null ? null : session.getPhase(), state.getBasePos());
		state.setObjectiveHealthValue(Math.max(0, state.getObjectiveHealth() - amount));
		if (state.getObjectiveHealth() == 0) {
			SiegeDebug.log("damageObjective objective_destroyed objective={}", state.getBasePos());
			world.breakBlock(state.getBasePos(), false);
			handleObjectiveDestroyed(world, state, session, state.getBasePos());
			return;
		}

		if (state.isSiegeActive()) {
			world.getServer().getPlayerManager().broadcast(
				Text.literal("Settlement banner damaged: " + state.getObjectiveHealth() + "/" + state.getMaxObjectiveHealth() + " HP"),
				false
			);
		}

		SiegeDebug.log("damageObjective complete hpAfter={}", state.getObjectiveHealth());
		state.markDirty();
	}

	public void handleObjectiveDestroyed(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos pos) {
		if (!state.hasBase()
			|| !state.getBasePos().equals(pos)
			|| !world.getRegistryKey().getValue().toString().equals(state.getDimensionId())) {
			return;
		}

		if (state.isSiegeActive()) {
			spawner.despawnAttackers(world, state.getAttackerIds());
			spawner.despawnRams(world, state.getRamIds());
			state.endSiege(true, false);
			world.getServer().getPlayerManager().broadcast(Text.literal("The Settlement Standard was destroyed. The siege is lost."), false);
			return;
		}

		state.clearBase();
		world.getServer().getPlayerManager().broadcast(
			Text.literal("The Settlement Standard was destroyed. This base is no longer claimed."),
			false
		);
	}
}
