package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.entity.ModEntities;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.siege.service.ObjectiveService;
import com.stamperl.agesofsiege.siege.service.RamController;
import com.stamperl.agesofsiege.siege.service.SiegeRewardService;
import com.stamperl.agesofsiege.siege.service.SiegeSpawner;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SiegeManager {
	private static final ObjectiveService OBJECTIVE_SERVICE = new ObjectiveService();
	private static final SiegeSpawner SIEGE_SPAWNER = new SiegeSpawner();
	private static final SiegeRewardService REWARD_SERVICE = new SiegeRewardService();
	private static final RamController RAM_CONTROLLER = new RamController();
	private static final boolean DEBUG_LOGGING = true;
	private static final int MIN_SPAWN_RADIUS = 28;
	private static final int MAX_SPAWN_RADIUS = 36;
	private static final double PLAYER_AGGRO_RANGE = 10.0D;
	private static final double SUPPORT_PLAYER_AGGRO_RANGE = 18.0D;
	private static final double OBJECTIVE_ATTACK_RANGE = 2.25D;
	private static final double BREACH_CHECK_RANGE = 3.0D;
	private static final double BREACH_VERTICAL_REACH = 3.0D;
	private static final double BREACH_SLOT_RADIUS = 1.75D;
	private static final double BREACH_SIDESTEP_DISTANCE = 1.5D;
	private static final double SUPPORT_FORMATION_RADIUS = 6.0D;
	private static final double SUPPORT_FORMATION_DEPTH = 4.0D;
	private static final double BREACHER_GUARD_RADIUS = 3.0D;
	private static final double BREACHER_GUARD_DEPTH = 2.0D;
	private static final double MAX_BREACH_SCAN_DEPTH = 96.0D;
	private static final double ASSAULT_LANE_CHECK_DISTANCE = 250.0D;
	private static final int INFANTRY_PATH_SEARCH_LIMIT = 4096;
	private static final double LANE_CHECK_INITIAL_SKIP = 6.0D;
	private static final double[] BREACH_LANES = {-6.0D, -3.0D, 0.0D, 3.0D, 6.0D};
	private static final double[] LANE_WIDTH_SAMPLES = {-2.0D, -1.0D, 0.0D, 1.0D, 2.0D};
	private static final double[] INFANTRY_LANE_WIDTH_SAMPLES = {-0.75D, 0.0D, 0.75D};
	private static final double INFANTRY_APPROACH_STEP = 2.0D;
	private static final double INFANTRY_MAX_STEP_UP = 0.75D;
	private static final double INFANTRY_HOLE_ESCAPE_DEPTH = 0.75D;
	private static final double RAM_MOVE_STEP = 0.06D;
	private static final double RAM_BACKOFF_DISTANCE = 4.0D;
	private static final int RAM_BACKOFF_TICKS = 60;
	private static final double RAM_ATTACK_RANGE = 3.2D;
	private static final int RAM_ATTACK_INTERVAL_TICKS = 30;
	private static final int RAM_BASE_ATTACK_DAMAGE = 25;
	private static final int RAM_SPLASH_ATTACK_DAMAGE = 12;
	private static final int RAM_MAX_ATTACK_BONUS = 20;
	private static final double RAM_ESCORT_DEFEND_RANGE = 7.0D;
	private static final double RAM_ESCORT_LEASH_RANGE = 4.0D;
	private static final double PLAYER_CHASE_WALL_CHECK_DEPTH = 16.0D;
	private static final String RAM_ESCORT_TAG = "ages_of_siege_ram_escort";
	private static final String RANGED_ROLE_TAG = "ages_of_siege_ranged";
	private static final String BREACHER_ROLE_TAG = "ages_of_siege_breacher";
	private static final int FORMATION_SPACING = 3;

	private enum AssaultMode {
		RUSH_BANNER,
		SUPPORT_RAM,
		INFANTRY_BREACH
	}

	private record AssaultDecision(
		AssaultMode mode,
		UUID leadUnitId,
		BreachPlan breachPlan,
		boolean pathReachable,
		boolean activeRam,
		boolean breachTeamAlive
	) {
	}

	private record InfantryPathResult(boolean reachable, BlockPos startFoot, int explored, int pathSteps, BlockPos frontierTarget) {
	}

	private record PathNode(BlockPos pos, int steps) {
	}

	private record BreachGridPlan(BlockPos anchorBase, Vec3d forward, Vec3d right, List<BlockPos> cells) {
	}

	private record BreachRouteScore(
		double totalScore,
		double sectionCost,
		double routeScore,
		Vec3d breachExit,
		boolean postReachable,
		int postExplored,
		int postSteps,
		BlockPos postFrontier
	) {
	}

	private record BreachPlan(
		BlockPos anchorBase,
		BreachGridPlan gridPlan,
		List<BlockPos> requiredBlocks,
		Vec3d stagingPoint,
		Vec3d breachExit,
		boolean doorwayOpen
	) {
		private boolean isActionable(ServerWorld world) {
			return requiredBlocks.stream().anyMatch(cell -> WallTier.from(world.getBlockState(cell)) != WallTier.NONE);
		}

		private boolean isOpen(ServerWorld world) {
			return !isActionable(world) && breachExit != null;
		}
	}

	private record CorridorBreachAnalysis(
		BlockPos anchor,
		double firstSliceCost,
		double totalCorridorCost,
		int blockedSlices,
		Vec3d breachExit,
		InfantryPathResult postPath
	) {
	}

	private SiegeManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SiegeManager::tickServer);
	}

	private static void tickServer(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		if (!state.hasBase()) {
			return;
		}
		if (!state.isSiegeActive() && !state.isSiegePending()) {
			return;
		}

		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			state.endSiege(true, false);
			return;
		}

		BlockPos objectivePos = state.getBasePos();
		if (!OBJECTIVE_SERVICE.isObjectivePresent(world, state.getActiveSession(), objectivePos)) {
			failActiveSiege(world, state, "The Settlement Standard was destroyed. The siege is lost.");
			return;
		}

		if (state.isSiegePending()) {
			tickPendingSiege(server, world, state, objectivePos);
			return;
		}

		if (!state.isSiegeActive()) {
			return;
		}

		AssaultDecision decision = evaluateAssaultDecision(world, state, objectivePos);
		if (shouldHoldDeployment(state, decision)) {
			holdWavePosition(world, state, objectivePos);
			state.tickDeploymentHold();
			return;
		}
		AssaultMode assaultMode = decision.mode();
		state.setBreachOpen(assaultMode == AssaultMode.RUSH_BANNER);
		state.setRushTicks(assaultMode == AssaultMode.RUSH_BANNER ? state.getRushTicks() + 1 : 0);
		if (DEBUG_LOGGING && world.getTime() % 20L == 0L) {
			AgesOfSiegeMod.LOGGER.info(
				"[SiegeDebug] mode={} age={} attackers={} rams={} breachOpen={} rushTicks={} activeBreachTeam={} breachedWalls={}",
				assaultMode,
				state.getAgeLevel(),
				state.getAttackerIds().size(),
				state.getRamIds().size(),
				state.isBreachOpen(),
				state.getRushTicks(),
				hasActiveBreachTeam(world, state),
				state.getBreachedWallBlocks()
			);
		}

		List<UUID> livingAttackers = new ArrayList<>();
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}

			livingAttackers.add(attackerId);
			updateAttacker(hostile, world, objectivePos, state, decision);
		}

		state.replaceAttackers(livingAttackers);

		List<UUID> livingRams = new ArrayList<>();
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}

			livingRams.add(ramId);
			updateRam(ram, world, objectivePos, decision);
		}

		state.replaceRams(livingRams);

		if (livingAttackers.isEmpty() && livingRams.isEmpty()) {
			int previousAge = state.getAgeLevel();
			var completedSession = state.getActiveSession();
			REWARD_SERVICE.dropVictoryRewards(world, completedSession, objectivePos, state.getAgeLevel(), state.getSelectedSiegeId());
			state.endSiege(false, true);
			server.getPlayerManager().broadcast(Text.literal("The siege wave has been defeated."), false);
			if (state.getAgeLevel() > previousAge) {
				server.getPlayerManager().broadcast(
					Text.literal("Age advanced: " + state.getAgeName() + " unlocked."),
					false
				);
			}
			sendAgeProgressMessage(server, state);
		}
	}

	private static void tickPendingSiege(MinecraftServer server, ServerWorld world, SiegeBaseState state, BlockPos objectivePos) {
		holdWavePosition(world, state, objectivePos);
		AssaultDecision decision = evaluateAssaultDecision(world, state, objectivePos);
		boolean plannerReady = decision.pathReachable() || (decision.breachPlan() != null && decision.breachPlan().isActionable(world));
		int currentTicks = state.getCountdownTicks();
		int remainingTicks = state.tickCountdown();
		if (plannerReady && remainingTicks <= 20) {
			state.activateStagedSiege(server);
			return;
		}
		if (remainingTicks <= 0) {
			state.activateStagedSiege(server);
			return;
		}

		int remainingSeconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0D));
		if (currentTicks != remainingTicks && remainingTicks % 20 == 0 && (remainingSeconds <= 5 || remainingSeconds == 10)) {
			server.getPlayerManager().broadcast(
				Text.literal("Siege begins in " + remainingSeconds + " seconds."),
				false
			);
		}
	}

	private static boolean shouldHoldDeployment(SiegeBaseState state, AssaultDecision decision) {
		if (state.getDeploymentHoldTicks() <= 0) {
			return false;
		}
		if (decision.pathReachable()) {
			return false;
		}
		return decision.breachPlan() == null;
	}

	private static void holdWavePosition(ServerWorld world, SiegeBaseState state, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			hostile.setTarget(null);
			hostile.getNavigation().stop();
			faceTowards(hostile, objectiveCenter);
		}
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}
			ram.setVelocity(0.0D, 0.0D, 0.0D);
			faceTowards(ram, objectiveCenter);
		}
	}

	private static void updateAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, SiegeBaseState state, AssaultDecision decision) {
		suppressPotionUse(hostile);
		if (hostile.getCommandTags().contains(RAM_ESCORT_TAG)) {
			updateRamEscort(hostile, world, state, decision);
			return;
		}

		if (hostile.getCommandTags().contains(RANGED_ROLE_TAG)) {
			updateRangedAttacker(hostile, world, objectivePos, state, decision);
			return;
		}

		updateBreacherAttacker(hostile, world, objectivePos, decision);
	}

	public static boolean startSiege(MinecraftServer server, SiegeBaseState state) {
		if (!state.hasBase() || state.isSiegeActive() || state.isSiegePending()) {
			return false;
		}

		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			return false;
		}

		if (!isObjectivePresent(world, state.getBasePos())) {
			return false;
		}

		state.beginCountdown(server, 10);
		spawnWave(server, state);
		return true;
	}

	public static void failActiveSiege(ServerWorld world, SiegeBaseState state, String message) {
		SIEGE_SPAWNER.despawnAttackers(world, state.getAttackerIds());
		SIEGE_SPAWNER.despawnRams(world, state.getRamIds());
		state.endSiege(true, false);
		world.getServer().getPlayerManager().broadcast(Text.literal(message), false);
	}

	private static void spawnWave(MinecraftServer server, SiegeBaseState state) {
		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			state.endSiege(true, false);
			return;
		}

		SIEGE_SPAWNER.spawnWave(server, world, state, state.getActiveSession());
		sendAgeProgressMessage(server, state);
	}

	private static int getWaveSize(SiegeBaseState state) {
		// Scale waves from actual victories, not failed attempts.
		// Age unlocks still change composition and rewards, but each win should
		// make the next siege feel tougher even within the same age tier.
		return 6 + Math.min(state.getCompletedSieges(), 8);
	}

	private static HostileEntity createAttacker(ServerWorld world, SiegeBaseState state, int index) {
		int age = state.getAgeLevel();
		if (age <= 0) {
			return EntityType.PILLAGER.create(world);
		}
		if (age == 1) {
			return index % 3 == 0 ? EntityType.VINDICATOR.create(world) : EntityType.PILLAGER.create(world);
		}
		if (age == 2) {
			return index % 2 == 0 ? EntityType.VINDICATOR.create(world) : EntityType.PILLAGER.create(world);
		}
		return index % 3 == 2 ? EntityType.PILLAGER.create(world) : EntityType.VINDICATOR.create(world);
	}

	private static List<UUID> spawnRamEscort(ServerWorld world, SiegeBaseState state, BlockPos ramSpawn) {
		List<UUID> escortIds = new ArrayList<>();
		int escortCount = state.getAgeLevel() >= 3 ? 3 : 2;
		for (int i = 0; i < escortCount; i++) {
			HostileEntity escort = i == escortCount - 1
				? EntityType.PILLAGER.create(world)
				: EntityType.VINDICATOR.create(world);
			if (escort == null) {
				continue;
			}

			BlockPos escortPos = ramSpawn.add((i % 2 == 0 ? 2 : -2), 0, i == 0 ? 2 : -2);
			BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, escortPos).up();
			escort.refreshPositionAndAngles(
				top.getX() + 0.5D,
				top.getY(),
				top.getZ() + 0.5D,
				world.random.nextFloat() * 360.0F,
				0.0F
			);
			escort.addCommandTag(RAM_ESCORT_TAG);
			escort.setCanPickUpLoot(false);
			escort.setPersistent();
			equipAttacker(escort, state);
			world.spawnEntity(escort);
			escortIds.add(escort.getUuid());
		}
		return escortIds;
	}

	private static void equipAttacker(HostileEntity attacker, SiegeBaseState state) {
		MedievalLoadouts.RaiderRole role = attacker instanceof VindicatorEntity
			? MedievalLoadouts.RaiderRole.BREACHER
			: MedievalLoadouts.RaiderRole.RANGED;
		attacker.addCommandTag(role == MedievalLoadouts.RaiderRole.BREACHER ? BREACHER_ROLE_TAG : RANGED_ROLE_TAG);
		MedievalLoadouts.equipAttacker(attacker, role, state.getAgeLevel(), attacker.getRandom());
	}

	private static SiegeRamEntity createBatteringRam(ServerWorld world, BlockPos spawnPos) {
		SiegeRamEntity ram = ModEntities.SIEGE_RAM.create(world);
		if (ram == null) {
			return null;
		}

		ram.refreshPositionAndAngles(
			spawnPos.getX() + 0.5D,
			spawnPos.getY(),
			spawnPos.getZ() + 0.5D,
			world.random.nextFloat() * 360.0F,
			0.0F
		);
		ram.setCustomName(Text.literal("Battering Ram"));
		ram.setCustomNameVisible(true);
		ram.setAiDisabled(true);
		ram.setPersistent();
		return ram;
	}

	private static void updateRam(SiegeRamEntity ram, ServerWorld world, BlockPos objectivePos, AssaultDecision decision) {
		SiegeBaseState state = SiegeBaseState.get(world.getServer());
		BreachPlan breachPlan = decision.breachPlan();
		RAM_CONTROLLER.updateRam(
			ram,
			world,
			state,
			state.getActiveSession(),
			objectivePos,
			decision.mode() == AssaultMode.RUSH_BANNER,
			state.getRushTicks(),
			breachPlan != null ? breachPlan.anchorBase() : null
		);
	}

	private static void updateRamEscort(HostileEntity escort, ServerWorld world, SiegeBaseState state, AssaultDecision decision) {
		AssaultMode assaultMode = decision.mode();
		SiegeRamEntity ram = getNearestRam(world, state, escort.getPos());
		if (ram == null) {
			escort.setTarget(null);
			if (!hasActiveBreachTeam(world, state) && tryFallbackWallAssault(escort, world, state.getBasePos(), 1, 40)) {
				return;
			}
			return;
		}

		PlayerEntity playerTarget = world.getClosestPlayer(
			ram.getX(),
			ram.getY(),
			ram.getZ(),
			RAM_ESCORT_DEFEND_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, escort.getPos(), candidate.getPos())
		);

		if (playerTarget != null) {
			escort.setTarget(playerTarget);
			moveInfantryToward(escort, world, playerTarget.getPos(), 1.0D);
			return;
		}

		escort.setTarget(null);
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			advanceOnObjective(escort, world, state.getBasePos(), 1);
			return;
		}
		if (!hasActiveBreachTeam(world, state) && tryFallbackWallAssault(escort, world, state.getBasePos(), 1, 40)) {
			return;
		}
		Vec3d ramPos = ram.getPos();
		if (escort.squaredDistanceTo(ramPos) > RAM_ESCORT_LEASH_RANGE * RAM_ESCORT_LEASH_RANGE) {
			moveInfantryToward(escort, world, ramPos, 1.0D);
		} else {
			escort.getNavigation().stop();
		}
	}

	private static void updateBreacherAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, AssaultDecision decision) {
		if (tryEscapeHole(hostile, world)) {
			return;
		}

		AssaultMode assaultMode = decision.mode();
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		PlayerEntity playerTarget = world.getClosestPlayer(
			hostile.getX(),
			hostile.getY(),
			hostile.getZ(),
			PLAYER_AGGRO_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, hostile.getPos(), candidate.getPos())
		);

		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			moveInfantryToward(hostile, world, playerTarget.getPos(), 1.0D);
			return;
		}

		hostile.setTarget(null);
		SiegeRamEntity ram = getNearestLiveRam(world, hostile.getPos());
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			advanceOnObjective(hostile, world, objectivePos, 1);
			return;
		}

		if (assaultMode == AssaultMode.SUPPORT_RAM && ram != null) {
			Vec3d guardPosition = getBreacherGuardPosition(world, hostile, objectivePos, ram);
			if (hostile.squaredDistanceTo(guardPosition) > 3.0D) {
				moveInfantryToward(hostile, world, guardPosition, 1.0D);
			} else {
				hostile.getNavigation().stop();
				faceTowards(hostile, Vec3d.ofCenter(objectivePos));
			}
			return;
		}

		BreachPlan breachPlan = decision.breachPlan();
		BlockPos breachAnchor = breachPlan != null ? breachPlan.anchorBase() : null;
		BreachGridPlan gridPlan = breachPlan != null ? breachPlan.gridPlan() : null;
		BlockPos breachTarget = getAssignedExecutableBreachTarget(world, breachPlan, hostile);
		Vec3d breachApproach = getAssignedBreachApproachPosition(world, gridPlan, hostile, breachTarget);
		if (breachPlan != null && breachPlan.stagingPoint() != null && hostile.squaredDistanceTo(breachPlan.stagingPoint()) > 6.0D * 6.0D) {
			moveInfantryToward(hostile, world, breachPlan.stagingPoint(), 1.0D);
			return;
		}
		if (DEBUG_LOGGING && world.getTime() % 40L == 0L) {
			AgesOfSiegeMod.LOGGER.info(
				"[SiegeDebug] breacher={} mode={} breachAnchor={} breachTarget={} breachApproach={} requiredBlocks={} pos={}",
				hostile.getType().getUntranslatedName(),
				assaultMode,
				breachAnchor,
				breachTarget,
				breachApproach,
				breachPlan != null ? breachPlan.requiredBlocks().size() : 0,
				hostile.getBlockPos()
			);
		}
		if (breachTarget != null && breachApproach != null && tryAttackWallTarget(hostile, world, breachTarget, breachApproach, 1, 15)) {
			return;
		}

		advanceOnObjective(hostile, world, objectivePos, 1);
	}

	private static void updateRangedAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, SiegeBaseState state, AssaultDecision decision) {
		AssaultMode assaultMode = decision.mode();
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			PlayerEntity playerTarget = world.getClosestPlayer(
				hostile.getX(),
				hostile.getY(),
				hostile.getZ(),
				SUPPORT_PLAYER_AGGRO_RANGE,
				candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, hostile.getPos(), candidate.getPos())
			);
			if (playerTarget != null) {
				hostile.setTarget(playerTarget);
				return;
			}

			hostile.setTarget(null);
			advanceOnObjective(hostile, world, objectivePos, 1);
			return;
		}

		if (!hasActiveBreachTeam(world, state) && tryFallbackWallAssault(hostile, world, objectivePos, 1, 40)) {
			if (DEBUG_LOGGING && world.getTime() % 40L == 0L) {
				AgesOfSiegeMod.LOGGER.info(
					"[SiegeDebug] fallback-wall-assault attacker={} pos={}",
					hostile.getType().getUntranslatedName(),
					hostile.getBlockPos()
				);
			}
			return;
		}

		Vec3d supportFocus = getSupportFocus(world, hostile, objectivePos, state);
		PlayerEntity playerTarget = world.getClosestPlayer(
			supportFocus.x,
			supportFocus.y,
			supportFocus.z,
			SUPPORT_PLAYER_AGGRO_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, hostile.getPos(), candidate.getPos())
		);

		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			if (hostile.squaredDistanceTo(playerTarget) < 36.0D) {
				Vec3d retreat = hostile.getPos().subtract(playerTarget.getPos()).normalize();
				Vec3d retreatPos = hostile.getPos().add(retreat.multiply(3.0D));
				moveInfantryToward(hostile, world, retreatPos, 1.0D);
			}
			return;
		}

		hostile.setTarget(null);
		Vec3d stagingPos = getSupportFormationPosition(world, hostile, objectivePos, supportFocus);
		if (hostile.squaredDistanceTo(stagingPos) > 4.0D) {
			moveInfantryToward(hostile, world, stagingPos, 1.0D);
		} else {
			hostile.getNavigation().stop();
			faceTowards(hostile, Vec3d.ofCenter(objectivePos));
		}
	}

	private static AssaultDecision evaluateAssaultDecision(ServerWorld world, SiegeBaseState state, BlockPos objectivePos) {
		if (!state.isAssaultModePrimed()) {
			state.setAssaultModePrimed(true);
			return new AssaultDecision(
				getDefaultAssaultMode(state),
				null,
				null,
				false,
				!state.getRamIds().isEmpty(),
				hasActiveBreachTeam(world, state)
			);
		}

		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		HostileEntity furthestBreacher = getFurthestBreacher(world, state, objectiveCenter);
		SiegeRamEntity furthestRam = getFurthestRam(world, state, objectiveCenter);
		HostileEntity furthestAttacker = getFurthestAttacker(world, state, objectiveCenter);
		Vec3d pathSource = state.getAssaultOrigin() != null ? Vec3d.ofCenter(state.getAssaultOrigin()) : null;
		Entity pathLead = furthestBreacher != null ? furthestBreacher : (furthestAttacker != null ? furthestAttacker : furthestRam);
		boolean breachTeamAlive = hasActiveBreachTeam(world, state);
		if (pathLead == null) {
			return new AssaultDecision(AssaultMode.RUSH_BANNER, null, null, true, furthestRam != null, breachTeamAlive);
		}
		if (pathSource == null) {
			pathSource = pathLead.getPos();
		}

		InfantryPathResult directPathResult = findInfantryPath(world, pathSource, objectiveCenter, ASSAULT_LANE_CHECK_DISTANCE, 2);
		BlockPos currentAnchor = state.getPrimaryBreachTarget();
		BlockPos frontierBase = directPathResult.frontierTarget() != null ? getWallBase(directPathResult.frontierTarget(), world) : null;
		BreachPlan breachPlan = computeBreachPlan(world, state, objectivePos, furthestRam, pathSource, frontierBase);
		InfantryPathResult pathResult = directPathResult;

		if (currentAnchor == null && directPathResult.reachable()) {
			state.setPrimaryBreachTarget(null);
			logAssaultDecision(world, pathLead, pathSource, directPathResult, furthestRam != null, null, AssaultMode.RUSH_BANNER);
			return new AssaultDecision(AssaultMode.RUSH_BANNER, pathLead.getUuid(), null, true, furthestRam != null, breachTeamAlive);
		}

		if (breachPlan != null && breachPlan.isOpen(world) && breachPlan.breachExit() != null) {
			pathResult = findInfantryPath(world, breachPlan.breachExit(), objectiveCenter, ASSAULT_LANE_CHECK_DISTANCE, 2, 0.0D);
			if (pathResult.reachable()) {
				state.setPrimaryBreachTarget(null);
				logAssaultDecision(world, pathLead, breachPlan.breachExit(), pathResult, furthestRam != null, breachPlan.anchorBase(), AssaultMode.RUSH_BANNER);
				return new AssaultDecision(AssaultMode.RUSH_BANNER, pathLead.getUuid(), breachPlan, true, furthestRam != null, breachTeamAlive);
			}
		}

		AssaultMode blockedMode = furthestRam != null ? AssaultMode.SUPPORT_RAM : AssaultMode.INFANTRY_BREACH;
		Vec3d decisionSource = breachPlan != null && breachPlan.stagingPoint() != null ? breachPlan.stagingPoint() : pathSource;
		logAssaultDecision(world, pathLead, decisionSource, pathResult, furthestRam != null, breachPlan != null ? breachPlan.anchorBase() : null, blockedMode);
		return new AssaultDecision(blockedMode, pathLead.getUuid(), breachPlan, false, furthestRam != null, breachTeamAlive);
	}

	private static BreachPlan computeBreachPlan(
		ServerWorld world,
		SiegeBaseState state,
		BlockPos objectivePos,
		SiegeRamEntity furthestRam,
		Vec3d pathSource,
		BlockPos frontierBase
	) {
		BlockPos anchor = resolvePlannedBreachAnchor(world, state, objectivePos, furthestRam, pathSource, frontierBase);
		if (anchor == null) {
			return null;
		}
		BreachGridPlan gridPlan = buildBreachGridPlan(world, anchor, objectivePos);
		List<BlockPos> requiredBlocks = gridPlan.cells().stream()
			.filter(cell -> WallTier.from(world.getBlockState(cell)) != WallTier.NONE)
			.toList();
		Vec3d stagingPoint = getBreachStagingPoint(world, pathSource, anchor, objectivePos);
		Vec3d breachExit = findInfantryBreachExit(world, anchor, objectivePos, 2);
		boolean doorwayOpen = requiredBlocks.isEmpty() && breachExit != null;
		return new BreachPlan(anchor, gridPlan, requiredBlocks, stagingPoint, breachExit, doorwayOpen);
	}

	private static BlockPos resolvePlannedBreachAnchor(
		ServerWorld world,
		SiegeBaseState state,
		BlockPos objectivePos,
		SiegeRamEntity furthestRam,
		Vec3d pathSource,
		BlockPos frontierBase
	) {
		BlockPos current = state.getPrimaryBreachTarget();
		if (current != null) {
			BreachGridPlan currentPlan = buildBreachGridPlan(world, current, objectivePos);
			boolean actionable = currentPlan.cells().stream().anyMatch(cell -> WallTier.from(world.getBlockState(cell)) != WallTier.NONE);
			boolean open = !actionable && findInfantryBreachExit(world, current, objectivePos, 2) != null;
			if (actionable || open) {
				return current;
			}
		}
		BlockPos next = furthestRam != null
			? getEffectiveRamTarget(world, furthestRam, objectivePos)
			: frontierBase != null ? frontierBase : findBestBreachTarget(world, pathSource, objectivePos);
		state.setPrimaryBreachTarget(next);
		return next;
	}

	private static BlockPos resolveCommittedBreachAnchor(ServerWorld world, BlockPos anchorBase, BlockPos objectivePos) {
		if (anchorBase == null) {
			return null;
		}
		BlockPos committedAnchor = anchorBase.toImmutable();
		if (!getCommittedBreachFaceCells(world, committedAnchor, objectivePos).isEmpty()) {
			return committedAnchor;
		}

		Vec3d[] axes = getBreachAxes(anchorBase, objectivePos);
		Vec3d forward = axes[0];
		Vec3d right = axes[1];
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		int scanHeight = Math.max(3, getRamWallHeight(world, anchorBase) + 1);
		for (int depth = -1; depth <= 4; depth++) {
			for (int lateral = -3; lateral <= 3; lateral++) {
				BlockPos base = anchorBase.add(
					MathHelper.floor(forward.x * depth + right.x * lateral),
					0,
					MathHelper.floor(forward.z * depth + right.z * lateral)
				);
				for (int yOffset = 0; yOffset <= scanHeight; yOffset++) {
					BlockPos candidate = base.up(yOffset);
					WallTier tier = WallTier.from(world.getBlockState(candidate));
					if (tier == WallTier.NONE) {
						continue;
					}
					BlockPos candidateBase = normalizeGroundedBreachAnchor(world, getWallBase(candidate, world), objectivePos);
					double score = Math.max(0, depth) * 25.0D + Math.abs(lateral) * 4.0D + yOffset * 2.0D;
					if (best == null || score < bestScore) {
						best = candidateBase;
						bestScore = score;
					}
				}
			}
		}
		return best;
	}

	private static BlockPos getAssignedBreachFaceTarget(BreachGridPlan plan, HostileEntity hostile) {
		if (plan == null || plan.cells().isEmpty()) {
			return null;
		}

		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3) - 1;
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		Vec3d anchorCenter = Vec3d.ofCenter(plan.anchorBase());
		for (BlockPos cell : plan.cells()) {
			Vec3d offset = Vec3d.ofCenter(cell).subtract(anchorCenter);
			double lateral = offset.dotProduct(plan.right());
			double depth = offset.dotProduct(plan.forward());
			double height = cell.getY() - plan.anchorBase().getY();
			double lateralFromCenter = Math.abs(lateral);
			double corridorPenalty = lateralFromCenter > 1.1D ? 40.0D + (lateralFromCenter - 1.1D) * 20.0D : lateralFromCenter * 8.0D;
			double slotPenalty = Math.abs(lateral - slot) * 0.75D;
			double score = corridorPenalty + slotPenalty + depth * 32.0D + height * 3.0D;
			if (best == null || score < bestScore) {
				best = cell;
				bestScore = score;
			}
		}
		return best;
	}

	private static BlockPos getAssignedExecutableBreachTarget(ServerWorld world, BreachPlan plan, HostileEntity hostile) {
		if (plan == null || plan.requiredBlocks().isEmpty()) {
			return null;
		}

		List<BlockPos> executable = plan.requiredBlocks().stream()
			.filter(cell -> WallTier.from(world.getBlockState(cell)) != WallTier.NONE)
			.toList();
		if (executable.isEmpty()) {
			return null;
		}

		return executable.stream()
			.min(
				java.util.Comparator
					.comparingInt(BlockPos::getY)
					.thenComparingDouble(cell -> hostile.squaredDistanceTo(Vec3d.ofCenter(cell)))
			)
			.orElse(null);
	}

	private static Vec3d getAssignedBreachApproachPosition(ServerWorld world, BreachGridPlan plan, HostileEntity hostile, BlockPos target) {
		if (plan == null || target == null) {
			return null;
		}

		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3) - 1;
		Vec3d targetCenter = Vec3d.ofCenter(target);
		Vec3d baseApproach = targetCenter
			.subtract(plan.forward().multiply(1.85D))
			.add(plan.right().multiply(slot * (BREACH_SLOT_RADIUS * 0.8D)));
		double groundedY = getGroundY(world, baseApproach.x, baseApproach.z);
		return new Vec3d(baseApproach.x, groundedY, baseApproach.z);
	}

	private static Vec3d getBreachStagingPoint(ServerWorld world, Vec3d pathSource, BlockPos breachAnchor, BlockPos objectivePos) {
		Vec3d[] axes = getBreachAxes(breachAnchor, objectivePos);
		Vec3d forward = axes[0];
		Vec3d anchorCenter = Vec3d.ofCenter(breachAnchor);
		Vec3d towardAnchor = anchorCenter.subtract(pathSource);
		Vec3d staging = anchorCenter.subtract(forward.multiply(4.0D));
		if (towardAnchor.lengthSquared() > 0.001D && towardAnchor.dotProduct(forward) < 0.0D) {
			staging = anchorCenter.add(forward.multiply(4.0D));
		}
		double groundedY = getGroundY(world, staging.x, staging.z);
		return new Vec3d(staging.x, groundedY, staging.z);
	}

	private static boolean isBreachZoneActive(ServerWorld world, BlockPos anchorBase, BlockPos objectivePos) {
		return !buildBreachGridPlan(world, anchorBase, objectivePos).cells().isEmpty();
	}

	private static BlockPos findNearestWallAroundAnchor(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		BreachGridPlan plan = buildBreachGridPlan(world, breachAnchor, objectivePos);
		return plan.cells().isEmpty() ? null : plan.cells().get(0);
	}

	private static Vec3d[] getBreachAxes(BlockPos breachAnchor, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d anchorCenter = Vec3d.ofCenter(breachAnchor);
		Vec3d forward = new Vec3d(objectiveCenter.x - anchorCenter.x, 0.0D, objectiveCenter.z - anchorCenter.z);
		if (forward.lengthSquared() < 0.001D) {
			forward = new Vec3d(1.0D, 0.0D, 0.0D);
		}
		forward = forward.normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		return new Vec3d[] {forward, right};
	}

	private static BreachGridPlan buildBreachGridPlan(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		BlockPos normalizedAnchor = normalizeGroundedBreachAnchor(world, breachAnchor, objectivePos);
		Vec3d[] axes = getBreachAxes(normalizedAnchor, objectivePos);
		Vec3d forward = axes[0];
		Vec3d right = axes[1];
		List<BlockPos> cells = getCommittedBreachFaceCells(world, normalizedAnchor, objectivePos);
		if (cells.isEmpty()) {
			cells = getTwoByTwoBreachFaceCells(world, normalizedAnchor, objectivePos);
		}
		cells.sort((a, b) -> compareBreachCells(a, b, normalizedAnchor, forward, right));
		return new BreachGridPlan(normalizedAnchor, forward, right, cells);
	}

	private static BlockPos normalizeGroundedBreachAnchor(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		if (breachAnchor == null) {
			return null;
		}
		List<BlockPos> faceCells = getTwoByTwoBreachFaceCellsInternal(world, breachAnchor, objectivePos);
		if (faceCells.isEmpty()) {
			return breachAnchor.toImmutable();
		}
		return faceCells.stream()
			.min(java.util.Comparator
				.comparingInt(BlockPos::getY)
				.thenComparingDouble(cell -> cell.getSquaredDistance(breachAnchor)))
			.map(BlockPos::toImmutable)
			.orElse(breachAnchor.toImmutable());
	}

	private static List<BlockPos> getTwoByTwoBreachFaceCells(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		breachAnchor = normalizeGroundedBreachAnchor(world, breachAnchor, objectivePos);
		return getTwoByTwoBreachFaceCellsInternal(world, breachAnchor, objectivePos);
	}

	private static List<BlockPos> getCommittedBreachFaceCells(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		if (breachAnchor == null) {
			return new ArrayList<>();
		}
		Vec3d[] axes = getBreachAxes(breachAnchor, objectivePos);
		Vec3d right = axes[1];
		List<BlockPos> outerColumns = getOutermostBreachColumns(world, breachAnchor, axes[0], right);
		BlockPos secondaryColumn = chooseAdjacentBreachColumn(breachAnchor, outerColumns, breachAnchor, right)
			.orElseGet(() -> findImmediateAdjacentBreachColumn(world, breachAnchor, right));

		java.util.LinkedHashSet<BlockPos> cells = new java.util.LinkedHashSet<>();
		addBreachColumnCells(world, breachAnchor, cells);
		if (secondaryColumn != null) {
			addBreachColumnCells(world, secondaryColumn, cells);
		}
		return new ArrayList<>(cells);
	}

	private static BlockPos findImmediateAdjacentBreachColumn(ServerWorld world, BlockPos primaryColumn, Vec3d right) {
		List<BlockPos> candidates = new ArrayList<>();
		for (int direction : new int[] {-1, 1}) {
			BlockPos adjacent = primaryColumn.add(
				MathHelper.floor(Math.signum(right.x) * direction),
				0,
				MathHelper.floor(Math.signum(right.z) * direction)
			);
			for (int yOffset = 0; yOffset <= 1; yOffset++) {
				if (WallTier.from(world.getBlockState(adjacent.up(yOffset))) != WallTier.NONE) {
					candidates.add(adjacent.toImmutable());
					break;
				}
			}
		}
		return candidates.stream()
			.min(java.util.Comparator.comparingDouble(candidate -> candidate.getSquaredDistance(primaryColumn)))
			.orElse(null);
	}

	private static List<BlockPos> getTwoByTwoBreachFaceCellsInternal(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		Vec3d[] axes = getBreachAxes(breachAnchor, objectivePos);
		Vec3d forward = axes[0];
		Vec3d right = axes[1];
		List<BlockPos> outerColumns = getOutermostBreachColumns(world, breachAnchor, forward, right);
		if (outerColumns.isEmpty()) {
			return new ArrayList<>();
		}

		BlockPos primaryColumn = outerColumns.stream()
			.min(java.util.Comparator.comparingDouble(column -> column.getSquaredDistance(breachAnchor)))
			.orElse(outerColumns.get(0));
		BlockPos secondaryColumn = chooseAdjacentBreachColumn(primaryColumn, outerColumns, breachAnchor, right)
			.orElse(primaryColumn);

		java.util.LinkedHashSet<BlockPos> cells = new java.util.LinkedHashSet<>();
		addBreachColumnCells(world, primaryColumn, cells);
		addBreachColumnCells(world, secondaryColumn, cells);
		return new ArrayList<>(cells);
	}

	private static java.util.Optional<BlockPos> chooseAdjacentBreachColumn(
		BlockPos primaryColumn,
		List<BlockPos> outerColumns,
		BlockPos breachAnchor,
		Vec3d right
	) {
		Vec3d primaryCenter = Vec3d.ofCenter(primaryColumn);
		return outerColumns.stream()
			.filter(column -> !column.equals(primaryColumn))
			.filter(column -> column.getY() == primaryColumn.getY())
			.filter(column -> {
				Vec3d delta = Vec3d.ofCenter(column).subtract(primaryCenter);
				double lateral = Math.abs(delta.dotProduct(right));
				double horizontalDistance = Math.sqrt(squaredHorizontalDistance(Vec3d.ofCenter(column), primaryCenter));
				return lateral <= 1.25D && horizontalDistance <= 1.75D;
			})
			.min(java.util.Comparator
				.comparingDouble((BlockPos column) -> column.getSquaredDistance(breachAnchor))
				.thenComparingDouble(column -> Math.abs(column.getY() - primaryColumn.getY())));
	}

	private static List<BlockPos> getOutermostBreachColumns(ServerWorld world, BlockPos breachAnchor, Vec3d forward, Vec3d right) {
		List<BlockPos> columns = new ArrayList<>();
		double minDepth = Double.MAX_VALUE;
		for (int depth = 0; depth <= 3; depth++) {
			for (int lateral = -2; lateral <= 2; lateral++) {
				BlockPos base = breachAnchor.add(
					MathHelper.floor(right.x * lateral + forward.x * depth),
					0,
					MathHelper.floor(right.z * lateral + forward.z * depth)
				);
				boolean blocked = false;
				for (int yOffset = 0; yOffset <= 1; yOffset++) {
					if (WallTier.from(world.getBlockState(base.up(yOffset))) != WallTier.NONE) {
						blocked = true;
						break;
					}
				}
				if (!blocked) {
					continue;
				}
				double candidateDepth = Vec3d.ofCenter(base).subtract(Vec3d.ofCenter(breachAnchor)).dotProduct(forward);
				if (candidateDepth < minDepth - 0.01D) {
					minDepth = candidateDepth;
					columns.clear();
				}
				if (candidateDepth <= minDepth + 0.75D && columns.stream().noneMatch(existing -> existing.equals(base))) {
					columns.add(base.toImmutable());
				}
			}
		}
		return columns;
	}

	private static void addBreachColumnCells(ServerWorld world, BlockPos columnBase, java.util.LinkedHashSet<BlockPos> cells) {
		for (int yOffset = 0; yOffset <= 1; yOffset++) {
			BlockPos candidate = columnBase.up(yOffset);
			if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
				cells.add(candidate.toImmutable());
			}
		}
	}

	private static int compareBreachCells(BlockPos a, BlockPos b, BlockPos anchorBase, Vec3d forward, Vec3d right) {
		Vec3d anchorCenter = Vec3d.ofCenter(anchorBase);
		Vec3d aOffset = Vec3d.ofCenter(a).subtract(anchorCenter);
		Vec3d bOffset = Vec3d.ofCenter(b).subtract(anchorCenter);
		double aDepth = aOffset.dotProduct(forward);
		double bDepth = bOffset.dotProduct(forward);
		if (aDepth != bDepth) {
			return Double.compare(aDepth, bDepth);
		}
		double aLateral = Math.abs(aOffset.dotProduct(right));
		double bLateral = Math.abs(bOffset.dotProduct(right));
		if (aLateral != bLateral) {
			return Double.compare(aLateral, bLateral);
		}
		return Integer.compare(a.getY(), b.getY());
	}

	private static AssaultMode getDefaultAssaultMode(SiegeBaseState state) {
		return state.getRamIds().isEmpty()
			? AssaultMode.INFANTRY_BREACH
			: AssaultMode.SUPPORT_RAM;
	}

	private static SiegeRamEntity getFurthestRam(ServerWorld world, SiegeBaseState state, Vec3d objectiveCenter) {
		SiegeRamEntity furthest = null;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}
			double distance = ram.squaredDistanceTo(objectiveCenter);
			if (distance > bestDistance) {
				bestDistance = distance;
				furthest = ram;
			}
		}
		return furthest;
	}

	private static HostileEntity getFurthestBreacher(ServerWorld world, SiegeBaseState state, Vec3d objectiveCenter) {
		HostileEntity furthest = null;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			if (!hostile.getCommandTags().contains(BREACHER_ROLE_TAG)) {
				continue;
			}
			double distance = hostile.squaredDistanceTo(objectiveCenter);
			if (distance > bestDistance) {
				bestDistance = distance;
				furthest = hostile;
			}
		}
		return furthest;
	}

	private static HostileEntity getFurthestAttacker(ServerWorld world, SiegeBaseState state, Vec3d objectiveCenter) {
		HostileEntity furthest = null;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			double distance = hostile.squaredDistanceTo(objectiveCenter);
			if (distance > bestDistance) {
				bestDistance = distance;
				furthest = hostile;
			}
		}
		return furthest;
	}

	private static SiegeRamEntity getNearestRam(ServerWorld world, SiegeBaseState state, Vec3d fromPos) {
		SiegeRamEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}

			double distance = ram.squaredDistanceTo(fromPos);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = ram;
			}
		}
		return nearest;
	}

	private static SiegeRamEntity getNearestLiveRam(ServerWorld world, Vec3d fromPos) {
		SiegeRamEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (SiegeRamEntity ram : world.getEntitiesByClass(SiegeRamEntity.class, new Box(fromPos.x - 24.0D, fromPos.y - 8.0D, fromPos.z - 24.0D, fromPos.x + 24.0D, fromPos.y + 8.0D, fromPos.z + 24.0D), Entity::isAlive)) {
			double distance = ram.squaredDistanceTo(fromPos);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = ram;
			}
		}
		return nearest;
	}

	private static HostileEntity getNearestFriendlyBreacher(ServerWorld world, SiegeBaseState state, Vec3d fromPos) {
		HostileEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			if (!hostile.getCommandTags().contains(BREACHER_ROLE_TAG)) {
				continue;
			}

			double distance = hostile.squaredDistanceTo(fromPos);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = hostile;
			}
		}
		return nearest;
	}

	private static boolean hasActiveBreachTeam(ServerWorld world, SiegeBaseState state) {
		if (getNearestRam(world, state, Vec3d.ofCenter(state.getBasePos())) != null) {
			return true;
		}
		return getNearestFriendlyBreacher(world, state, Vec3d.ofCenter(state.getBasePos())) != null;
	}

	private static Vec3d getSupportFocus(ServerWorld world, HostileEntity hostile, BlockPos objectivePos, SiegeBaseState state) {
		SiegeRamEntity ram = getNearestRam(world, state, hostile.getPos());
		if (ram != null) {
			return ram.getPos();
		}

		HostileEntity breacher = getNearestFriendlyBreacher(world, state, hostile.getPos());
		if (breacher != null) {
			return breacher.getPos();
		}

		BlockPos breachTarget = state.getPrimaryBreachTarget();
		if (breachTarget == null) {
			Vec3d source = state.getAssaultOrigin() != null ? Vec3d.ofCenter(state.getAssaultOrigin()) : hostile.getPos();
			breachTarget = findBestBreachTarget(world, source, objectivePos);
		}
		return breachTarget != null ? Vec3d.ofCenter(breachTarget) : Vec3d.ofCenter(objectivePos);
	}

	private static void suppressPotionUse(HostileEntity hostile) {
		if (isPotionItem(hostile.getMainHandStack())) {
			hostile.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		if (isPotionItem(hostile.getOffHandStack())) {
			hostile.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
	}

	private static boolean isPotionItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof PotionItem
			|| stack.getItem() instanceof SplashPotionItem
			|| stack.getItem() instanceof LingeringPotionItem
			|| stack.getItem() instanceof ThrowablePotionItem;
	}

	private static Vec3d getSupportFormationPosition(ServerWorld world, HostileEntity hostile, BlockPos objectivePos, Vec3d supportFocus) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(supportFocus);
		if (approach.lengthSquared() < 0.001D) {
			approach = objectiveCenter.subtract(hostile.getPos());
		}
		if (approach.lengthSquared() < 0.001D) {
			approach = new Vec3d(1.0D, 0.0D, 0.0D);
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int slot = Math.floorMod(hostile.getUuid().hashCode(), 4);
		double lateral = switch (slot) {
			case 0 -> -SUPPORT_FORMATION_RADIUS;
			case 1 -> -SUPPORT_FORMATION_RADIUS / 3.0D;
			case 2 -> SUPPORT_FORMATION_RADIUS / 3.0D;
			default -> SUPPORT_FORMATION_RADIUS;
		};
		double depth = (slot % 2 == 0) ? SUPPORT_FORMATION_DEPTH : SUPPORT_FORMATION_DEPTH + 2.0D;
		Vec3d raw = supportFocus.subtract(forward.multiply(depth)).add(right.multiply(lateral));
		double groundedY = getGroundY(world, raw.x, raw.z);
		return new Vec3d(raw.x, groundedY, raw.z);
	}

	private static Vec3d getBreacherGuardPosition(ServerWorld world, HostileEntity hostile, BlockPos objectivePos, SiegeRamEntity ram) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(ram.getPos());
		if (approach.lengthSquared() < 0.001D) {
			approach = objectiveCenter.subtract(hostile.getPos());
		}
		if (approach.lengthSquared() < 0.001D) {
			approach = new Vec3d(1.0D, 0.0D, 0.0D);
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3);
		double lateral = switch (slot) {
			case 0 -> -BREACHER_GUARD_RADIUS;
			case 1 -> 0.0D;
			default -> BREACHER_GUARD_RADIUS;
		};
		double depth = slot == 1 ? BREACHER_GUARD_DEPTH + 1.0D : BREACHER_GUARD_DEPTH;
		Vec3d raw = ram.getPos().subtract(forward.multiply(depth)).add(right.multiply(lateral));
		double groundedY = getGroundY(world, raw.x, raw.z);
		return new Vec3d(raw.x, groundedY, raw.z);
	}

	private static double getGroundY(ServerWorld world, double x, double z) {
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
		return top.getY();
	}

	private static boolean isBlockedByWallTop(ServerWorld world, double x, double y, double z) {
		BlockPos supportPos = BlockPos.ofFloored(x, y - 1.0D, z);
		return WallTier.from(world.getBlockState(supportPos)) != WallTier.NONE;
	}

	private static boolean hasRoomForRam(SiegeRamEntity ram, ServerWorld world, double x, double y, double z) {
		Box currentBox = ram.getBoundingBox();
		double halfWidth = (currentBox.maxX - currentBox.minX) / 2.0D;
		double halfDepth = (currentBox.maxZ - currentBox.minZ) / 2.0D;
		double height = currentBox.maxY - currentBox.minY;
		Box targetBox = new Box(
			x - halfWidth,
			y,
			z - halfDepth,
			x + halfWidth,
			y + height,
			z + halfDepth
		);
		return world.isSpaceEmpty(ram, targetBox);
	}

	private static void faceTowards(Entity entity, Vec3d target) {
		Vec3d delta = target.subtract(entity.getPos());
		if (delta.lengthSquared() < 0.0001D) {
			return;
		}

		float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
		entity.setYaw(yaw);
		entity.setBodyYaw(yaw);
		entity.setHeadYaw(yaw);
		entity.setPitch(0.0F);
	}


	private static void sendAgeProgressMessage(MinecraftServer server, SiegeBaseState state) {
		int nextRequirement = state.getNextAgeSiegeRequirement();
		String progressText = nextRequirement < 0
			? "You are already at the current maximum age tier."
			: "Next age unlocks at " + nextRequirement + " total siege victories.";
		server.getPlayerManager().broadcast(
			Text.literal("Current age: " + state.getAgeName() + ". " + progressText),
			false
		);
	}

	private static boolean isObjectivePresent(ServerWorld world, BlockPos objectivePos) {
		return world.getBlockState(objectivePos).isIn(BlockTags.BANNERS);
	}

	private static boolean isRallyMarkerPresent(ServerWorld world, BlockPos rallyPos) {
		return world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_BANNER)
			|| world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_WALL_BANNER);
	}

	private static BlockPos findBestBreachTarget(ServerWorld world, Vec3d fromPos, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(fromPos);
		if (approach.lengthSquared() < 0.001D) {
			return null;
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		BlockPos bestTarget = null;
		double bestScore = Double.MAX_VALUE;
		int maxStep = Math.max(2, Math.min((int) Math.ceil(Math.sqrt(approach.lengthSquared())), (int) MAX_BREACH_SCAN_DEPTH));
		double[] infantryLanes = new double[] {0.0D, -1.5D, 1.5D, -3.0D, 3.0D};

		for (double laneOffset : infantryLanes) {
			CorridorBreachAnalysis analysis = analyzeCorridorBreach(world, fromPos, objectivePos, forward, right, laneOffset, maxStep);
			if (analysis == null) {
				continue;
			}

			double routeScore = computeCorridorRouteScore(analysis, objectiveCenter);
			double totalScore = analysis.anchor().getSquaredDistance(objectivePos) * 0.03D
				+ Math.abs(laneOffset) * 10.0D
				+ analysis.firstSliceCost() * 5.0D
				+ analysis.totalCorridorCost() * 6.5D
				+ analysis.blockedSlices() * 320.0D
				+ routeScore;
			BreachRouteScore candidateScore = new BreachRouteScore(
				totalScore,
				analysis.totalCorridorCost(),
				routeScore,
				analysis.breachExit(),
				analysis.postPath() != null && analysis.postPath().reachable(),
				analysis.postPath() != null ? analysis.postPath().explored() : 0,
				analysis.postPath() != null ? analysis.postPath().pathSteps() : 0,
				analysis.postPath() != null ? analysis.postPath().frontierTarget() : null
			);
			logBreachRouteCandidate(world, fromPos, analysis.anchor(), analysis.anchor().getManhattanDistance(BlockPos.ofFloored(fromPos)), laneOffset, candidateScore);
			if (candidateScore.totalScore() < bestScore) {
				bestScore = candidateScore.totalScore();
				bestTarget = analysis.anchor();
			}
		}

		if (DEBUG_LOGGING) {
			AgesOfSiegeMod.LOGGER.info(
				"[SiegeDebug] breach-choice source={} bestTarget={} bestScore={}",
				BlockPos.ofFloored(fromPos),
				bestTarget,
				bestScore
			);
		}

		return bestTarget;
	}

	private static CorridorBreachAnalysis analyzeCorridorBreach(
		ServerWorld world,
		Vec3d fromPos,
		BlockPos objectivePos,
		Vec3d forward,
		Vec3d right,
		double laneOffset,
		int maxStep
	) {
		BlockPos anchor = null;
		double firstSliceCost = 0.0D;
		double totalCorridorCost = 0.0D;
		int blockedSlices = 0;
		Vec3d breachExit = null;

		for (int step = 2; step <= maxStep; step++) {
			Vec3d sample = fromPos.add(forward.multiply(step)).add(right.multiply(laneOffset));
			BlockPos sliceAnchor = findLaneSliceAnchor(world, sample, objectivePos);
			if (sliceAnchor == null) {
				if (anchor != null) {
					breachExit = Vec3d.ofBottomCenter(getInfantryFootPos(world, sample.x, sample.z));
					break;
				}
				continue;
			}

			double sliceCost = estimateInfantryBreachSectionCost(world, sliceAnchor, objectivePos);
			if (sliceCost <= 0.0D) {
				continue;
			}
			if (anchor == null) {
				anchor = sliceAnchor;
				firstSliceCost = sliceCost;
			}
			totalCorridorCost += sliceCost;
			blockedSlices++;
		}

		if (anchor == null) {
			return null;
		}
		if (breachExit == null) {
			breachExit = findInfantryBreachExit(world, anchor, objectivePos, 2);
		}
		InfantryPathResult postPath = breachExit != null
			? findInfantryPath(world, breachExit, Vec3d.ofCenter(objectivePos), ASSAULT_LANE_CHECK_DISTANCE, 2, 0.0D)
			: null;
		return new CorridorBreachAnalysis(anchor, firstSliceCost, totalCorridorCost, blockedSlices, breachExit, postPath);
	}

	private static BlockPos findLaneSliceAnchor(ServerWorld world, Vec3d sample, BlockPos objectivePos) {
		BlockPos surface = getSurfaceBlock(world, sample.x, sample.z);
		BlockPos groundedFoot = getInfantryFootPos(world, sample.x, sample.z);
		for (BlockPos candidate : new BlockPos[] {surface, surface.up(), surface.down()}) {
			if (WallTier.from(world.getBlockState(candidate)) == WallTier.NONE) {
				continue;
			}
			BlockPos wallBase = getWallBase(candidate, world);
			BlockPos base = normalizeGroundedBreachAnchor(
				world,
				new BlockPos(wallBase.getX(), groundedFoot.getY(), wallBase.getZ()),
				objectivePos
			);
			if (estimateInfantryBreachSectionCost(world, base, objectivePos) > 0.0D) {
				return base;
			}
		}
		return null;
	}

	private static double computeCorridorRouteScore(CorridorBreachAnalysis analysis, Vec3d objectiveCenter) {
		if (analysis.breachExit() == null) {
			return 2400.0D;
		}
		InfantryPathResult postPath = analysis.postPath();
		if (postPath == null) {
			return 1800.0D + squaredHorizontalDistance(analysis.breachExit(), objectiveCenter) * 0.8D;
		}
		if (postPath.reachable()) {
			return postPath.pathSteps() * 20.0D + postPath.explored() * 0.8D;
		}
		if (postPath.frontierTarget() != null) {
			return 1200.0D + frontierScore(postPath.frontierTarget(), objectiveCenter, postPath.pathSteps()) * 1.7D;
		}
		return 1600.0D + squaredHorizontalDistance(analysis.breachExit(), objectiveCenter) * 0.9D;
	}

	private static void logBreachRouteCandidate(ServerWorld world, Vec3d fromPos, BlockPos anchorBase, int step, double laneOffset, BreachRouteScore score) {
		if (!DEBUG_LOGGING) {
			return;
		}
		AgesOfSiegeMod.LOGGER.info(
			"[SiegeDebug] breach-candidate source={} anchor={} step={} lane={} sectionCost={} routeScore={} totalScore={} breachExit={} postReachable={} postExplored={} postSteps={} postFrontier={}",
			BlockPos.ofFloored(fromPos),
			anchorBase,
			step,
			laneOffset,
			String.format("%.2f", score.sectionCost()),
			String.format("%.2f", score.routeScore()),
			String.format("%.2f", score.totalScore()),
			score.breachExit() != null ? BlockPos.ofFloored(score.breachExit()) : null,
			score.postReachable(),
			score.postExplored(),
			score.postSteps(),
			score.postFrontier()
		);
	}

	private static double estimateInfantryBreachSectionCost(ServerWorld world, BlockPos anchorBase, BlockPos objectivePos) {
		List<BlockPos> faceCells = getTwoByTwoBreachFaceCells(world, anchorBase, objectivePos);
		if (faceCells.isEmpty()) {
			return 0.0D;
		}

		double totalHitPoints = 0.0D;
		for (BlockPos cell : faceCells) {
			WallTier tier = WallTier.from(world.getBlockState(cell));
			if (tier != WallTier.NONE) {
				totalHitPoints += tier.getHitPoints();
			}
		}
		return totalHitPoints;
	}

	private static Vec3d findInfantryBreachExit(ServerWorld world, BlockPos anchorBase, BlockPos objectivePos, int clearanceHeight) {
		Vec3d[] axes = getBreachAxes(anchorBase, objectivePos);
		Vec3d forward = axes[0];
		Vec3d right = axes[1];
		for (int depth = 1; depth <= 8; depth++) {
			for (int lateral : new int[] {0, -1, 1}) {
				double sampleX = anchorBase.getX() + 0.5D + forward.x * depth + right.x * lateral;
				double sampleZ = anchorBase.getZ() + 0.5D + forward.z * depth + right.z * lateral;
				BlockPos foot = getInfantryFootPos(world, sampleX, sampleZ);
				if (canOccupyInfantryCell(world, foot, clearanceHeight) && hasUsableInteriorCorridor(world, foot, forward, clearanceHeight)) {
					return Vec3d.ofBottomCenter(foot);
				}
			}
		}
		return null;
	}

	private static boolean hasUsableInteriorCorridor(ServerWorld world, BlockPos start, Vec3d forward, int clearanceHeight) {
		BlockPos current = start;
		for (int depth = 0; depth < 3; depth++) {
			if (!canOccupyInfantryCell(world, current, clearanceHeight)) {
				return false;
			}
			if (depth == 2) {
				return true;
			}

			BlockPos next = getInfantryFootPos(
				world,
				current.getX() + 0.5D + forward.x,
				current.getZ() + 0.5D + forward.z
			);
			if (Math.abs(next.getY() - current.getY()) > 1 || !canTraverseInfantryStep(world, current, next, clearanceHeight)) {
				return false;
			}
			current = next;
		}
		return false;
	}

	private static int measureInteriorCorridorLength(ServerWorld world, BlockPos start, BlockPos objectivePos, int clearanceHeight, int maxDepth) {
		Vec3d[] axes = getBreachAxes(start, objectivePos);
		Vec3d forward = axes[0];
		BlockPos current = start;
		int traversable = 0;
		for (int depth = 0; depth < maxDepth; depth++) {
			if (!canOccupyInfantryCell(world, current, clearanceHeight)) {
				break;
			}
			traversable++;
			BlockPos next = getInfantryFootPos(
				world,
				current.getX() + 0.5D + forward.x,
				current.getZ() + 0.5D + forward.z
			);
			if (Math.abs(next.getY() - current.getY()) > 1 || !canTraverseInfantryStep(world, current, next, clearanceHeight)) {
				break;
			}
			current = next;
		}
		return traversable;
	}

	private static BlockPos getEffectiveRamTarget(ServerWorld world, SiegeRamEntity ram, BlockPos objectivePos) {
		BlockPos impactTarget = findRamImpactTarget(world, ram);
		if (impactTarget != null) {
			ram.setBreachTarget(getWallBase(impactTarget, world));
			return impactTarget;
		}

		BlockPos assigned = ram.getBreachTarget();
		if (isValidRamBreachTarget(world, assigned)) {
			return assigned;
		}

		BlockPos bestTarget = findBestRamBreachTarget(world, ram.getPos(), objectivePos);
		ram.setBreachTarget(bestTarget);
		return bestTarget;
	}

	private static BlockPos findBestRamBreachTarget(ServerWorld world, Vec3d fromPos, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(fromPos);
		if (approach.lengthSquared() < 0.001D) {
			return null;
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		BlockPos bestTarget = null;
		double bestScore = Double.MAX_VALUE;
		int maxStep = Math.max(2, Math.min((int) Math.ceil(Math.sqrt(approach.lengthSquared())), (int) MAX_BREACH_SCAN_DEPTH));

		for (double laneOffset : BREACH_LANES) {
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

	private static int getRamFrontageWidth(ServerWorld world, BlockPos base, Vec3d right) {
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

	private static int getRamWallHeight(ServerWorld world, BlockPos base) {
		int height = 0;
		for (int yOffset = 0; yOffset <= 3; yOffset++) {
			if (WallTier.from(world.getBlockState(base.up(yOffset))) == WallTier.NONE) {
				break;
			}
			height++;
		}
		return height;
	}

	private static boolean isValidRamBreachTarget(ServerWorld world, BlockPos target) {
		return target != null && WallTier.from(world.getBlockState(target)) != WallTier.NONE;
	}

	private static BlockPos getSurfaceBlock(ServerWorld world, double x, double z) {
		return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).down();
	}

	private static BlockPos findRamImpactTarget(ServerWorld world, SiegeRamEntity ram) {
		Vec3d forward = Vec3d.fromPolar(0.0F, ram.getYaw()).multiply(1.8D);
		Vec3d probe = ram.getPos().add(forward);
		BlockPos origin = BlockPos.ofFloored(probe.x, probe.y, probe.z);
		for (int yOffset = 0; yOffset <= 2; yOffset++) {
			for (int xOffset = -1; xOffset <= 1; xOffset++) {
				for (int zOffset = -1; zOffset <= 1; zOffset++) {
					BlockPos candidate = origin.add(xOffset, yOffset, zOffset);
					if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
						return candidate.toImmutable();
					}
				}
			}
		}
		return null;
	}

	private static BlockPos getWallBase(BlockPos impactTarget, ServerWorld world) {
		BlockPos cursor = impactTarget;
		while (cursor.getY() > world.getBottomY() && WallTier.from(world.getBlockState(cursor.down())) != WallTier.NONE) {
			cursor = cursor.down();
		}
		return cursor.toImmutable();
	}

	private static boolean tryAttackWallTarget(HostileEntity hostile, ServerWorld world, BlockPos target, Vec3d approachPos, int damage, int attackIntervalTicks) {
		if (WallTier.from(world.getBlockState(target)) == WallTier.NONE) {
			return false;
		}

		Vec3d targetCenter = Vec3d.ofCenter(target);
		double targetHorizontalDistanceSq = squaredHorizontalDistance(hostile.getPos(), targetCenter);
		double approachHorizontalDistanceSq = squaredHorizontalDistance(hostile.getPos(), approachPos);
		double targetVerticalDistance = Math.abs(targetCenter.y - hostile.getY());
		if (approachHorizontalDistanceSq > 2.75D * 2.75D) {
			moveInfantryToward(hostile, world, approachPos, 1.0D);
			return true;
		}
		if (targetHorizontalDistanceSq > BREACH_CHECK_RANGE * BREACH_CHECK_RANGE || targetVerticalDistance > BREACH_VERTICAL_REACH) {
			moveInfantryToward(hostile, world, approachPos, 1.0D);
			return true;
		}

		hostile.getNavigation().stop();
		faceTowards(hostile, targetCenter);
		hostile.swingHand(hostile.getMainHandStack().isEmpty() ? hostile.getActiveHand() : net.minecraft.util.Hand.MAIN_HAND);
		if (hostile.age % attackIntervalTicks == 0) {
			if (DEBUG_LOGGING) {
				AgesOfSiegeMod.LOGGER.info(
					"[SiegeDebug] wall-hit attacker={} target={} damage={} targetHorizontalDistanceSq={} approachHorizontalDistanceSq={} targetVerticalDistance={}",
					hostile.getType().getUntranslatedName(),
					target,
					damage,
					targetHorizontalDistanceSq,
					approachHorizontalDistanceSq,
					targetVerticalDistance
				);
			}
			SiegeBaseState.get(world.getServer()).damageWall(world, target, damage);
		}
		return true;
	}

	private static double squaredHorizontalDistance(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static boolean tryFallbackWallAssault(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, int damage, int attackIntervalTicks) {
		BlockPos breachTarget = findBestBreachTarget(world, hostile.getPos(), objectivePos);
		return breachTarget != null
			&& tryAttackWallTarget(hostile, world, breachTarget, getBreachApproachPosition(world, hostile, breachTarget), damage, attackIntervalTicks);
	}

	private static void advanceOnObjective(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, int objectiveDamage) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		if (hostile.squaredDistanceTo(objectiveCenter) > OBJECTIVE_ATTACK_RANGE * OBJECTIVE_ATTACK_RANGE) {
			moveInfantryToward(hostile, world, objectiveCenter, 1.0D);
			return;
		}

		hostile.getNavigation().stop();
		hostile.swingHand(hostile.getActiveHand());
		if (hostile.age % 20 == 0) {
			SiegeBaseState.get(world.getServer()).damageObjective(world, objectiveDamage);
		}
	}

	private static void moveInfantryToward(HostileEntity hostile, ServerWorld world, Vec3d target, double speed) {
		Vec3d from = hostile.getPos();
		Vec3d delta = new Vec3d(target.x - from.x, 0.0D, target.z - from.z);
		if (delta.lengthSquared() < 0.0001D) {
			hostile.getNavigation().stop();
			return;
		}

		double distanceSq = delta.lengthSquared();
		if (distanceSq > 6.0D * 6.0D) {
			double groundedY = getGroundY(world, target.x, target.z);
			hostile.getNavigation().startMovingTo(target.x, groundedY, target.z, speed);
			return;
		}

		Vec3d forward = delta.normalize();
		double stepLength = Math.min(INFANTRY_APPROACH_STEP, Math.sqrt(delta.lengthSquared()));
		Vec3d[] candidates = new Vec3d[] {
			from.add(forward.multiply(stepLength)),
			from.add(forward.multiply(stepLength)).add(new Vec3d(-forward.z, 0.0D, forward.x).multiply(BREACH_SIDESTEP_DISTANCE)),
			from.add(forward.multiply(stepLength)).add(new Vec3d(forward.z, 0.0D, -forward.x).multiply(BREACH_SIDESTEP_DISTANCE)),
			from.add(new Vec3d(-forward.z, 0.0D, forward.x).multiply(BREACH_SIDESTEP_DISTANCE)),
			from.add(new Vec3d(forward.z, 0.0D, -forward.x).multiply(BREACH_SIDESTEP_DISTANCE))
		};

		for (Vec3d candidate : candidates) {
			if (tryMoveInfantryToward(hostile, world, candidate, speed)) {
				return;
			}
		}

		hostile.getNavigation().stop();
		faceTowards(hostile, target);
	}

	private static boolean tryEscapeHole(HostileEntity hostile, ServerWorld world) {
		double localGroundY = getGroundY(world, hostile.getX(), hostile.getZ());
		if (localGroundY - hostile.getY() < INFANTRY_HOLE_ESCAPE_DEPTH) {
			return false;
		}

		Vec3d escape = findHoleEscapePosition(world, hostile.getPos(), localGroundY);
		if (escape == null) {
			return false;
		}

		moveInfantryToward(hostile, world, escape, 1.1D);
		if (DEBUG_LOGGING && world.getTime() % 40L == 0L) {
			AgesOfSiegeMod.LOGGER.info(
				"[SiegeDebug] hole-escape attacker={} pos={} groundY={} escape={}",
				hostile.getType().getUntranslatedName(),
				hostile.getBlockPos(),
				localGroundY,
				BlockPos.ofFloored(escape)
			);
		}
		return true;
	}

	private static Vec3d findHoleEscapePosition(ServerWorld world, Vec3d from, double localGroundY) {
		Vec3d best = null;
		double bestScore = Double.MAX_VALUE;
		for (int radius = 1; radius <= 3; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}
					double sampleX = from.x + dx;
					double sampleZ = from.z + dz;
					double sampleGroundY = getGroundY(world, sampleX, sampleZ);
					if (sampleGroundY < localGroundY - 0.1D) {
						continue;
					}
					Vec3d candidate = new Vec3d(sampleX, sampleGroundY, sampleZ);
					if (!tryCanMoveInfantryToward(world, from, candidate)) {
						continue;
					}
					double score = from.squaredDistanceTo(candidate);
					if (best == null || score < bestScore) {
						best = candidate;
						bestScore = score;
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	private static boolean tryCanMoveInfantryToward(ServerWorld world, Vec3d from, Vec3d candidate) {
		double currentY = getGroundY(world, from.x, from.z);
		double nextY = getGroundY(world, candidate.x, candidate.z);
		return !(nextY > currentY + INFANTRY_MAX_STEP_UP || isBlockedByWallTop(world, candidate.x, nextY, candidate.z));
	}

	private static Vec3d getInfantryApproachPosition(ServerWorld world, Vec3d from, Vec3d target) {
		Vec3d delta = new Vec3d(target.x - from.x, 0.0D, target.z - from.z);
		if (delta.lengthSquared() < 0.0001D) {
			return target;
		}

		Vec3d approach = target.subtract(delta.normalize().multiply(1.6D));
		double groundedY = getGroundY(world, approach.x, approach.z);
		return new Vec3d(approach.x, groundedY, approach.z);
	}

	private static Vec3d getBreachApproachPosition(ServerWorld world, HostileEntity hostile, BlockPos target) {
		Vec3d targetCenter = Vec3d.ofCenter(target);
		Vec3d from = hostile.getPos();
		Vec3d delta = new Vec3d(targetCenter.x - from.x, 0.0D, targetCenter.z - from.z);
		if (delta.lengthSquared() < 0.0001D) {
			return targetCenter;
		}

		Vec3d forward = delta.normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3) - 1;
		Vec3d baseApproach = targetCenter.subtract(forward.multiply(1.8D)).add(right.multiply(slot * BREACH_SLOT_RADIUS));
		double groundedY = getGroundY(world, baseApproach.x, baseApproach.z);
		return new Vec3d(baseApproach.x, groundedY, baseApproach.z);
	}

	private static boolean tryMoveInfantryToward(HostileEntity hostile, ServerWorld world, Vec3d candidate, double speed) {
		Vec3d from = hostile.getPos();
		double currentY = getGroundY(world, from.x, from.z);
		double nextY = getGroundY(world, candidate.x, candidate.z);
		if (nextY > currentY + INFANTRY_MAX_STEP_UP || isBlockedByWallTop(world, candidate.x, nextY, candidate.z)) {
			return false;
		}

		hostile.getNavigation().startMovingTo(candidate.x, nextY, candidate.z, speed);
		return true;
	}

	private static void logAssaultDecision(ServerWorld world, Entity pathLead, Vec3d pathSource, InfantryPathResult pathResult, boolean hasRam, BlockPos primaryBreachTarget, AssaultMode mode) {
		if (!DEBUG_LOGGING || world.getTime() % 20L != 0L) {
			return;
		}
		AgesOfSiegeMod.LOGGER.info(
			"[SiegeDebug] decision lead={} source={} hasRam={} reachable={} explored={} start={} frontier={} breachTarget={} -> {}",
			pathLead.getType().getUntranslatedName(),
			BlockPos.ofFloored(pathSource),
			hasRam,
			pathResult.reachable(),
			pathResult.explored(),
			pathResult.startFoot(),
			pathResult.frontierTarget(),
			primaryBreachTarget,
			mode
		);
	}

	private static boolean hasClearGroundApproach(ServerWorld world, Vec3d from, Vec3d to) {
		return findInfantryPath(world, from, to, PLAYER_CHASE_WALL_CHECK_DEPTH, 2).reachable();
	}

	private static boolean hasClearInfantryAssaultLane(ServerWorld world, Vec3d from, Vec3d to) {
		return findInfantryPath(world, from, to, ASSAULT_LANE_CHECK_DISTANCE, 2).reachable();
	}

	private static InfantryPathResult findInfantryPath(ServerWorld world, Vec3d from, Vec3d to, double maxDistance, int clearanceHeight) {
		return findInfantryPath(world, from, to, maxDistance, clearanceHeight, LANE_CHECK_INITIAL_SKIP);
	}

	private static InfantryPathResult findInfantryPath(ServerWorld world, Vec3d from, Vec3d to, double maxDistance, int clearanceHeight, double initialSkip) {
		Vec3d delta = new Vec3d(to.x - from.x, 0.0D, to.z - from.z);
		if (delta.lengthSquared() < 0.001D) {
			return new InfantryPathResult(true, BlockPos.ofFloored(from), 0, 0, null);
		}

		double distance = Math.min(Math.sqrt(delta.lengthSquared()), maxDistance);
		if (distance < initialSkip) {
			return new InfantryPathResult(true, BlockPos.ofFloored(from), 0, 0, null);
		}

		Vec3d direction = delta.normalize();
		Vec3d bfsStart = from.add(direction.multiply(initialSkip));
		BlockPos start = getInfantryFootPos(world, bfsStart.x, bfsStart.z);
		if (!canOccupyInfantryCell(world, start, clearanceHeight)) {
			return new InfantryPathResult(false, start, 0, 0, findBlockingWallInColumn(world, start, clearanceHeight));
		}

		ArrayDeque<PathNode> queue = new ArrayDeque<>();
		Set<Long> visited = new HashSet<>();
		queue.add(new PathNode(start, 0));
		visited.add(packXZ(start.getX(), start.getZ()));
		int explored = 0;
		BlockPos bestFrontier = null;
		double bestFrontierScore = Double.MAX_VALUE;

		while (!queue.isEmpty() && explored < INFANTRY_PATH_SEARCH_LIMIT) {
			PathNode node = queue.removeFirst();
			BlockPos current = node.pos();
			explored++;
			if (Vec3d.ofBottomCenter(current).squaredDistanceTo(to.x, current.getY(), to.z) <= OBJECTIVE_ATTACK_RANGE * OBJECTIVE_ATTACK_RANGE) {
				return new InfantryPathResult(true, start, explored, node.steps(), bestFrontier);
			}

			for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nextX = current.getX() + offset[0];
				int nextZ = current.getZ() + offset[1];
				long packed = packXZ(nextX, nextZ);
				if (!visited.add(packed)) {
					continue;
				}

				BlockPos next = getInfantryFootPos(world, nextX + 0.5D, nextZ + 0.5D);
				if (Math.abs(next.getY() - current.getY()) > 1) {
					continue;
				}
				if (Vec3d.ofBottomCenter(next).squaredDistanceTo(from.x, next.getY(), from.z) > distance * distance) {
					continue;
				}
				if (!canOccupyInfantryCell(world, next, clearanceHeight)) {
					bestFrontier = pickBetterFrontier(world, to, bestFrontier, bestFrontierScore, findBlockingWallInColumn(world, next, clearanceHeight), node.steps() + 1);
					if (bestFrontier != null) {
						bestFrontierScore = frontierScore(bestFrontier, to, node.steps() + 1);
					}
					continue;
				}
				if (!canTraverseInfantryStep(world, current, next, clearanceHeight)) {
					bestFrontier = pickBetterFrontier(world, to, bestFrontier, bestFrontierScore, findBlockingWallBetween(world, current, next, clearanceHeight), node.steps() + 1);
					if (bestFrontier != null) {
						bestFrontierScore = frontierScore(bestFrontier, to, node.steps() + 1);
					}
					continue;
				}
				queue.addLast(new PathNode(next, node.steps() + 1));
			}
		}

		return new InfantryPathResult(false, start, explored, explored, bestFrontier);
	}

	private static BlockPos pickBetterFrontier(ServerWorld world, Vec3d objective, BlockPos currentBest, double currentBestScore, BlockPos candidate, int steps) {
		if (candidate == null) {
			return currentBest;
		}
		if (currentBest == null) {
			return candidate;
		}
		double candidateScore = frontierScore(candidate, objective, steps);
		return candidateScore < currentBestScore ? candidate : currentBest;
	}

	private static double frontierScore(BlockPos frontier, Vec3d objective, int steps) {
		Vec3d center = Vec3d.ofCenter(frontier);
		double horizontalDistanceSq = squaredHorizontalDistance(center, objective);
		return steps * 3.0D + horizontalDistanceSq + frontier.getY() * 0.25D;
	}

	private static BlockPos findBlockingWallInColumn(ServerWorld world, BlockPos footPos, int clearanceHeight) {
		for (int yOffset = 0; yOffset < Math.max(3, clearanceHeight + 1); yOffset++) {
			BlockPos candidate = footPos.up(yOffset);
			if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
				return candidate.toImmutable();
			}
		}
		return null;
	}

	private static BlockPos findBlockingWallBetween(ServerWorld world, BlockPos from, BlockPos to, int clearanceHeight) {
		Vec3d fromCenter = Vec3d.ofBottomCenter(from);
		Vec3d toCenter = Vec3d.ofBottomCenter(to);
		BlockPos nearest = null;
		double bestScore = Double.MAX_VALUE;
		for (double t : new double[] {0.25D, 0.5D, 0.75D}) {
			double sampleX = MathHelper.lerp(t, fromCenter.x, toCenter.x);
			double sampleZ = MathHelper.lerp(t, fromCenter.z, toCenter.z);
			BlockPos sample = getInfantryFootPos(world, sampleX, sampleZ);
			BlockPos wall = findBlockingWallInColumn(world, sample, clearanceHeight);
			if (wall != null) {
				double score = squaredHorizontalDistance(Vec3d.ofBottomCenter(from), Vec3d.ofCenter(wall));
				if (nearest == null || score < bestScore) {
					nearest = wall;
					bestScore = score;
				}
			}
		}
		return nearest;
	}

	private static BlockPos getInfantryFootPos(ServerWorld world, double x, double z) {
		double footY = getGroundY(world, x, z);
		return BlockPos.ofFloored(x, footY, z);
	}

	private static boolean canOccupyInfantryCell(ServerWorld world, BlockPos footPos, int clearanceHeight) {
		BlockPos support = footPos.down();
		if (world.getBlockState(support).isAir() || WallTier.from(world.getBlockState(support)) != WallTier.NONE) {
			return false;
		}
		for (int yOffset = 0; yOffset < clearanceHeight; yOffset++) {
			if (isMovementObstacle(world, footPos.up(yOffset))) {
				return false;
			}
		}
		return true;
	}

	private static boolean canTraverseInfantryStep(ServerWorld world, BlockPos from, BlockPos to, int clearanceHeight) {
		Vec3d fromCenter = Vec3d.ofBottomCenter(from);
		Vec3d toCenter = Vec3d.ofBottomCenter(to);
		for (double t : new double[] {0.25D, 0.5D, 0.75D}) {
			double sampleX = MathHelper.lerp(t, fromCenter.x, toCenter.x);
			double sampleZ = MathHelper.lerp(t, fromCenter.z, toCenter.z);
			BlockPos sample = getInfantryFootPos(world, sampleX, sampleZ);
			if (Math.abs(sample.getY() - from.getY()) > 1 && Math.abs(sample.getY() - to.getY()) > 1) {
				return false;
			}
			if (!canOccupyInfantryCell(world, sample, clearanceHeight)) {
				return false;
			}
		}
		return true;
	}

	private static long packXZ(int x, int z) {
		return (((long) x) << 32) ^ (z & 0xffffffffL);
	}

	private static boolean isMovementObstacle(ServerWorld world, BlockPos pos) {
		var state = world.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		return state.blocksMovement() || WallTier.from(state) != WallTier.NONE;
	}

	private static SiegeBaseState stateFrom(ServerWorld world) {
		return SiegeBaseState.get(world.getServer());
	}

	private static BlockPos getLaneBase(ServerWorld world, BlockPos surface) {
		BlockPos cursor = surface;
		while (cursor.getY() > world.getBottomY() && world.getBlockState(cursor).isAir() && world.getBlockState(cursor.down()).isAir()) {
			cursor = cursor.down();
		}

		if (!world.getBlockState(cursor).isAir()) {
			return cursor.up().toImmutable();
		}
		return cursor.toImmutable();
	}
}
