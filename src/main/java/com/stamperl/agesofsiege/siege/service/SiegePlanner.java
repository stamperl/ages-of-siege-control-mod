package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.siege.SiegeDebug;
import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.BattlefieldObservation;
import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePlanType;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SiegePlanner {
	private static final long PLAN_TTL_TICKS = 100L;
	private static final double[] LANE_OFFSETS = {-6.0D, -4.0D, -2.0D, 0.0D, 2.0D, 4.0D, 6.0D};
	private static final double[] LANE_WIDTH_SAMPLES = {-1.25D, -0.5D, 0.0D, 0.5D, 1.25D};
	private final BattlefieldObservationService observationService = new BattlefieldObservationService();

	private record RouteCandidate(
		double laneOffset,
		BlockPos stagingPoint,
		BlockPos breachAnchor,
		BlockPos breachExit,
		List<BlockPos> targetBlocks,
		int barrierCount,
		boolean traversableAfterBreach,
		double score
	) {
	}

	public SiegePlan createPlan(ServerWorld world, BlockPos objectivePos, BlockPos rallyPos, BattlefieldObservation observation, boolean hasRam) {
		if (rallyPos == null || objectivePos == null) {
			SiegePlan fallback = fallbackPlan(world, objectivePos, observation, "missing_positions");
			SiegeDebug.logPlan(world, fallback, "missing_positions");
			return fallback;
		}

		if (observation.isPathToObjectiveExists()) {
			SiegePlan plan = new SiegePlan(
				SiegePlanType.DIRECT_RUSH,
				approachVector(rallyPos, objectivePos),
				rallyPos.toImmutable(),
				null,
				List.of(),
				null,
				true,
				0,
				0,
				1.0F,
				world.getTime() + PLAN_TTL_TICKS
			);
			SiegeDebug.logPlan(world, plan, "direct_path");
			return plan;
		}

		RouteCandidate bestRoute = null;
		for (double laneOffset : LANE_OFFSETS) {
			RouteCandidate route = analyzeLane(world, rallyPos, objectivePos, laneOffset);
			if (route == null) {
				continue;
			}
			SiegeDebug.log(
				"route laneOffset={} score={} staging={} breachAnchor={} breachExit={} targetBlocks={} barriers={} traversableAfter={}",
				route.laneOffset(),
				route.score(),
				route.stagingPoint(),
				route.breachAnchor(),
				route.breachExit(),
				route.targetBlocks().size(),
				route.barrierCount(),
				route.traversableAfterBreach()
			);
			if (bestRoute == null || route.score() < bestRoute.score()) {
				bestRoute = route;
			}
		}

		if (bestRoute != null) {
			SiegePlan plan = new SiegePlan(
				SiegePlanType.BREACH_REQUIRED,
				approachVector(rallyPos, objectivePos),
				bestRoute.stagingPoint(),
				bestRoute.breachAnchor(),
				bestRoute.targetBlocks(),
				bestRoute.breachExit(),
				bestRoute.traversableAfterBreach(),
				1,
				2,
				0.85F,
				world.getTime() + PLAN_TTL_TICKS
			);
			SiegeDebug.logPlan(world, plan, "least_work_route");
			return plan;
		}

		SiegePlan fallback = fallbackPlan(world, objectivePos, observation, hasRam ? "no_scoreable_lane_with_ram" : "no_scoreable_lane");
		SiegeDebug.logPlan(world, fallback, "fallback");
		return fallback;
	}

	public boolean shouldRefreshPlan(ServerWorld world, SiegeSession session) {
		SiegePlan currentPlan = session.getCurrentPlan();
		if (currentPlan == null) {
			return true;
		}
		if (world.getTime() >= currentPlan.expiresAtTick()) {
			return true;
		}
		if (currentPlan.planType() != SiegePlanType.BREACH_REQUIRED) {
			return false;
		}
		for (BlockPos target : currentPlan.targetBlocks()) {
			if (WallTier.from(world.getBlockState(target)) != WallTier.NONE) {
				return false;
			}
		}
		return true;
	}

	public boolean isPlanInvalid(ServerWorld world, SiegePlan plan) {
		if (plan == null) {
			return true;
		}
		if (plan.planType() != SiegePlanType.BREACH_REQUIRED) {
			return false;
		}
		return plan.targetBlocks().isEmpty();
	}

	public boolean hasActionableTargets(ServerWorld world, SiegePlan plan) {
		if (plan == null || plan.planType() != SiegePlanType.BREACH_REQUIRED) {
			return false;
		}
		for (BlockPos target : plan.targetBlocks()) {
			if (WallTier.from(world.getBlockState(target)) != WallTier.NONE) {
				return true;
			}
		}
		return false;
	}

	public boolean isOpeningUsable(ServerWorld world, SiegePlan plan, BlockPos objectivePos) {
		if (plan == null) {
			return false;
		}
		for (BlockPos target : plan.targetBlocks()) {
			if (WallTier.from(world.getBlockState(target)) != WallTier.NONE) {
				return false;
			}
		}
		if (plan.breachExit() != null && SiegePathing.pathExists(world, plan.breachExit(), objectivePos, 2048)) {
			return true;
		}
		return false;
	}

	private SiegePlan fallbackPlan(ServerWorld world, BlockPos objectivePos, BattlefieldObservation observation, String reason) {
		BlockPos fallbackAnchor = observation.getIntactWallSegments().isEmpty()
			? objectivePos
			: observation.getIntactWallSegments().get(0);
		List<BlockPos> targetBlocks = fallbackAnchor == null ? List.of() : List.of(fallbackAnchor.toImmutable());
		return new SiegePlan(
			SiegePlanType.FALLBACK_PUSH,
			Vec3d.ZERO,
			fallbackAnchor == null ? objectivePos : fallbackAnchor,
			fallbackAnchor,
			targetBlocks,
			null,
			false,
			0,
			0,
			0.25F,
			world.getTime() + 100L
		);
	}

	private BlockPos normalizeWallBase(ServerWorld world, BlockPos anchor) {
		BlockPos cursor = anchor;
		while (cursor.getY() > world.getBottomY() && WallTier.from(world.getBlockState(cursor.down())) != WallTier.NONE) {
			cursor = cursor.down();
		}
		return cursor.toImmutable();
	}

	private List<BlockPos> targetBlocks(ServerWorld world, BlockPos wallBase) {
		List<BlockPos> blocks = new ArrayList<>();
		for (int y = 0; y <= 2; y++) {
			BlockPos candidate = wallBase.up(y);
			if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
				blocks.add(candidate.toImmutable());
			}
		}
		return List.copyOf(blocks);
	}

	private BlockPos stagingPoint(BlockPos rallyPos, BlockPos wallBase) {
		Vec3d approach = approachVector(rallyPos, wallBase);
		Vec3d staging = Vec3d.ofCenter(wallBase).subtract(approach.multiply(4.0D));
		return BlockPos.ofFloored(staging);
	}

	private Vec3d approachVector(BlockPos from, BlockPos to) {
		Vec3d delta = Vec3d.ofCenter(to).subtract(Vec3d.ofCenter(from));
		if (delta.lengthSquared() < 0.0001D) {
			return Vec3d.ZERO;
		}
		return new Vec3d(delta.x, 0.0D, delta.z).normalize();
	}

	private RouteCandidate analyzeLane(ServerWorld world, BlockPos rallyPos, BlockPos objectivePos, double laneOffset) {
		Vec3d start = Vec3d.ofCenter(rallyPos);
		Vec3d end = Vec3d.ofCenter(objectivePos);
		Vec3d delta = end.subtract(start);
		double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (length < 0.001D) {
			return null;
		}

		Vec3d forward = new Vec3d(delta.x / length, 0.0D, delta.z / length);
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		Set<BlockPos> blockingCells = new LinkedHashSet<>();
		BlockPos firstBlocking = null;
		BlockPos lastBlocking = null;
		int barrierCount = 0;
		boolean inBarrier = false;

		for (double step = 1.0D; step < length; step += 0.5D) {
			Vec3d center = start.add(forward.multiply(step)).add(right.multiply(laneOffset));
			boolean foundBlockingAtStep = false;
			for (double widthSample : LANE_WIDTH_SAMPLES) {
				Vec3d sample = center.add(right.multiply(widthSample));
				BlockPos base = BlockPos.ofFloored(sample.x, objectivePos.getY(), sample.z);
				for (int yOffset = 0; yOffset <= 2; yOffset++) {
					BlockPos candidate = base.up(yOffset);
					if (WallTier.from(world.getBlockState(candidate)) == WallTier.NONE) {
						continue;
					}
					BlockPos normalized = normalizeWallBase(world, candidate);
					List<BlockPos> verticalColumn = targetBlocks(world, normalized);
					blockingCells.addAll(verticalColumn);
					if (firstBlocking == null) {
						firstBlocking = normalized;
					}
					lastBlocking = normalized;
					foundBlockingAtStep = true;
				}
			}
			if (foundBlockingAtStep) {
				if (!inBarrier) {
					barrierCount++;
					inBarrier = true;
				}
			} else {
				inBarrier = false;
			}
		}

		if (blockingCells.isEmpty()) {
			return null;
		}

		List<BlockPos> targets = List.copyOf(blockingCells);
		BlockPos staging = stagingPoint(rallyPos, firstBlocking);
		BlockPos breachExit = findLaneExit(world, objectivePos, start, forward, right, laneOffset, lastBlocking);
		int remainingBarrierCount = breachExit == null ? 0 : countBarrierSegmentsAlongLane(world, breachExit, objectivePos);
		int totalBarrierCount = barrierCount + remainingBarrierCount;
		boolean traversableAfterBreach = breachExit != null && remainingBarrierCount == 0;
		double score = routeScore(world, rallyPos, objectivePos, targets, firstBlocking, breachExit, laneOffset, totalBarrierCount, traversableAfterBreach);
		return new RouteCandidate(
			laneOffset,
			staging,
			firstBlocking,
			breachExit,
			targets,
			totalBarrierCount,
			traversableAfterBreach,
			score
		);
	}

	private BlockPos findLaneExit(ServerWorld world, BlockPos objectivePos, Vec3d start, Vec3d forward, Vec3d right, double laneOffset, BlockPos lastBlocking) {
		if (lastBlocking == null) {
			return null;
		}
		Vec3d blockingCenter = Vec3d.ofCenter(lastBlocking);
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d toObjective = objectiveCenter.subtract(blockingCenter);
		double distance = Math.max(1.0D, Math.sqrt(toObjective.x * toObjective.x + toObjective.z * toObjective.z));
		Vec3d towardObjective = new Vec3d(toObjective.x / distance, 0.0D, toObjective.z / distance);
		for (double step = 1.0D; step <= 6.0D; step += 0.5D) {
			Vec3d sample = blockingCenter.add(towardObjective.multiply(step));
			BlockPos foot = SiegePathing.getFootPos(world, BlockPos.ofFloored(sample));
			if (SiegePathing.canOccupy(world, foot) && SiegePathing.pathExists(world, foot, objectivePos, 2048)) {
				return foot.toImmutable();
			}
		}
		return null;
	}

	private double routeScore(
		ServerWorld world,
		BlockPos rallyPos,
		BlockPos objectivePos,
		List<BlockPos> targets,
		BlockPos firstBlocking,
		BlockPos breachExit,
		double laneOffset,
		int barrierCount,
		boolean traversableAfterBreach
	) {
		double hpCost = 0.0D;
		for (BlockPos target : targets) {
			hpCost += Math.max(1, WallTier.from(world.getBlockState(target)).getHitPoints());
		}
		double approachDistancePenalty = Math.sqrt(firstBlocking.getSquaredDistance(rallyPos)) * 0.15D;
		double postBreachPenalty = breachExit == null ? 1000.0D : Math.sqrt(breachExit.getSquaredDistance(objectivePos)) * 2.5D;
		double lanePenalty = Math.abs(laneOffset) * 2.5D;
		double blockCountPenalty = targets.size() * 3.0D;
		double barrierPenalty = Math.max(0, barrierCount - 1) * 35.0D;
		double deadEndPenalty = traversableAfterBreach ? 0.0D : 1000.0D;
		return hpCost + approachDistancePenalty + postBreachPenalty + lanePenalty + blockCountPenalty + barrierPenalty + deadEndPenalty;
	}

	private int countBarrierSegmentsAlongLane(ServerWorld world, BlockPos from, BlockPos to) {
		Vec3d start = Vec3d.ofCenter(from);
		Vec3d end = Vec3d.ofCenter(to);
		Vec3d delta = end.subtract(start);
		double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (length < 0.001D) {
			return 0;
		}

		Vec3d forward = new Vec3d(delta.x / length, 0.0D, delta.z / length);
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int barriers = 0;
		boolean inBarrier = false;

		for (double step = 0.5D; step < length; step += 0.5D) {
			Vec3d center = start.add(forward.multiply(step));
			boolean foundBlockingAtStep = false;
			for (double widthSample : LANE_WIDTH_SAMPLES) {
				Vec3d sample = center.add(right.multiply(widthSample));
				BlockPos base = BlockPos.ofFloored(sample.x, to.getY(), sample.z);
				for (int yOffset = 0; yOffset <= 2; yOffset++) {
					if (WallTier.from(world.getBlockState(base.up(yOffset))) != WallTier.NONE) {
						foundBlockingAtStep = true;
						break;
					}
				}
				if (foundBlockingAtStep) {
					break;
				}
			}
			if (foundBlockingAtStep) {
				if (!inBarrier) {
					barriers++;
					inBarrier = true;
				}
			} else {
				inBarrier = false;
			}
		}

		return barriers;
	}
}
