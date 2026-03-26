package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.siege.WallTier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

final class SiegePathing {
	private static final int[][] OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private SiegePathing() {
	}

	static boolean pathExists(ServerWorld world, BlockPos start, BlockPos target, int maxNodes) {
		if (start == null || target == null) {
			return false;
		}
		BlockPos startFoot = getFootPos(world, start);
		BlockPos targetFoot = getFootPos(world, target);
		if (!canOccupy(world, startFoot) || !canOccupy(world, targetFoot)) {
			return false;
		}

		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<Long> visited = new HashSet<>();
		queue.add(startFoot);
		visited.add(pack(startFoot));
		int explored = 0;

		while (!queue.isEmpty() && explored < maxNodes) {
			BlockPos current = queue.removeFirst();
			explored++;
			if (isNear(current, targetFoot, 2.0D)) {
				return true;
			}

			for (int[] offset : OFFSETS) {
				BlockPos next = getFootPos(world, current.add(offset[0], 0, offset[1]));
				if (!visited.add(pack(next))) {
					continue;
				}
				if (Math.abs(next.getY() - current.getY()) > 1) {
					continue;
				}
				if (!canOccupy(world, next)) {
					continue;
				}
				queue.addLast(next);
			}
		}

		return false;
	}

	static BlockPos getFootPos(ServerWorld world, BlockPos pos) {
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
		return top;
	}

	static BlockPos getFootPos(ServerWorld world, Vec3d pos) {
		return getFootPos(world, BlockPos.ofFloored(pos));
	}

	static boolean canOccupy(ServerWorld world, BlockPos foot) {
		BlockPos support = foot.down();
		if (world.getBlockState(support).isAir()) {
			return false;
		}
		if (WallTier.from(world.getBlockState(support)) != WallTier.NONE) {
			return false;
		}
		return isClear(world, foot) && isClear(world, foot.up());
	}

	static boolean isClear(ServerWorld world, BlockPos pos) {
		var state = world.getBlockState(pos);
		return state.isAir() && WallTier.from(state) == WallTier.NONE;
	}

	static boolean isNear(BlockPos a, BlockPos b, double horizontalDistance) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz <= horizontalDistance * horizontalDistance;
	}

	private static long pack(BlockPos pos) {
		return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
	}
}
