package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePlanType;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import com.stamperl.agesofsiege.state.PlacedDefender;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SiegeUnitController {
	private static final double OBJECTIVE_ATTACK_RANGE = 2.25D;
	private static final double PLAYER_AGGRO_RANGE = 12.0D;
	private static final double RANGED_AGGRO_RANGE = 18.0D;
	private static final double ESCORT_RANGE = 5.0D;
	private static final double DEFENDER_SCREEN_RANGE = 12.0D;

	private final ObjectiveService objectiveService = new ObjectiveService();
	private final RamController ramController = new RamController();
	private final Set<String> fallbackLoggedSessions = new HashSet<>();

	public void dispatch(ServerWorld world, SiegeBaseState state, SiegeSession session) {
		if (session.getPhase() == SiegePhase.STAGED || session.getPhase() == SiegePhase.COUNTDOWN) {
			holdAtRally(world, session);
			return;
		}

		Map<UUID, UnitRole> assignments = session.getRoleAssignments();
		SiegePlan plan = session.getCurrentPlan();
		BlockPos objectivePos = session.getObjectivePos();
		List<Vec3d> primaryBreacherPositions = new ArrayList<>();
		List<LivingEntity> placedDefenders = resolvePlacedDefenders(world, state);
		boolean hasLivingActiveRam = hasLivingActiveRam(world, session);
		boolean hasLivingPrimaryBreacher = hasLivingPrimaryBreacher(world, session);
		boolean fallbackBreachEnabled = !hasLivingPrimaryBreacher && !hasLivingActiveRam;
		handleFallbackBreachLogging(session, fallbackBreachEnabled, hasLivingPrimaryBreacher, hasLivingActiveRam);

		for (UUID attackerId : session.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			UnitRole role = assignments.getOrDefault(attackerId, UnitRole.RANGED);
			BreachCapability breachCapability = breachCapabilityFor(hostile, role);
			int wallDamage = wallDamageFor(hostile, breachCapability);
			if (breachCapability == BreachCapability.PRIMARY) {
				primaryBreacherPositions.add(hostile.getPos());
			}
			if (fallbackBreachEnabled && breachCapability != BreachCapability.NONE) {
				controlFallbackBreacher(hostile, world, state, session, plan, objectivePos, wallDamage);
				continue;
			}
			switch (role) {
				case BREACHER -> controlPrimaryBreacher(hostile, world, state, session, plan, objectivePos, wallDamage);
				case RANGED -> controlRanged(hostile, world, state, session, plan, objectivePos, primaryBreacherPositions, placedDefenders);
				case ESCORT -> controlEscort(hostile, world, state, session, plan, objectivePos, primaryBreacherPositions, placedDefenders);
				case RAM -> {
				}
			}
		}

		for (UUID ramId : session.getEngineIds()) {
			Entity entity = world.getEntity(ramId);
			if (entity instanceof SiegeRamEntity ram && ram.isAlive()) {
				ramController.updateRam(
					ram,
					world,
					state,
					session,
					objectivePos,
					session.getPhase() == SiegePhase.RUSH,
					state.getRushTicks(),
					plan == null ? null : plan.primaryBreachAnchor()
				);
			}
		}
	}

	private void controlPrimaryBreacher(HostileEntity hostile, ServerWorld world, SiegeBaseState state, SiegeSession session, SiegePlan plan, BlockPos objectivePos, int wallDamage) {
		if (session.getPhase() == SiegePhase.FORM_UP) {
			moveToFormation(hostile, world, session);
			return;
		}
		PlayerEntity playerTarget = getNearbyPlayer(world, hostile, PLAYER_AGGRO_RANGE);
		if (playerTarget != null && session.getPhase() != SiegePhase.RUSH) {
			hostile.setTarget(playerTarget);
			moveToward(hostile, world, playerTarget.getPos(), 1.0D);
			return;
		}
		hostile.setTarget(null);

		if (session.getPhase() == SiegePhase.RUSH) {
			attackObjective(hostile, world, state, session, objectivePos);
			return;
		}

		if (plan == null) {
			moveToward(hostile, world, Vec3d.ofCenter(objectivePos), 1.0D);
			return;
		}

		if (plan.planType() == SiegePlanType.BREACH_REQUIRED || plan.planType() == SiegePlanType.FALLBACK_PUSH) {
			if (plan.stagingPoint() != null && hostile.squaredDistanceTo(Vec3d.ofCenter(plan.stagingPoint())) > 16.0D) {
				moveToward(hostile, world, Vec3d.ofCenter(plan.stagingPoint()), 1.0D);
				return;
			}
			BlockPos targetBlock = firstIntactTarget(world, plan.targetBlocks());
			if (targetBlock != null) {
				attackWall(hostile, world, state, targetBlock, wallDamage);
				return;
			}
		}

		attackObjective(hostile, world, state, session, objectivePos);
	}

	private void controlFallbackBreacher(HostileEntity hostile, ServerWorld world, SiegeBaseState state, SiegeSession session, SiegePlan plan, BlockPos objectivePos, int wallDamage) {
		if (session.getPhase() == SiegePhase.FORM_UP) {
			moveToFormation(hostile, world, session);
			return;
		}
		hostile.setTarget(null);
		if (session.getPhase() == SiegePhase.RUSH) {
			attackObjective(hostile, world, state, session, objectivePos);
			return;
		}
		if (plan == null) {
			moveToward(hostile, world, Vec3d.ofCenter(objectivePos), 1.0D);
			return;
		}
		if (plan.planType() == SiegePlanType.BREACH_REQUIRED || plan.planType() == SiegePlanType.FALLBACK_PUSH) {
			if (plan.stagingPoint() != null && hostile.squaredDistanceTo(Vec3d.ofCenter(plan.stagingPoint())) > 16.0D) {
				moveToward(hostile, world, Vec3d.ofCenter(plan.stagingPoint()), 1.0D);
				return;
			}
			BlockPos targetBlock = firstIntactTarget(world, plan.targetBlocks());
			if (targetBlock != null) {
				attackWall(hostile, world, state, targetBlock, wallDamage);
				return;
			}
		}
		attackObjective(hostile, world, state, session, objectivePos);
	}

	private void controlRanged(
		HostileEntity hostile,
		ServerWorld world,
		SiegeBaseState state,
		SiegeSession session,
		SiegePlan plan,
		BlockPos objectivePos,
		List<Vec3d> breacherPositions,
		List<LivingEntity> placedDefenders
	) {
		if (session.getPhase() == SiegePhase.FORM_UP) {
			moveToFormation(hostile, world, session);
			return;
		}
		LivingEntity defenderTarget = getNearbyDefenderTarget(hostile, placedDefenders, breacherPositions, DEFENDER_SCREEN_RANGE);
		if (defenderTarget != null) {
			hostile.setTarget(defenderTarget);
			return;
		}
		PlayerEntity playerTarget = getNearbyPlayer(world, hostile, RANGED_AGGRO_RANGE);
		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			return;
		}
		hostile.setTarget(null);

		if (session.getPhase() == SiegePhase.RUSH) {
			attackObjective(hostile, world, stateFor(world), session, objectivePos);
			return;
		}

		Vec3d anchor = average(breacherPositions);
		if (anchor == null && plan != null && plan.stagingPoint() != null) {
			anchor = Vec3d.ofCenter(plan.stagingPoint());
		}
		if (anchor == null) {
			anchor = Vec3d.ofCenter(session.getRallyPos() == null ? objectivePos : session.getRallyPos());
		}
		Vec3d offset = anchor.add(new Vec3d(2.0D, 0.0D, 2.0D));
		moveToward(hostile, world, offset, 0.9D);
	}

	private void controlEscort(
		HostileEntity hostile,
		ServerWorld world,
		SiegeBaseState state,
		SiegeSession session,
		SiegePlan plan,
		BlockPos objectivePos,
		List<Vec3d> breacherPositions,
		List<LivingEntity> placedDefenders
	) {
		if (session.getPhase() == SiegePhase.FORM_UP) {
			moveToFormation(hostile, world, session);
			return;
		}
		LivingEntity defenderTarget = getNearbyDefenderTarget(hostile, placedDefenders, breacherPositions, DEFENDER_SCREEN_RANGE);
		if (defenderTarget != null) {
			hostile.setTarget(defenderTarget);
			moveToward(hostile, world, defenderTarget.getPos(), 1.0D);
			return;
		}
		PlayerEntity playerTarget = getNearbyPlayer(world, hostile, PLAYER_AGGRO_RANGE);
		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			moveToward(hostile, world, playerTarget.getPos(), 1.0D);
			return;
		}
		hostile.setTarget(null);

		SiegeRamEntity ram = nearestRam(world, session, hostile.getPos());
		if (ram != null) {
			if (hostile.squaredDistanceTo(ram) > ESCORT_RANGE * ESCORT_RANGE) {
				moveToward(hostile, world, ram.getPos(), 1.0D);
			} else {
				hostile.getNavigation().stop();
			}
			return;
		}

		if (session.getPhase() == SiegePhase.RUSH) {
			attackObjective(hostile, world, stateFor(world), session, objectivePos);
			return;
		}

		Vec3d breacherAnchor = average(breacherPositions);
		if (breacherAnchor != null) {
			moveToward(hostile, world, breacherAnchor, 1.0D);
			return;
		}

		if (plan != null && plan.stagingPoint() != null) {
			moveToward(hostile, world, Vec3d.ofCenter(plan.stagingPoint()), 1.0D);
			return;
		}

		moveToward(hostile, world, Vec3d.ofCenter(objectivePos), 1.0D);
	}

	private void attackObjective(HostileEntity hostile, ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos objectivePos) {
		Vec3d objective = Vec3d.ofCenter(objectivePos);
		if (hostile.squaredDistanceTo(objective) > OBJECTIVE_ATTACK_RANGE * OBJECTIVE_ATTACK_RANGE) {
			moveToward(hostile, world, objective, 1.0D);
			return;
		}
		hostile.getNavigation().stop();
		hostile.swingHand(Hand.MAIN_HAND);
		if (world.getTime() % 10L == 0L) {
			objectiveService.damageObjective(world, state, session, 1);
		}
	}

	private void attackWall(HostileEntity hostile, ServerWorld world, SiegeBaseState state, BlockPos targetBlock, int wallDamage) {
		Vec3d target = Vec3d.ofCenter(targetBlock);
		if (hostile.squaredDistanceTo(target) > 9.0D) {
			moveToward(hostile, world, target, 1.0D);
			return;
		}
		hostile.getNavigation().stop();
		hostile.setYaw((float) (MathHelper.atan2(target.z - hostile.getZ(), target.x - hostile.getX()) * (180.0D / Math.PI)) - 90.0F);
		hostile.swingHand(Hand.MAIN_HAND);
		if (hostile.age % 15 == 0) {
			state.damageWall(world, targetBlock, Math.max(1, wallDamage));
		}
	}

	private BlockPos firstIntactTarget(ServerWorld world, List<BlockPos> targets) {
		for (BlockPos target : targets) {
			if (WallTier.from(world.getBlockState(target)) != WallTier.NONE) {
				return target;
			}
		}
		return null;
	}

	private void moveToward(HostileEntity hostile, ServerWorld world, Vec3d target, double speed) {
		BlockPos foot = SiegePathing.getFootPos(world, BlockPos.ofFloored(target));
		hostile.getNavigation().startMovingTo(target.x, foot.getY(), target.z, speed);
	}

	private void holdAtRally(ServerWorld world, SiegeSession session) {
		Vec3d anchor = Vec3d.ofCenter(session.getSpawnCenter() == null ? session.getRallyPos() : session.getSpawnCenter());
		for (UUID attackerId : session.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (entity instanceof HostileEntity hostile && hostile.isAlive()) {
				hostile.setTarget(null);
				if (hostile.squaredDistanceTo(anchor) > 25.0D) {
					moveToward(hostile, world, anchor, 1.0D);
				} else {
					hostile.getNavigation().stop();
				}
			}
		}
		for (UUID ramId : session.getEngineIds()) {
			Entity entity = world.getEntity(ramId);
			if (entity instanceof SiegeRamEntity ram && ram.isAlive()) {
				ram.getNavigation().stop();
				ram.setVelocity(0.0D, 0.0D, 0.0D);
			}
		}
	}

	private void moveToFormation(HostileEntity hostile, ServerWorld world, SiegeSession session) {
		Vec3d anchor = Vec3d.ofCenter(session.getSpawnCenter() == null ? session.getRallyPos() : session.getSpawnCenter());
		hostile.setTarget(null);
		if (hostile.squaredDistanceTo(anchor) > 16.0D) {
			moveToward(hostile, world, anchor, 1.0D);
		} else {
			hostile.getNavigation().stop();
		}
	}

	private PlayerEntity getNearbyPlayer(ServerWorld world, HostileEntity hostile, double range) {
		return world.getClosestPlayer(
			hostile.getX(),
			hostile.getY(),
			hostile.getZ(),
			range,
			player -> player.isAlive() && !player.isSpectator()
		);
	}

	private SiegeRamEntity nearestRam(ServerWorld world, SiegeSession session, Vec3d from) {
		SiegeRamEntity nearest = null;
		double bestDistance = Double.MAX_VALUE;
		for (UUID ramId : session.getEngineIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}
			double distance = ram.squaredDistanceTo(from);
			if (distance < bestDistance) {
				bestDistance = distance;
				nearest = ram;
			}
		}
		return nearest;
	}

	private Vec3d average(List<Vec3d> positions) {
		if (positions.isEmpty()) {
			return null;
		}
		double x = 0.0D;
		double y = 0.0D;
		double z = 0.0D;
		for (Vec3d pos : positions) {
			x += pos.x;
			y += pos.y;
			z += pos.z;
		}
		return new Vec3d(x / positions.size(), y / positions.size(), z / positions.size());
	}

	private SiegeBaseState stateFor(ServerWorld world) {
		return SiegeBaseState.get(world.getServer());
	}

	private List<LivingEntity> resolvePlacedDefenders(ServerWorld world, SiegeBaseState state) {
		List<LivingEntity> defenders = new ArrayList<>();
		for (PlacedDefender placedDefender : state.getPlacedDefenders()) {
			Entity entity = world.getEntity(placedDefender.entityUuid());
			if (entity instanceof LivingEntity living && living.isAlive()) {
				defenders.add(living);
			}
		}
		return defenders;
	}

	private LivingEntity getNearbyDefenderTarget(
		HostileEntity hostile,
		List<LivingEntity> placedDefenders,
		List<Vec3d> breacherPositions,
		double range
	) {
		LivingEntity nearest = null;
		double bestDistance = range * range;
		for (LivingEntity defender : placedDefenders) {
			double attackerDistance = hostile.squaredDistanceTo(defender);
			if (attackerDistance > bestDistance) {
				continue;
			}
			if (!isNearBreacherLine(defender.getPos(), breacherPositions, range)) {
				continue;
			}
			bestDistance = attackerDistance;
			nearest = defender;
		}
		return nearest;
	}

	private boolean isNearBreacherLine(Vec3d defenderPos, List<Vec3d> breacherPositions, double range) {
		if (breacherPositions.isEmpty()) {
			return true;
		}
		double maxDistanceSq = range * range;
		for (Vec3d breacherPos : breacherPositions) {
			if (breacherPos.squaredDistanceTo(defenderPos) <= maxDistanceSq) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLivingPrimaryBreacher(ServerWorld world, SiegeSession session) {
		for (UUID attackerId : session.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (entity instanceof HostileEntity hostile && hostile.isAlive() && breachCapabilityFor(hostile, session.getRoleAssignments().getOrDefault(attackerId, UnitRole.RANGED)) == BreachCapability.PRIMARY) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLivingActiveRam(ServerWorld world, SiegeSession session) {
		for (UUID ramId : session.getEngineIds()) {
			Entity entity = world.getEntity(ramId);
			if (entity instanceof SiegeRamEntity ram && ram.isAlive()) {
				return true;
			}
		}
		return false;
	}

	private BreachCapability breachCapabilityFor(Entity entity, UnitRole role) {
		if (entity.getCommandTags().contains(BattleUnitRegistry.BREACH_PRIMARY_TAG)) {
			return BreachCapability.PRIMARY;
		}
		if (entity.getCommandTags().contains(BattleUnitRegistry.BREACH_FALLBACK_TAG)) {
			return BreachCapability.FALLBACK;
		}
		if (entity.getCommandTags().contains(BattleUnitRegistry.BREACH_NONE_TAG)) {
			return BreachCapability.NONE;
		}
		return switch (role) {
			case BREACHER, RAM -> BreachCapability.PRIMARY;
			default -> BreachCapability.FALLBACK;
		};
	}

	private int wallDamageFor(Entity entity, BreachCapability capability) {
		for (String tag : entity.getCommandTags()) {
			if (!tag.startsWith(BattleUnitRegistry.WALL_DAMAGE_TAG_PREFIX)) {
				continue;
			}
			try {
				return Math.max(0, Integer.parseInt(tag.substring(BattleUnitRegistry.WALL_DAMAGE_TAG_PREFIX.length())));
			} catch (NumberFormatException ignored) {
				break;
			}
		}
		return capability == BreachCapability.PRIMARY ? 2 : capability == BreachCapability.FALLBACK ? 1 : 0;
	}

	private void handleFallbackBreachLogging(SiegeSession session, boolean fallbackBreachEnabled, boolean hasLivingPrimaryBreacher, boolean hasLivingActiveRam) {
		String sessionKey = session.getStartedGameTime() + ":" + String.valueOf(session.getObjectivePos());
		if (fallbackBreachEnabled) {
			if (fallbackLoggedSessions.add(sessionKey)) {
				AgesOfSiegeMod.LOGGER.info(
					"Fallback breach activated for siege session {}. primaryBreacherAlive={}, activeRamAlive={}. Remaining attackers may breach walls at reduced damage.",
					sessionKey,
					hasLivingPrimaryBreacher,
					hasLivingActiveRam
				);
			}
			return;
		}
		fallbackLoggedSessions.remove(sessionKey);
	}
}
