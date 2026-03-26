package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.siege.SiegeDebug;
import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.BattlefieldObservation;
import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePlanType;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class SiegePlanner {
	private static final long PLAN_TTL_TICKS = 100L;
	private static final int MAX_SEARCH_NODES = 4096;
	private static final int[][] OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final double OPEN_STEP_COST = 1.0D;
	private static final double WALL_STEP_BASE_COST = 6.0D;
	private static final double CONE_HALF_ANGLE_DOT = 0.2D;

	private record SearchNode(BlockPos pos, double cost) {
	}

	private record CellInfo(BlockPos footPos, BlockPos wallBase, int breachCost) {
		boolean isOpen() {
			return wallBase == null;
		}
	}

	private record PathResult(List<CellInfo> cells, double cost) {
	}

	public SiegePlan createPlan(ServerWorld world, BlockPos objectivePos, BlockPos rallyPos, BattlefieldObservation observation, boolean hasRam) {
		if (rallyPos == null || objectivePos == null) {
			SiegePlan fallback = fallbackPlan(world, objectivePos, observation, "missing_positions");
			SiegeDebug.logPlan(world, fallback, "missing_positions");
			return fallback;
		}

		PathResult path = findCheapestConePath(world, rallyPos, objectivePos);
		if (path == null || path.cells().isEmpty()) {
			SiegePlan fallback = fallbackPlan(world, objectivePos, observation, hasRam ? "cone_search_failed_with_ram" : "cone_search_failed");
			SiegeDebug.logPlan(world, fallback, "fallback");
			return fallback;
		}

		BreachSegment firstSegment = firstBreachSegment(world, rallyPos, objectivePos, path);
		if (firstSegment == null) {
			SiegePlan plan = new SiegePlan(
				SiegePlanType.DIRECT_RUSH,
				approachVector(rallyPos, objectivePos),
				rallyPos.toImmutable(),
				null,
				List.of(),
				objectivePos.toImmutable(),
				true,
				1,
				2,
				1.0F,
				world.getTime() + PLAN_TTL_TICKS
			);
			SiegeDebug.log(
				"cone_route totalCost={} cells={} type=direct_rush",
				path.cost(),
				path.cells().size()
			);
			SiegeDebug.logPlan(world, plan, "cone_direct");
			return plan;
		}

		SiegePlan plan = new SiegePlan(
			SiegePlanType.BREACH_REQUIRED,
			approachVector(rallyPos, objectivePos),
			firstSegment.stagingPoint(),
			firstSegment.anchor(),
			firstSegment.targetBlocks(),
			firstSegment.exit(),
			firstSegment.remainingWallCount() == 0,
			1,
			2,
			0.9F,
			world.getTime() + PLAN_TTL_TICKS
		);
		SiegeDebug.log(
			"cone_route totalCost={} cells={} firstLayerBlocks={} remainingWalls={} anchor={} exit={}",
			path.cost(),
			path.cells().size(),
			firstSegment.targetBlocks().size(),
			firstSegment.remainingWallCount(),
			firstSegment.anchor(),
			firstSegment.exit()
		);
		SiegeDebug.logPlan(world, plan, "cone_layered");
		return plan;
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
		return plan.breachExit() != null && SiegePathing.pathExists(world, plan.breachExit(), objectivePos, 4096);
	}

	private PathResult findCheapestConePath(ServerWorld world, BlockPos rallyPos, BlockPos objectivePos) {
		BlockPos start = SiegePathing.getFootPos(world, rallyPos);
		BlockPos target = SiegePathing.getFootPos(world, objectivePos);
		Vec3d forward = approachVector(rallyPos, objectivePos);
		if (forward.lengthSquared() < 0.0001D) {
			return null;
		}

		int maxRange = (int) Math.ceil(Math.sqrt(start.getSquaredDistance(target)) + 16.0D);
		PriorityQueue<SearchNode> frontier = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::cost));
		Map<Long, Double> bestCost = new HashMap<>();
		Map<Long, Long> previous = new HashMap<>();
		Map<Long, CellInfo> cellInfos = new HashMap<>();
		frontier.add(new SearchNode(start, 0.0D));
		bestCost.put(pack(start), 0.0D);
		cellInfos.put(pack(start), new CellInfo(start, null, 0));

		int explored = 0;
		long targetKey = -1L;

		while (!frontier.isEmpty() && explored < MAX_SEARCH_NODES) {
			SearchNode current = frontier.poll();
			long currentKey = pack(current.pos());
			if (current.cost() > bestCost.getOrDefault(currentKey, Double.POSITIVE_INFINITY)) {
				continue;
			}
			explored++;
			if (SiegePathing.isNear(current.pos(), target, 1.5D)) {
				targetKey = currentKey;
				break;
			}

			for (int[] offset : OFFSETS) {
				BlockPos nextXZ = current.pos().add(offset[0], 0, offset[1]);
				if (!isWithinCone(start, nextXZ, forward, maxRange)) {
					continue;
				}
				CellInfo nextCell = inspectCell(world, nextXZ, objectivePos.getY());
				if (nextCell == null) {
					continue;
				}
				double stepCost = nextCell.isOpen() ? OPEN_STEP_COST : WALL_STEP_BASE_COST + nextCell.breachCost();
				double nextCost = current.cost() + stepCost;
				long nextKey = pack(nextCell.footPos());
				if (nextCost >= bestCost.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
					continue;
				}
				bestCost.put(nextKey, nextCost);
				previous.put(nextKey, currentKey);
				cellInfos.put(nextKey, nextCell);
				frontier.add(new SearchNode(nextCell.footPos(), nextCost));
			}
		}

		if (targetKey == -1L) {
			return null;
		}

		List<CellInfo> reversed = new ArrayList<>();
		long cursor = targetKey;
		while (cellInfos.containsKey(cursor)) {
			reversed.add(cellInfos.get(cursor));
			Long next = previous.get(cursor);
			if (next == null) {
				break;
			}
			cursor = next;
		}
		List<CellInfo> cells = new ArrayList<>(reversed.size());
		for (int i = reversed.size() - 1; i >= 0; i--) {
			cells.add(reversed.get(i));
		}
		return new PathResult(List.copyOf(cells), bestCost.get(targetKey));
	}

	private BreachSegment firstBreachSegment(ServerWorld world, BlockPos rallyPos, BlockPos objectivePos, PathResult path) {
		List<CellInfo> cells = path.cells();
		int firstWallIndex = -1;
		int lastWallIndex = -1;
		for (int i = 0; i < cells.size(); i++) {
			if (!cells.get(i).isOpen()) {
				if (firstWallIndex < 0) {
					firstWallIndex = i;
				}
				lastWallIndex = i;
			} else if (firstWallIndex >= 0) {
				break;
			}
		}

		if (firstWallIndex < 0) {
			return null;
		}

		Set<BlockPos> targetedColumns = new LinkedHashSet<>();
		for (int i = firstWallIndex; i <= lastWallIndex; i++) {
			CellInfo cell = cells.get(i);
			if (cell.wallBase() != null) {
				targetedColumns.add(cell.wallBase());
			}
		}

		List<BlockPos> targetBlocks = new ArrayList<>();
		for (BlockPos wallBase : targetedColumns) {
			targetBlocks.addAll(verticalColumnTargets(world, wallBase));
		}

		BlockPos stagingPoint = cells.get(Math.max(0, firstWallIndex - 1)).footPos();
		BlockPos breachExit = lastWallIndex + 1 < cells.size()
			? cells.get(lastWallIndex + 1).footPos()
			: objectivePos.toImmutable();
		int remainingWalls = 0;
		for (int i = lastWallIndex + 1; i < cells.size(); i++) {
			if (!cells.get(i).isOpen()) {
				remainingWalls++;
			}
		}
		return new BreachSegment(
			targetedColumns.iterator().next(),
			stagingPoint.toImmutable(),
			breachExit.toImmutable(),
			List.copyOf(targetBlocks),
			remainingWalls
		);
	}

	private CellInfo inspectCell(ServerWorld world, BlockPos xzPos, int referenceY) {
		BlockPos foot = SiegePathing.getFootPos(world, xzPos);
		if (SiegePathing.canOccupy(world, foot)) {
			return new CellInfo(foot.toImmutable(), null, 0);
		}

		BlockPos wallBase = findWallBase(world, xzPos, referenceY);
		if (wallBase == null) {
			return null;
		}
		return new CellInfo(wallBase.toImmutable(), wallBase.toImmutable(), breachCost(world, wallBase));
	}

	private BlockPos findWallBase(ServerWorld world, BlockPos xzPos, int referenceY) {
		for (int y = referenceY + 4; y >= referenceY - 4; y--) {
			BlockPos candidate = new BlockPos(xzPos.getX(), y, xzPos.getZ());
			if (WallTier.from(world.getBlockState(candidate)) == WallTier.NONE) {
				continue;
			}
			BlockPos cursor = candidate;
			while (cursor.getY() > world.getBottomY() && WallTier.from(world.getBlockState(cursor.down())) != WallTier.NONE) {
				cursor = cursor.down();
			}
			return cursor;
		}
		return null;
	}

	private int breachCost(ServerWorld world, BlockPos wallBase) {
		int hp = 0;
		for (int y = 0; y <= 2; y++) {
			WallTier tier = WallTier.from(world.getBlockState(wallBase.up(y)));
			if (tier != WallTier.NONE) {
				hp += Math.max(1, tier.getHitPoints());
			}
		}
		return hp;
	}

	private List<BlockPos> verticalColumnTargets(ServerWorld world, BlockPos wallBase) {
		List<BlockPos> blocks = new ArrayList<>();
		for (int y = 0; y <= 2; y++) {
			BlockPos candidate = wallBase.up(y);
			if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
				blocks.add(candidate.toImmutable());
			}
		}
		return blocks;
	}

	private boolean isWithinCone(BlockPos start, BlockPos candidate, Vec3d forward, int maxRange) {
		Vec3d delta = Vec3d.ofCenter(candidate).subtract(Vec3d.ofCenter(start));
		double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontalDistance > maxRange || horizontalDistance < 0.0001D) {
			return horizontalDistance < 0.0001D;
		}
		Vec3d dir = new Vec3d(delta.x / horizontalDistance, 0.0D, delta.z / horizontalDistance);
		return forward.dotProduct(dir) >= CONE_HALF_ANGLE_DOT;
	}

	private Vec3d approachVector(BlockPos from, BlockPos to) {
		Vec3d delta = Vec3d.ofCenter(to).subtract(Vec3d.ofCenter(from));
		if (delta.lengthSquared() < 0.0001D) {
			return Vec3d.ZERO;
		}
		return new Vec3d(delta.x, 0.0D, delta.z).normalize();
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

	private long pack(BlockPos pos) {
		return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
	}

	private record BreachSegment(
		BlockPos anchor,
		BlockPos stagingPoint,
		BlockPos exit,
		List<BlockPos> targetBlocks,
		int remainingWallCount
	) {
	}
}
