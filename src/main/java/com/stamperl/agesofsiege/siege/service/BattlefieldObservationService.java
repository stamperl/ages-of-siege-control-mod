package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.BattlefieldObservation;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BattlefieldObservationService {
	public BattlefieldObservation observe(ServerWorld world, SiegeSession session, BlockPos objectivePos) {
		int livingAttackers = 0;
		int livingBreachers = 0;
		int livingRanged = 0;
		int livingEscorts = 0;
		int livingRamCount = 0;
		LivingEntity closestAttacker = null;
		double closestDistanceSq = Double.MAX_VALUE;

		Map<UUID, UnitRole> assignments = session.getRoleAssignments();
		for (UUID attackerId : session.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			livingAttackers++;
			UnitRole role = assignments.get(attackerId);
			if (role == UnitRole.BREACHER) {
				livingBreachers++;
			} else if (role == UnitRole.RANGED) {
				livingRanged++;
			} else if (role == UnitRole.ESCORT) {
				livingEscorts++;
			}
			double distanceSq = living.squaredDistanceTo(Vec3d.ofCenter(objectivePos));
			if (distanceSq < closestDistanceSq) {
				closestDistanceSq = distanceSq;
				closestAttacker = living;
			}
		}

		for (UUID engineId : session.getEngineIds()) {
			Entity entity = world.getEntity(engineId);
			if (entity instanceof SiegeRamEntity ram && ram.isAlive()) {
				livingRamCount++;
			}
		}

		List<BlockPos> wallSegments = scanWallSegments(world, session.getRallyPos(), objectivePos);
		List<BlockPos> openings = scanCandidateOpenings(world, wallSegments, objectivePos);
		boolean pathExists = SiegePathing.pathExists(world, session.getRallyPos(), objectivePos, 4096);
		return new BattlefieldObservation(
			new ObjectiveService().isObjectivePresent(world, session, objectivePos),
			livingAttackers,
			livingBreachers,
			livingRanged,
			livingEscorts,
			livingRamCount,
			closestAttacker,
			pathExists,
			openings,
			wallSegments
		);
	}

	private List<BlockPos> scanWallSegments(ServerWorld world, BlockPos rallyPos, BlockPos objectivePos) {
		List<BlockPos> segments = new ArrayList<>();
		if (rallyPos == null || objectivePos == null) {
			return segments;
		}
		Vec3d from = Vec3d.ofCenter(rallyPos);
		Vec3d to = Vec3d.ofCenter(objectivePos);
		Vec3d delta = to.subtract(from);
		double length = Math.max(1.0D, Math.sqrt(delta.lengthSquared()));
		Vec3d forward = new Vec3d(delta.x / length, 0.0D, delta.z / length);
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);

		for (double step = 0; step <= length; step += 1.5D) {
			Vec3d center = from.add(forward.multiply(step));
			for (int lateral = -4; lateral <= 4; lateral++) {
				Vec3d sample = center.add(right.multiply(lateral));
				BlockPos base = BlockPos.ofFloored(sample.x, world.getTopY(), sample.z);
				for (int y = -2; y <= 2; y++) {
					BlockPos candidate = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, base).add(0, y - 1, 0);
					if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE && !segments.contains(candidate.toImmutable())) {
						segments.add(candidate.toImmutable());
					}
				}
			}
		}
		return List.copyOf(segments);
	}

	private List<BlockPos> scanCandidateOpenings(ServerWorld world, List<BlockPos> wallSegments, BlockPos objectivePos) {
		List<BlockPos> openings = new ArrayList<>();
		for (BlockPos wall : wallSegments) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos candidate = wall.add(dx, 0, dz);
					if (isUsableOpening(world, candidate, objectivePos) && !openings.contains(candidate.toImmutable())) {
						openings.add(candidate.toImmutable());
					}
				}
			}
		}
		return List.copyOf(openings);
	}

	public boolean isUsableOpening(ServerWorld world, BlockPos candidate, BlockPos objectivePos) {
		if (!SiegePathing.isClear(world, candidate) || !SiegePathing.isClear(world, candidate.up())) {
			return false;
		}
		boolean widthTwo = (SiegePathing.isClear(world, candidate.east()) && SiegePathing.isClear(world, candidate.east().up()))
			|| (SiegePathing.isClear(world, candidate.west()) && SiegePathing.isClear(world, candidate.west().up()))
			|| (SiegePathing.isClear(world, candidate.north()) && SiegePathing.isClear(world, candidate.north().up()))
			|| (SiegePathing.isClear(world, candidate.south()) && SiegePathing.isClear(world, candidate.south().up()));
		if (!widthTwo) {
			return false;
		}

		BlockPos outside = findOpenSide(world, candidate, objectivePos, false);
		BlockPos inside = findOpenSide(world, candidate, objectivePos, true);
		if (outside == null || inside == null) {
			return false;
		}

		if (!SiegePathing.canOccupy(world, outside) || !SiegePathing.canOccupy(world, inside)) {
			return false;
		}

		return SiegePathing.pathExists(world, inside, objectivePos, 2048);
	}

	private BlockPos findOpenSide(ServerWorld world, BlockPos candidate, BlockPos objectivePos, boolean towardObjective) {
		Vec3d objective = Vec3d.ofCenter(objectivePos);
		BlockPos best = null;
		double bestScore = towardObjective ? Double.MAX_VALUE : -Double.MAX_VALUE;
		for (BlockPos side : List.of(candidate.east(), candidate.west(), candidate.north(), candidate.south())) {
			if (!SiegePathing.canOccupy(world, side)) {
				continue;
			}
			double score = side.getSquaredDistance(objective.x, side.getY(), objective.z);
			if (towardObjective) {
				if (score < bestScore) {
					bestScore = score;
					best = side.toImmutable();
				}
			} else if (score > bestScore) {
				bestScore = score;
				best = side.toImmutable();
			}
		}
		return best;
	}
}
