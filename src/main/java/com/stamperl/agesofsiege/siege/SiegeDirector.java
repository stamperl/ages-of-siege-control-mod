package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.siege.runtime.BattlefieldObservation;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePlanType;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.service.BattlefieldObservationService;
import com.stamperl.agesofsiege.siege.service.ObjectiveService;
import com.stamperl.agesofsiege.siege.service.SiegePlanner;
import com.stamperl.agesofsiege.siege.service.SiegeRewardService;
import com.stamperl.agesofsiege.siege.service.SiegeSpawner;
import com.stamperl.agesofsiege.siege.service.SiegeUnitController;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SiegeDirector {
	private static final long OBSERVATION_INTERVAL = 20L;
	private static final long FALLBACK_REPLAN_INTERVAL = 100L;
	private static final long BREACH_REPLAN_COOLDOWN = 200L;

	private static final ObjectiveService OBJECTIVE_SERVICE = new ObjectiveService();
	private static final BattlefieldObservationService OBSERVATION_SERVICE = new BattlefieldObservationService();
	private static final SiegePlanner PLANNER = new SiegePlanner();
	private static final SiegeSpawner SPAWNER = new SiegeSpawner();
	private static final SiegeRewardService REWARDS = new SiegeRewardService();
	private static final SiegeUnitController UNIT_CONTROLLER = new SiegeUnitController();

	private SiegeDirector() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SiegeDirector::tickServer);
	}

	public static boolean startSiege(MinecraftServer server, SiegeBaseState state) {
		SiegeCatalog.SiegeDefinition definition = SiegeCatalog.byId(state.getSelectedSiegeId());
		if (definition == null || !definition.isUnlocked(state.getCompletedSieges())) {
			definition = SiegeCatalog.highestUnlocked(state.getCompletedSieges());
		}
		if (!lockSiegeFromLedger(server, state, definition)) {
			return false;
		}
		return startLockedSiege(server, state);
	}

	public static boolean lockSiegeFromLedger(MinecraftServer server, SiegeBaseState state, SiegeCatalog.SiegeDefinition definition) {
		if (!state.hasBase() || state.getActiveSession() != null) {
			return false;
		}
		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			return false;
		}
		if (!OBJECTIVE_SERVICE.isObjectivePresent(world, null, state.getBasePos())) {
			return false;
		}
		BlockPos rally = state.getRallyPoint();
		if (rally == null || !isRallyMarkerPresent(world, rally)) {
			return false;
		}
		state.setSelectedSiegeId(definition.id());
		state.prepareStagedSiege(server, definition.id(), definition.ageLevel());
		SPAWNER.spawnWave(server, world, state, state.getActiveSession(), definition);
		return true;
	}

	public static boolean startLockedSiege(MinecraftServer server, SiegeBaseState state) {
		if (state.getActiveSession() == null || state.getActiveSession().getPhase() != SiegePhase.STAGED) {
			return false;
		}
		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			return false;
		}
		BlockPos objectivePos = state.getActiveSession().getObjectivePos() == null ? state.getBasePos() : state.getActiveSession().getObjectivePos();
		if (!OBJECTIVE_SERVICE.isObjectivePresent(world, state.getActiveSession(), objectivePos)) {
			return false;
		}
		SiegeSession refreshed = refreshLivingEntities(world, state, state.getActiveSession());
		if (!hasTrackedUnits(refreshed)) {
			state.endSiege(false, false);
			return false;
		}
		state.activateStagedSiege(server);
		return true;
	}

	public static boolean cancelLockedSiege(MinecraftServer server, SiegeBaseState state) {
		if (state.getActiveSession() == null || state.getActiveSession().getPhase() != SiegePhase.STAGED) {
			return false;
		}
		ServerWorld world = state.getBaseWorld(server);
		if (world != null) {
			SPAWNER.despawnAttackers(world, state.getAttackerIds());
			SPAWNER.despawnRams(world, state.getRamIds());
		}
		state.endSiege(false, false);
		return true;
	}

	private static void tickServer(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		SiegeSession session = state.getActiveSession();
		if (session == null) {
			return;
		}

		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			state.endSiege(true, false);
			return;
		}

		BlockPos objectivePos = session.getObjectivePos() == null ? state.getBasePos() : session.getObjectivePos();
		if (!world.isChunkLoaded(objectivePos)) {
			return;
		}
		if (!OBJECTIVE_SERVICE.isObjectivePresent(world, session, objectivePos)) {
			transitionToDefeat(world, state, "The Settlement Standard was destroyed. The siege is lost.");
			return;
		}

		session = refreshLivingEntities(world, state, session);
		session = refreshObservationIfNeeded(world, state, session, objectivePos);
		session = refreshPlanIfNeeded(world, state, session, objectivePos);
		announceCountdownIfNeeded(server, session, world.getTime());
		session = advancePhase(world, state, session, objectivePos);
		if (session == null) {
			return;
		}

		if (SiegeDebug.isPathRenderEnabled() && world.getTime() % 10L == 0L) {
			SiegeDebug.renderPlan(world, session);
		}

		UNIT_CONTROLLER.dispatch(world, state, session);
		if (session.getPhase() == SiegePhase.STAGED) {
			if (!hasTrackedUnits(session)) {
				state.endSiege(false, false);
			}
			return;
		}
		resolveOutcome(world, server, state, session, objectivePos);
	}

	private static SiegeSession refreshLivingEntities(ServerWorld world, SiegeBaseState state, SiegeSession session) {
		BlockPos trackingAnchor = trackingAnchor(session);
		if (trackingAnchor != null && !world.isChunkLoaded(trackingAnchor)) {
			return session;
		}
		List<UUID> livingAttackers = new ArrayList<>();
		for (UUID attackerId : session.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (entity instanceof HostileEntity hostile && hostile.isAlive()) {
				livingAttackers.add(attackerId);
			}
		}
		List<UUID> livingEngines = new ArrayList<>();
		for (UUID engineId : session.getEngineIds()) {
			Entity entity = world.getEntity(engineId);
			if (entity != null && entity.isAlive()) {
				livingEngines.add(engineId);
			}
		}
		SiegeSession updated = copySession(session, session.getPhase(), session.getPhaseStartedGameTime(), session.getCountdownEndGameTime(), livingAttackers, livingEngines, session.getCurrentPlan(), session.getLastObservation(), session.getLastObservationTick(), session.getLastPlanTick(), session.getFallbackReason());
		state.setActiveSession(updated);
		return updated;
	}

	private static SiegeSession refreshObservationIfNeeded(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos objectivePos) {
		if (session.getLastObservation() != null && world.getTime() - session.getLastObservationTick() < OBSERVATION_INTERVAL) {
			return session;
		}
		BattlefieldObservation observation = OBSERVATION_SERVICE.observe(world, session, objectivePos);
		SiegeSession updated = copySession(session, session.getPhase(), session.getPhaseStartedGameTime(), session.getCountdownEndGameTime(), session.getAttackerIds(), session.getEngineIds(), session.getCurrentPlan(), observation, world.getTime(), session.getLastPlanTick(), session.getFallbackReason());
		state.setActiveSession(updated);
		return updated;
	}

	private static SiegeSession refreshPlanIfNeeded(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos objectivePos) {
		if (session.getPhase() == SiegePhase.STAGED || session.getPhase() == SiegePhase.COUNTDOWN || session.getPhase() == SiegePhase.VICTORY || session.getPhase() == SiegePhase.DEFEAT) {
			return session;
		}
		if (session.getPhase() == SiegePhase.BREACH && session.getCurrentPlan() != null) {
			if (PLANNER.hasActionableTargets(world, session.getCurrentPlan())) {
				return session;
			}
			if (world.getTime() - session.getLastPlanTick() < BREACH_REPLAN_COOLDOWN) {
				return session;
			}
		}

		boolean shouldRefresh = PLANNER.shouldRefreshPlan(world, session);
		if (!shouldRefresh && session.getCurrentPlan() != null && session.getCurrentPlan().planType() == SiegePlanType.FALLBACK_PUSH) {
			shouldRefresh = world.getTime() - session.getLastPlanTick() >= FALLBACK_REPLAN_INTERVAL;
		}
		if (!shouldRefresh) {
			return session;
		}

		BattlefieldObservation observation = session.getLastObservation() == null
			? OBSERVATION_SERVICE.observe(world, session, objectivePos)
			: session.getLastObservation();
		SiegePlan plan = PLANNER.createPlan(world, objectivePos, session.getRallyPos(), observation, !session.getEngineIds().isEmpty());
		String fallbackReason = plan.planType() == SiegePlanType.FALLBACK_PUSH ? "fallback_push" : null;
		SiegeSession updated = copySession(session, session.getPhase(), session.getPhaseStartedGameTime(), session.getCountdownEndGameTime(), session.getAttackerIds(), session.getEngineIds(), plan, observation, session.getLastObservationTick(), world.getTime(), fallbackReason);
		state.setActiveSession(updated);
		return updated;
	}

	private static SiegeSession advancePhase(ServerWorld world, SiegeBaseState state, SiegeSession session, BlockPos objectivePos) {
		long now = world.getTime();
		SiegePhase phase = session.getPhase();
		SiegePlan plan = session.getCurrentPlan();
		BattlefieldObservation observation = session.getLastObservation();

		if (phase == SiegePhase.COUNTDOWN && now >= session.getCountdownEndGameTime()) {
			return setPhase(state, session, SiegePhase.FORM_UP, now);
		}
		if (phase == SiegePhase.FORM_UP && (enoughNear(session.getAttackerIds(), world, session.getSpawnCenter(), 8.0D, 0.5D) || now - session.getPhaseStartedGameTime() >= 60L)) {
			return setPhase(state, session, SiegePhase.ADVANCE, now);
		}
		if (phase == SiegePhase.ADVANCE && plan != null) {
			if (plan.planType() == SiegePlanType.DIRECT_RUSH) {
				return setPhase(state, session, SiegePhase.RUSH, now);
			}
			if (plan.planType() == SiegePlanType.BREACH_REQUIRED && nearStaging(session, world, plan)) {
				return setPhase(state, session, SiegePhase.BREACH, now);
			}
		}
		if (phase == SiegePhase.BREACH && plan != null) {
			if (PLANNER.isOpeningUsable(world, plan, objectivePos)) {
				return setPhase(state, session, SiegePhase.RUSH, now);
			}
			if (plan.planType() == SiegePlanType.BREACH_REQUIRED
				&& session.getLastObservation() != null
				&& !PLANNER.hasActionableTargets(world, plan)
				&& now - session.getLastPlanTick() >= BREACH_REPLAN_COOLDOWN) {
				SiegePlan refreshed = PLANNER.createPlan(world, objectivePos, session.getRallyPos(), observation, !session.getEngineIds().isEmpty());
				if (PLANNER.isPlanInvalid(world, refreshed)) {
					return session;
				}
				if (refreshed.planType() == SiegePlanType.BREACH_REQUIRED
					&& refreshed.primaryBreachAnchor() != null
					&& plan.primaryBreachAnchor() != null
					&& !refreshed.primaryBreachAnchor().equals(plan.primaryBreachAnchor())) {
					SiegeSession updated = copySession(session, SiegePhase.ADVANCE, now, session.getCountdownEndGameTime(), session.getAttackerIds(), session.getEngineIds(), refreshed, observation, session.getLastObservationTick(), now, null);
					state.setActiveSession(updated);
					return updated;
				}
			}
		}
		return session;
	}

	private static void resolveOutcome(ServerWorld world, MinecraftServer server, SiegeBaseState state, SiegeSession session, BlockPos objectivePos) {
		if (!OBJECTIVE_SERVICE.isObjectivePresent(world, session, objectivePos)) {
			transitionToDefeat(world, state, "The Settlement Standard was destroyed. The siege is lost.");
			return;
		}
		if (session.getAttackerIds().isEmpty() && session.getEngineIds().isEmpty()) {
			int previousAge = state.getAgeLevel();
			int siegeAgeLevel = session.getSessionAgeLevel();
			boolean rewardProgress = siegeAgeLevel >= previousAge;
			REWARDS.dropVictoryRewards(world, session, objectivePos, siegeAgeLevel, state.getSelectedSiegeId());
			state.endSiege(false, rewardProgress);
			server.getPlayerManager().broadcast(Text.literal(rewardProgress
				? "The siege wave has been defeated."
				: "Replay siege defeated. Settlement progress unchanged."), false);
			if (rewardProgress && state.getAgeLevel() > previousAge) {
				server.getPlayerManager().broadcast(Text.literal("Age advanced: " + state.getAgeName() + " unlocked."), false);
			}
		}
	}

	private static boolean hasTrackedUnits(SiegeSession session) {
		return session != null && (!session.getAttackerIds().isEmpty() || !session.getEngineIds().isEmpty());
	}

	private static BlockPos trackingAnchor(SiegeSession session) {
		if (session == null) {
			return null;
		}
		return switch (session.getPhase()) {
			case STAGED, COUNTDOWN, FORM_UP -> session.getSpawnCenter() != null ? session.getSpawnCenter() : session.getRallyPos();
			default -> session.getObjectivePos() != null ? session.getObjectivePos() : session.getSpawnCenter();
		};
	}

	private static void announceCountdownIfNeeded(MinecraftServer server, SiegeSession session, long now) {
		if (session.getPhase() != SiegePhase.COUNTDOWN) {
			return;
		}
		long remainingTicks = Math.max(0L, session.getCountdownEndGameTime() - now);
		if (remainingTicks <= 0 || remainingTicks % 20L != 0L) {
			return;
		}
		int remainingSeconds = (int) Math.ceil(remainingTicks / 20.0D);
		if (remainingSeconds == 10 || remainingSeconds <= 5) {
			server.getPlayerManager().broadcast(
				Text.literal("Siege begins in " + remainingSeconds + " seconds."),
				false
			);
		}
	}

	private static void transitionToDefeat(ServerWorld world, SiegeBaseState state, String message) {
		SPAWNER.despawnAttackers(world, state.getAttackerIds());
		SPAWNER.despawnRams(world, state.getRamIds());
		state.endSiege(true, false);
		world.getServer().getPlayerManager().broadcast(Text.literal(message), false);
	}

	private static SiegeSession setPhase(SiegeBaseState state, SiegeSession session, SiegePhase phase, long now) {
		SiegeSession updated = copySession(session, phase, now, phase == SiegePhase.COUNTDOWN ? session.getCountdownEndGameTime() : session.getCountdownEndGameTime(), session.getAttackerIds(), session.getEngineIds(), session.getCurrentPlan(), session.getLastObservation(), session.getLastObservationTick(), session.getLastPlanTick(), session.getFallbackReason());
		state.setActiveSession(updated);
		return updated;
	}

	private static boolean enoughNear(List<UUID> entityIds, ServerWorld world, BlockPos anchor, double range, double ratio) {
		if (anchor == null || entityIds.isEmpty()) {
			return true;
		}
		int nearby = 0;
		for (UUID entityId : entityIds) {
			Entity entity = world.getEntity(entityId);
			if (entity != null && entity.isAlive() && entity.squaredDistanceTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D) <= range * range) {
				nearby++;
			}
		}
		return nearby >= Math.max(1, (int) Math.ceil(entityIds.size() * ratio));
	}

	private static boolean nearStaging(SiegeSession session, ServerWorld world, SiegePlan plan) {
		if (plan.stagingPoint() == null) {
			return true;
		}
		return enoughNear(session.getAttackerIds(), world, plan.stagingPoint(), 8.0D, 0.35D);
	}

	private static SiegeSession copySession(
		SiegeSession session,
		SiegePhase phase,
		long phaseStarted,
		long countdownEnd,
		List<UUID> attackers,
		List<UUID> engines,
		SiegePlan plan,
		BattlefieldObservation observation,
		long lastObservationTick,
		long lastPlanTick,
		String fallbackReason
	) {
		return new SiegeSession(
			phase,
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			session.getSpawnCenter(),
			session.getStartedGameTime(),
			phaseStarted,
			countdownEnd,
			attackers,
			engines,
			session.getRoleAssignments(),
			plan,
			observation,
			lastObservationTick,
			lastPlanTick,
			fallbackReason
		);
	}

	private static boolean isRallyMarkerPresent(ServerWorld world, BlockPos rallyPos) {
		return world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_BANNER)
			|| world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_WALL_BANNER);
	}
}
