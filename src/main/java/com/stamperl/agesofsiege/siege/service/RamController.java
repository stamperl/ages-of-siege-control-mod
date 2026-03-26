package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RamController {
	private static final double RAM_MOVE_STEP = 0.06D;
	private static final double RAM_BACKOFF_DISTANCE = 4.0D;
	private static final int RAM_ATTACK_RANGE = 3;
	private static final int RAM_ATTACK_INTERVAL_TICKS = 30;
	private static final int RAM_BASE_ATTACK_DAMAGE = 25;
	private static final int RAM_SPLASH_ATTACK_DAMAGE = 12;
	private static final int RAM_MAX_ATTACK_BONUS = 20;

	public void updateRam(
		SiegeRamEntity ram,
		ServerWorld world,
		SiegeBaseState state,
		SiegeSession session,
		BlockPos objectivePos,
		boolean rushBanner,
		int rushTicks,
		BlockPos breachAnchor
	) {
		Vec3d ramPos = ram.getPos();
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		if (rushBanner) {
			ram.setBreachTarget(null);
			if (rushTicks <= 60) {
				Vec3d retreatDirection = ramPos.subtract(objectiveCenter);
				if (retreatDirection.lengthSquared() > 0.0001D) {
					Vec3d retreatTarget = ramPos.add(retreatDirection.normalize().multiply(RAM_BACKOFF_DISTANCE));
					moveRam(ram, world, ramPos, retreatTarget.subtract(ramPos).normalize());
					faceTowards(ram, objectiveCenter);
					return;
				}
			}
			ram.setVelocity(0.0D, 0.0D, 0.0D);
			faceTowards(ram, objectiveCenter);
			return;
		}

		BlockPos ramTarget = breachAnchor != null ? breachAnchor : getEffectiveRamTarget(world, ram, objectivePos);
		Vec3d targetCenter = ramTarget != null ? Vec3d.ofCenter(ramTarget) : objectiveCenter;
		Vec3d flatDirection = new Vec3d(targetCenter.x - ramPos.x, 0.0D, targetCenter.z - ramPos.z);
		if (flatDirection.lengthSquared() < 0.0001D) {
			return;
		}

		Vec3d normalized = flatDirection.normalize();
		if (ramTarget != null) {
			if (ram.squaredDistanceTo(Vec3d.ofCenter(ramTarget)) <= RAM_ATTACK_RANGE * RAM_ATTACK_RANGE) {
				faceTowards(ram, targetCenter);
				if (world.getTime() % RAM_ATTACK_INTERVAL_TICKS == 0L) {
					applyRamImpact(state, world, session, ram, ramTarget);
				}
			} else {
				moveRam(ram, world, ramPos, normalized);
			}
			return;
		}

		if (ram.squaredDistanceTo(targetCenter) <= RAM_ATTACK_RANGE * RAM_ATTACK_RANGE) {
			faceTowards(ram, targetCenter);
			if (world.getTime() % RAM_ATTACK_INTERVAL_TICKS == 0L) {
				new ObjectiveService().damageObjective(world, state, session, 4);
			}
			return;
		}

		moveRam(ram, world, ramPos, normalized);
	}

	private void applyRamImpact(SiegeBaseState state, ServerWorld world, SiegeSession session, SiegeRamEntity ram, BlockPos impactTarget) {
		BlockPos base = getWallBase(impactTarget, world);
		Vec3d right = Vec3d.fromPolar(0.0F, ram.getYaw() + 90.0F).normalize();
		int primaryDamage = getRamAttackDamage(state);
		int splashDamage = getRamSplashDamage(state);
		WallDamageService wallDamageService = new WallDamageService();
		for (int lateralOffset = -1; lateralOffset <= 1; lateralOffset++) {
			for (int yOffset = 0; yOffset <= 2; yOffset++) {
				BlockPos target = base.add(
					MathHelper.floor(right.x * lateralOffset),
					yOffset,
					MathHelper.floor(right.z * lateralOffset)
				);
				if (WallTier.from(world.getBlockState(target)) == WallTier.NONE) {
					continue;
				}
				wallDamageService.damageWall(world, state, session, target, lateralOffset == 0 ? primaryDamage : splashDamage);
			}
		}
	}

	private int getRamAttackDamage(SiegeBaseState state) {
		int bonus = Math.min(state.getCompletedSieges() * 2, RAM_MAX_ATTACK_BONUS);
		return RAM_BASE_ATTACK_DAMAGE + bonus;
	}

	private int getRamSplashDamage(SiegeBaseState state) {
		int bonus = Math.min(state.getCompletedSieges(), RAM_MAX_ATTACK_BONUS / 2);
		return RAM_SPLASH_ATTACK_DAMAGE + bonus;
	}

	private BlockPos getWallBase(BlockPos impactTarget, ServerWorld world) {
		BlockPos cursor = impactTarget;
		while (cursor.getY() > world.getBottomY() && WallTier.from(world.getBlockState(cursor.down())) != WallTier.NONE) {
			cursor = cursor.down();
		}
		return cursor.toImmutable();
	}

	private void moveRam(SiegeRamEntity ram, ServerWorld world, Vec3d ramPos, Vec3d direction) {
		double nextX = ramPos.x + direction.x * RAM_MOVE_STEP;
		double nextZ = ramPos.z + direction.z * RAM_MOVE_STEP;
		double currentY = getGroundY(world, ramPos.x, ramPos.z);
		double nextY = getGroundY(world, nextX, nextZ);
		if (nextY > currentY + 1.0D || isBlockedByWallTop(world, nextX, nextY, nextZ) || !hasRoomForRam(ram, world, nextX, nextY, nextZ)) {
			return;
		}
		float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
		ram.refreshPositionAndAngles(nextX, nextY, nextZ, yaw, 0.0F);
	}

	private BlockPos getEffectiveRamTarget(ServerWorld world, SiegeRamEntity ram, BlockPos objectivePos) {
		BlockPos currentTarget = ram.getBreachTarget();
		if (isValidRamBreachTarget(world, currentTarget)) {
			return currentTarget;
		}
		BlockPos bestTarget = findBestRamBreachTarget(world, ram.getPos(), objectivePos);
		ram.setBreachTarget(bestTarget);
		return bestTarget;
	}

	private BlockPos findBestRamBreachTarget(ServerWorld world, Vec3d fromPos, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(fromPos);
		if (approach.lengthSquared() < 0.001D) {
			return null;
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		BlockPos bestTarget = null;
		double bestScore = Double.MAX_VALUE;
		int maxStep = Math.max(2, Math.min((int) Math.ceil(Math.sqrt(approach.lengthSquared())), 96));
		double[] breachLanes = {-6.0D, -3.0D, 0.0D, 3.0D, 6.0D};

		for (double laneOffset : breachLanes) {
			for (int step = 2; step <= maxStep; step += 2) {
				Vec3d sample = fromPos.add(forward.multiply(step)).add(right.multiply(laneOffset));
				BlockPos surface = getSurfaceBlock(world, sample.x, sample.z);
				for (BlockPos candidate : new BlockPos[] {surface, surface.up(), surface.down()}) {
					WallTier tier = WallTier.from(world.getBlockState(candidate));
					if (tier == WallTier.NONE) {
						continue;
					}

					BlockPos base = getWallBase(candidate, world);
					int frontage = getRamFrontageWidth(world, base, right);
					int height = getRamWallHeight(world, base);
					double score = step + Math.abs(laneOffset) * 1.5D + tier.getHitPoints() * 6.0D - frontage * 12.0D - height * 4.0D;
					if (score < bestScore) {
						bestScore = score;
						bestTarget = base.toImmutable();
					}
				}
			}
		}

		return bestTarget;
	}

	private int getRamFrontageWidth(ServerWorld world, BlockPos base, Vec3d right) {
		int width = 1;
		for (int direction : new int[] {-1, 1}) {
			for (int step = 1; step <= 2; step++) {
				BlockPos offset = base.add(
					MathHelper.floor(right.x * direction * step),
					0,
					MathHelper.floor(right.z * direction * step)
				);
				if (WallTier.from(world.getBlockState(offset)) == WallTier.NONE) {
					break;
				}
				width++;
			}
		}
		return width;
	}

	private int getRamWallHeight(ServerWorld world, BlockPos base) {
		int height = 0;
		for (int yOffset = 0; yOffset <= 3; yOffset++) {
			if (WallTier.from(world.getBlockState(base.up(yOffset))) == WallTier.NONE) {
				break;
			}
			height++;
		}
		return height;
	}

	private boolean isValidRamBreachTarget(ServerWorld world, BlockPos target) {
		return target != null && WallTier.from(world.getBlockState(target)) != WallTier.NONE;
	}

	private BlockPos getSurfaceBlock(ServerWorld world, double x, double z) {
		return world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).down();
	}

	private double getGroundY(ServerWorld world, double x, double z) {
		BlockPos top = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
		return top.getY();
	}

	private boolean isBlockedByWallTop(ServerWorld world, double x, double y, double z) {
		BlockPos foot = BlockPos.ofFloored(x, y, z);
		return WallTier.from(world.getBlockState(foot)) != WallTier.NONE
			|| WallTier.from(world.getBlockState(foot.up())) != WallTier.NONE;
	}

	private boolean hasRoomForRam(SiegeRamEntity ram, ServerWorld world, double x, double y, double z) {
		return world.isSpaceEmpty(ram, ram.getBoundingBox().offset(x - ram.getX(), y - ram.getY(), z - ram.getZ()));
	}

	private void faceTowards(SiegeRamEntity ram, Vec3d target) {
		Vec3d delta = target.subtract(ram.getPos());
		if (delta.lengthSquared() < 0.0001D) {
			return;
		}
		float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
		ram.setYaw(yaw);
	}
}
