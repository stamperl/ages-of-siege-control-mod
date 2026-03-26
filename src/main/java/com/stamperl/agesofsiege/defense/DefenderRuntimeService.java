package com.stamperl.agesofsiege.defense;

import com.stamperl.agesofsiege.state.PlacedDefender;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DefenderRuntimeService {
	private DefenderRuntimeService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DefenderRuntimeService::tickServer);
	}

	private static void tickServer(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		boolean siegeActive = state.isSiegeActive();
		Set<UUID> validSiegeTargets = collectValidSiegeTargets(state);
		for (PlacedDefender defender : state.getPlacedDefenders()) {
			ServerWorld world = server.getWorld(resolveWorldKey(defender.dimensionId()));
			if (world == null) {
				continue;
			}
			if (!(world.getEntity(defender.entityUuid()) instanceof MobEntity mob)) {
				continue;
			}
			enforcePost(world, mob, defender, siegeActive, validSiegeTargets);
		}
	}

	private static RegistryKey<World> resolveWorldKey(String dimensionId) {
		return RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimensionId));
	}

	private static void enforcePost(
		ServerWorld world,
		MobEntity mob,
		PlacedDefender defender,
		boolean siegeActive,
		Set<UUID> validSiegeTargets
	) {
		BlockPos homePost = defender.homePost();
		double leashRadius = defender.leashRadius();
		double maxDistance = defender.role().isRanged() ? 1.1D : Math.max(1.5D, leashRadius + 1.5D);
		double homeX = homePost.getX() + 0.5D;
		double homeY = homePost.getY();
		double homeZ = homePost.getZ() + 0.5D;
		double distanceSq = mob.squaredDistanceTo(homeX, homeY, homeZ);
		if (!defender.role().isRanged()) {
			updateSoldierTarget(world, mob, defender, validSiegeTargets);
		}
		if (defender.role().isRanged() && !siegeActive) {
			mob.setAiDisabled(true);
			clearTargetAndStop(mob);
		} else if (defender.role().isRanged()) {
			mob.setAiDisabled(false);
		}
		if (!defender.role().isRanged() && mob.getTarget() == null) {
			mob.setAiDisabled(true);
		}
		if (distanceSq <= maxDistance * maxDistance) {
			if (defender.role().isRanged() || mob.getTarget() == null) {
				lockDefenderToPost(mob);
			}
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target != null && target.squaredDistanceTo(homeX, homeY, homeZ) > maxDistance * maxDistance) {
			mob.setTarget(null);
		}
		if (!defender.role().isRanged() && mob.getTarget() == null) {
			mob.teleport(homeX, homeY, homeZ);
			lockDefenderToPost(mob);
			return;
		}

		if (distanceSq > (maxDistance + 8.0D) * (maxDistance + 8.0D) || defender.role().isRanged()) {
			mob.teleport(homeX, homeY, homeZ);
			if (defender.role().isRanged() || mob.getTarget() == null) {
				lockDefenderToPost(mob);
			} else {
				mob.getNavigation().stop();
			}
			return;
		}

		mob.getNavigation().startMovingTo(homeX, homeY, homeZ, 1.0D);
	}

	private static void clearTargetAndStop(MobEntity mob) {
		mob.setTarget(null);
		mob.getNavigation().stop();
	}

	private static void lockDefenderToPost(MobEntity mob) {
		mob.getNavigation().stop();
		mob.setVelocity(0.0D, mob.getVelocity().y, 0.0D);
	}

	private static void updateSoldierTarget(
		ServerWorld world,
		MobEntity mob,
		PlacedDefender defender,
		Set<UUID> validSiegeTargets
	) {
		LivingEntity currentTarget = mob.getTarget();
		if (currentTarget != null && (!currentTarget.isAlive() || !validSiegeTargets.contains(currentTarget.getUuid()))) {
			mob.setTarget(null);
			currentTarget = null;
		}

		if (currentTarget != null) {
			mob.setAiDisabled(false);
			return;
		}

		LivingEntity nearbyTarget = findNearestSiegeTarget(world, defender, validSiegeTargets);
		if (nearbyTarget == null) {
			clearTargetAndStop(mob);
			return;
		}

		mob.setAiDisabled(false);
		mob.setTarget(nearbyTarget);
	}

	private static LivingEntity findNearestSiegeTarget(ServerWorld world, PlacedDefender defender, Set<UUID> validSiegeTargets) {
		BlockPos homePost = defender.homePost();
		double searchRadius = Math.max(3.0D, defender.leashRadius() + 2.0D);
		Vec3d center = Vec3d.ofCenter(homePost);
		Box searchBox = new Box(
			center.x - searchRadius,
			center.y - searchRadius,
			center.z - searchRadius,
			center.x + searchRadius,
			center.y + searchRadius,
			center.z + searchRadius
		);

		LivingEntity nearest = null;
		double nearestDistanceSq = Double.MAX_VALUE;
		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, searchBox, candidate ->
			candidate.isAlive() && validSiegeTargets.contains(candidate.getUuid()))) {
			double distanceSq = entity.squaredDistanceTo(center);
			if (distanceSq < nearestDistanceSq) {
				nearestDistanceSq = distanceSq;
				nearest = entity;
			}
		}
		return nearest;
	}

	private static Set<UUID> collectValidSiegeTargets(SiegeBaseState state) {
		Set<UUID> targets = new HashSet<>();
		targets.addAll(state.getAttackerIds());
		targets.addAll(state.getRamIds());
		return targets;
	}
}
