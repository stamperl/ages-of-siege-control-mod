package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.siege.BattleFormationCatalog;
import com.stamperl.agesofsiege.siege.SiegeFormationDefinition;
import com.stamperl.agesofsiege.siege.SiegeFormationLaneDefinition;
import com.stamperl.agesofsiege.siege.runtime.BattleLane;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

public final class BattleFormationPlanner {
	private static final int MIN_SPAWN_RADIUS = 28;
	private static final int MAX_SPAWN_RADIUS = 36;
	private static final int FORMATION_SPACING = 3;

	private final Random random = Random.create();

	public FormationAnchor resolveAnchor(ServerWorld world, SiegeBaseState state, BlockPos basePos) {
		BlockPos rallyPoint = state.getRallyPoint();
		if (rallyPoint != null && isRallyMarkerPresent(world, rallyPoint)) {
			Vec3d center = Vec3d.ofCenter(rallyPoint);
			Vec3d forward = Vec3d.ofCenter(basePos).subtract(center);
			if (forward.lengthSquared() > 0.001D) {
				forward = forward.normalize();
				Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
				return new FormationAnchor(center, forward, right);
			}
		}

		if (rallyPoint != null && !isRallyMarkerPresent(world, rallyPoint)) {
			state.setRallyPoint(null);
		}

		double angle = random.nextDouble() * (Math.PI * 2.0D);
		int radius = MathHelper.nextInt(random, MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
		Vec3d forward = new Vec3d(-Math.cos(angle), 0.0D, -Math.sin(angle)).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		Vec3d center = Vec3d.ofCenter(basePos).add(forward.multiply(-radius));
		return new FormationAnchor(center, forward, right);
	}

	private boolean isRallyMarkerPresent(ServerWorld world, BlockPos rallyPos) {
		return world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_BANNER)
			|| world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_WALL_BANNER);
	}

	public record FormationAnchor(Vec3d center, Vec3d forward, Vec3d right) {
		public BlockPos positionFor(String formationId, BattleLane lane, int laneIndex, int laneCount, ServerWorld world) {
			SiegeFormationDefinition formation = resolveFormation(formationId);
			SiegeFormationLaneDefinition laneDefinition = formation.laneOrFallback(lane);
			int clampedCount = Math.max(1, laneCount);
			int rowWidth = Math.max(1, laneDefinition.rowWidth());
			int row = laneIndex / rowWidth;
			int rowStart = row * rowWidth;
			int rowItems = Math.min(rowWidth, Math.max(1, clampedCount - rowStart));
			int column = laneIndex % rowWidth;
			double centered = column - (rowItems - 1) / 2.0D;
			double lateralOffset = laneDefinition.lateralOffset() + centered * laneDefinition.lateralStep();
			double depthOffset = laneDefinition.depthOffset() + row * laneDefinition.depthStep();

			Vec3d raw = center.add(right.multiply(lateralOffset)).add(forward.multiply(-depthOffset));
			return grounded(world, raw.x, raw.z);
		}

		public BlockPos enginePosition(String formationId, BattleLane lane, int laneIndex, int laneCount, ServerWorld world) {
			return positionFor(formationId, lane == null ? BattleLane.ENGINE : lane, laneIndex, laneCount, world);
		}

		private SiegeFormationDefinition resolveFormation(String formationId) {
			SiegeFormationDefinition formation = BattleFormationCatalog.definition(formationId);
			if (formation != null) {
				return formation;
			}
			formation = BattleFormationCatalog.definition("line");
			return formation == null
				? new SiegeFormationDefinition("line", "Line", java.util.List.of(new SiegeFormationLaneDefinition(BattleLane.CENTER, 0.0D, 3.0D, FORMATION_SPACING, FORMATION_SPACING, 4, java.util.List.of())))
				: formation;
		}

		private BlockPos grounded(ServerWorld world, double x, double z) {
			BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
			return top.up();
		}
	}
}
