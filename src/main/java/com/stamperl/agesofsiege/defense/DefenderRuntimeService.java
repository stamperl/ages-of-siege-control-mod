package com.stamperl.agesofsiege.defense;

import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
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
	private static final double ARCHER_POST_RADIUS = 1.1D;
	private static final double SOLDIER_POST_RADIUS = 1.5D;
	private static final double SOLDIER_AGGRO_RADIUS = 12.0D;

	private DefenderRuntimeService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DefenderRuntimeService::tickServer);
	}

	private static void tickServer(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		boolean siegeActive = state.isSiegeActive();
		Set<UUID> validSiegeTargets = collectValidSiegeTargets(state);
		Set<UUID> breacherTargets = collectBreacherTargets(state);
		Set<UUID> staleDefenders = new HashSet<>();
		for (PlacedDefender defender : state.getPlacedDefenders()) {
			ServerWorld world = server.getWorld(resolveWorldKey(defender.dimensionId()));
			if (world == null) {
				continue;
			}
			if (!isDefenderBindingValid(world, defender)) {
				staleDefenders.add(defender.entityUuid());
				continue;
			}
			if (!(world.getEntity(defender.entityUuid()) instanceof MobEntity mob)) {
				continue;
			}
			enforcePost(world, mob, defender, siegeActive, validSiegeTargets, breacherTargets);
		}
		for (UUID defenderId : staleDefenders) {
			state.removePlacedDefender(defenderId);
		}
	}

	private static RegistryKey<World> resolveWorldKey(String dimensionId) {
		return RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimensionId));
	}

	private static boolean isDefenderBindingValid(ServerWorld world, PlacedDefender defender) {
		if (!world.isChunkLoaded(defender.homePost())) {
			return true;
		}
		if (!(world.getEntity(defender.entityUuid()) instanceof LivingEntity living)) {
			return false;
		}
		if (!living.isAlive()) {
			return false;
		}
		return living.getCommandTags().contains("ages_of_siege_bound_defender");
	}

	private static void enforcePost(
		ServerWorld world,
		MobEntity mob,
		PlacedDefender defender,
		boolean siegeActive,
		Set<UUID> validSiegeTargets,
		Set<UUID> breacherTargets
	) {
		if (!defender.role().isRanged()) {
			enforceSoldierPost(world, mob, defender, siegeActive, validSiegeTargets, breacherTargets);
			return;
		}

		BlockPos homePost = defender.homePost();
		double homeX = homePost.getX() + 0.5D;
		double homeY = homePost.getY();
		double homeZ = homePost.getZ() + 0.5D;
		double distanceSq = mob.squaredDistanceTo(homeX, homeY, homeZ);
		if (!siegeActive) {
			applyPeaceGuardState(mob);
			restoreIdlePostState(mob, defender, homeX, homeY, homeZ, distanceSq);
			return;
		}
		applySiegeGuardState(mob);
		double maxDistance = ARCHER_POST_RADIUS;
		mob.setAiDisabled(false);
		if (distanceSq <= maxDistance * maxDistance) {
			lockDefenderToPost(mob, defender.homeYaw());
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target != null && target.squaredDistanceTo(homeX, homeY, homeZ) > maxDistance * maxDistance) {
			mob.setTarget(null);
		}

		if (distanceSq > (maxDistance + 8.0D) * (maxDistance + 8.0D)) {
			mob.teleport(homeX, homeY, homeZ);
			lockDefenderToPost(mob, defender.homeYaw());
			return;
		}

		mob.getNavigation().startMovingTo(homeX, homeY, homeZ, 1.0D);
	}

	private static void clearTargetAndStop(MobEntity mob) {
		mob.setTarget(null);
		mob.getNavigation().stop();
	}

	private static void restoreIdlePostState(
		MobEntity mob,
		PlacedDefender defender,
		double homeX,
		double homeY,
		double homeZ,
		double distanceSq
	) {
		clearTargetAndStop(mob);
		double allowedDistance = defender.role().isRanged() ? ARCHER_POST_RADIUS : SOLDIER_POST_RADIUS;
		if (!defender.role().isRanged()) {
			if (distanceSq <= allowedDistance * allowedDistance) {
				mob.setAiDisabled(true);
				lockDefenderToPost(mob, defender.homeYaw());
				return;
			}
			mob.setAiDisabled(false);
			mob.getNavigation().startMovingTo(homeX, homeY, homeZ, 1.0D);
			return;
		}
		mob.setAiDisabled(true);
		if (distanceSq > allowedDistance * allowedDistance) {
			mob.teleport(homeX, homeY, homeZ);
		}
		lockDefenderToPost(mob, defender.homeYaw());
	}

	private static void applyPeaceGuardState(MobEntity mob) {
		mob.setInvulnerable(true);
		mob.extinguish();
	}

	private static void applySiegeGuardState(MobEntity mob) {
		if (mob.isInvulnerable()) {
			mob.setInvulnerable(false);
		}
	}

	private static void lockDefenderToPost(MobEntity mob, float yaw) {
		mob.getNavigation().stop();
		mob.setVelocity(0.0D, mob.getVelocity().y, 0.0D);
		mob.setYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setHeadYaw(yaw);
	}

	private static void updateSoldierTarget(
		ServerWorld world,
		MobEntity mob,
		PlacedDefender defender,
		Set<UUID> validSiegeTargets,
		Set<UUID> breacherTargets
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

		LivingEntity nearbyTarget = findNearestSiegeTarget(world, defender, validSiegeTargets, breacherTargets);
		if (nearbyTarget == null) {
			clearTargetAndStop(mob);
			return;
		}

		mob.setAiDisabled(false);
		mob.setTarget(nearbyTarget);
	}

	private static void enforceSoldierPost(
		ServerWorld world,
		MobEntity mob,
		PlacedDefender defender,
		boolean siegeActive,
		Set<UUID> validSiegeTargets,
		Set<UUID> breacherTargets
	) {
		BlockPos homePost = defender.homePost();
		double homeX = homePost.getX() + 0.5D;
		double homeY = homePost.getY();
		double homeZ = homePost.getZ() + 0.5D;
		double distanceSq = mob.squaredDistanceTo(homeX, homeY, homeZ);
		if (!siegeActive) {
			applyPeaceGuardState(mob);
			restoreIdlePostState(mob, defender, homeX, homeY, homeZ, distanceSq);
			return;
		}
		applySiegeGuardState(mob);

		updateSoldierTarget(world, mob, defender, validSiegeTargets, breacherTargets);
		LivingEntity target = mob.getTarget();
		if (target == null) {
			if (distanceSq <= SOLDIER_POST_RADIUS * SOLDIER_POST_RADIUS) {
				mob.setAiDisabled(true);
				lockDefenderToPost(mob, defender.homeYaw());
				return;
			}
			mob.setAiDisabled(false);
			mob.getNavigation().startMovingTo(homeX, homeY, homeZ, 1.0D);
			return;
		}

		mob.setAiDisabled(false);
		if (!target.isAlive() || !validSiegeTargets.contains(target.getUuid())) {
			mob.setTarget(null);
			mob.getNavigation().startMovingTo(homeX, homeY, homeZ, 1.0D);
		}
	}

	private static LivingEntity findNearestSiegeTarget(
		ServerWorld world,
		PlacedDefender defender,
		Set<UUID> validSiegeTargets,
		Set<UUID> breacherTargets
	) {
		BlockPos homePost = defender.homePost();
		double searchRadius = defender.role().isRanged()
			? Math.max(3.0D, defender.leashRadius() + 2.0D)
			: SOLDIER_AGGRO_RADIUS;
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
		LivingEntity nearestBreacher = null;
		double nearestDistanceSq = Double.MAX_VALUE;
		double nearestBreacherDistanceSq = Double.MAX_VALUE;
		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, searchBox, candidate ->
			candidate.isAlive() && validSiegeTargets.contains(candidate.getUuid()))) {
			double distanceSq = entity.squaredDistanceTo(center);
			if (breacherTargets.contains(entity.getUuid()) && distanceSq < nearestBreacherDistanceSq) {
				nearestBreacherDistanceSq = distanceSq;
				nearestBreacher = entity;
			}
			if (distanceSq < nearestDistanceSq) {
				nearestDistanceSq = distanceSq;
				nearest = entity;
			}
		}
		return nearestBreacher != null ? nearestBreacher : nearest;
	}

	private static Set<UUID> collectValidSiegeTargets(SiegeBaseState state) {
		Set<UUID> targets = new HashSet<>();
		targets.addAll(state.getAttackerIds());
		targets.addAll(state.getRamIds());
		return targets;
	}

	private static Set<UUID> collectBreacherTargets(SiegeBaseState state) {
		Set<UUID> targets = new HashSet<>();
		SiegeSession session = state.getActiveSession();
		if (session == null) {
			return targets;
		}
		session.getRoleAssignments().forEach((entityId, role) -> {
			if (role == UnitRole.BREACHER) {
				targets.add(entityId);
			}
		});
		return targets;
	}
}
