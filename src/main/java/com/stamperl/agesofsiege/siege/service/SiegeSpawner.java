package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.report.SiegeWarReportService;
import com.stamperl.agesofsiege.siege.SiegeBattleCatalog;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.runtime.BattleLane;
import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import com.stamperl.agesofsiege.siege.runtime.SiegeBattlePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SiegeSpawner {
	private final Random siegeRandom = Random.create();

	public void spawnWave(MinecraftServer server, ServerWorld world, SiegeBaseState state, SiegeSession session) {
		SiegeCatalog.SiegeDefinition definition = resolveDefinition(state);
		if (definition == null) {
			AgesOfSiegeMod.LOGGER.warn("No siege definition could be resolved for the current state; ending staged wave.");
			state.endSiege(session != null && session.getPhase() != SiegePhase.STAGED, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}
		spawnWave(server, world, state, session, definition);
	}

	public void spawnWave(MinecraftServer server, ServerWorld world, SiegeBaseState state, SiegeSession session, SiegeCatalog.SiegeDefinition definition) {
		SiegeBattlePlan plan = SiegeBattleCatalog.resolve(state, definition);
		if (plan.isEmpty()) {
			AgesOfSiegeMod.LOGGER.warn("Resolved siege '{}' produced an empty battle plan; ending staged wave.", definition == null ? "<null>" : definition.id());
			state.endSiege(session != null && session.getPhase() != SiegePhase.STAGED, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}
		AgesOfSiegeMod.LOGGER.info(
			"Staging siege '{}' with breach posture '{}' (wave={}, engines={}).",
			plan.profileId(),
			plan.breachSummary(),
			plan.totalCombatants(),
			plan.totalEngines()
		);

		BattleFormationPlanner.FormationAnchor anchor = new BattleFormationPlanner().resolveAnchor(world, state, state.getBasePos());
		List<UUID> spawnedAttackers = new ArrayList<>();
		List<UUID> spawnedRams = new ArrayList<>();
		Map<UUID, UnitRole> roleAssignments = new HashMap<>();
		Map<UUID, String> spawnedAttackerKeys = new HashMap<>();
		spawnUnitGroups(world, plan, anchor, spawnedAttackers, roleAssignments, spawnedAttackerKeys);
		spawnEngineGroups(world, plan, anchor, spawnedRams, roleAssignments, spawnedAttackerKeys);

		if (spawnedAttackers.isEmpty() && spawnedRams.isEmpty()) {
			AgesOfSiegeMod.LOGGER.warn("Resolved siege '{}' had no supported unit or engine groups; ending staged wave.", plan.profileId());
			state.endSiege(session != null && session.getPhase() != SiegePhase.STAGED, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}

		state.stageSiegeWave(
			spawnedAttackers,
			spawnedRams,
			roleAssignments,
			Math.max(1, plan.totalCombatants() + plan.totalEngines()),
			BlockPos.ofFloored(anchor.center())
		);
		recordBattleStats(state, plan, spawnedAttackerKeys);
	}

	public void despawnAttackers(ServerWorld world, List<UUID> attackerIds) {
		for (UUID attackerId : attackerIds) {
			Entity entity = world.getEntity(attackerId);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
	}

	public void despawnRams(ServerWorld world, List<UUID> ramIds) {
		for (UUID ramId : ramIds) {
			Entity entity = world.getEntity(ramId);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
	}

	private SiegeCatalog.SiegeDefinition resolveDefinition(SiegeBaseState state) {
		SiegeCatalog.SiegeDefinition selected = SiegeCatalog.resolveForState(state, state.getSelectedSiegeId());
		if (selected != null && selected.isUnlocked(state)) {
			return selected;
		}
		SiegeCatalog.SiegeDefinition highestUnlocked = SiegeCatalog.highestUnlocked(state);
		return highestUnlocked == null ? null : highestUnlocked;
	}

	private void spawnUnitGroups(
		ServerWorld world,
		SiegeBattlePlan plan,
		BattleFormationPlanner.FormationAnchor anchor,
		List<UUID> spawnedAttackers,
		Map<UUID, UnitRole> roleAssignments,
		Map<UUID, String> spawnedAttackerKeys
	) {
		Map<BattleLane, Integer> laneCounts = laneCounts(plan.unitGroups());
		Map<BattleLane, Integer> laneCursor = new EnumMap<>(BattleLane.class);
		int eligibleScreenersRemaining = countEligibleScreeners(plan);
		int screeningAssignmentsRemaining = targetScreeningCount(eligibleScreenersRemaining, plan.totalCombatants());
		int breacherCount = 0;
		int screeningCount = 0;
		int pressureCount = 0;
		for (SiegeBattlePlan.UnitGroup group : plan.unitGroups()) {
			if (group == null || group.count() <= 0) {
				continue;
			}
			int laneCount = laneCounts.getOrDefault(group.lane(), 1);
			for (int i = 0; i < group.count(); i++) {
				int laneIndex = laneCursor.getOrDefault(group.lane(), 0);
				BlockPos spawnPos = anchor.positionFor(plan.formationId(), group.lane(), laneIndex, laneCount, world);
				laneCursor.put(group.lane(), laneIndex + 1);
				Optional<BattleUnitRegistry.SpawnedUnit> spawned = BattleUnitRegistry.spawn(group, plan.loadoutTier(), world, siegeRandom);
				if (spawned.isEmpty()) {
					continue;
				}

				Entity entity = spawned.get().entity();
				entity.refreshPositionAndAngles(
					spawnPos.getX() + 0.5D,
					spawnPos.getY(),
					spawnPos.getZ() + 0.5D,
					siegeRandom.nextFloat() * 360.0F,
					0.0F
				);
				world.spawnEntity(entity);
				spawnedAttackers.add(entity.getUuid());
				UnitRole baseRole = effectiveRole(spawned.get().role() == null ? group.role() : spawned.get().role(), group.breachCapability());
				UnitRole assignedRole = baseRole;
				if (isEligibleScreener(baseRole, group)) {
					if (shouldAssignScreeningRole(screeningAssignmentsRemaining, eligibleScreenersRemaining)) {
						assignedRole = UnitRole.ESCORT;
						screeningAssignmentsRemaining--;
					}
					eligibleScreenersRemaining--;
				}
				roleAssignments.put(entity.getUuid(), assignedRole);
				spawnedAttackerKeys.put(entity.getUuid(), group.unitKind());
				if (baseRole == UnitRole.BREACHER) {
					breacherCount++;
				} else if (assignedRole == UnitRole.ESCORT) {
					screeningCount++;
				} else {
					pressureCount++;
				}
			}
		}
		AgesOfSiegeMod.LOGGER.info(
			"Wave role split for '{}': breachers={}, screeners={}, pressure={}.",
			plan.profileId(),
			breacherCount,
			screeningCount,
			pressureCount
		);
	}

	private void spawnEngineGroups(
		ServerWorld world,
		SiegeBattlePlan plan,
		BattleFormationPlanner.FormationAnchor anchor,
		List<UUID> spawnedRams,
		Map<UUID, UnitRole> roleAssignments,
		Map<UUID, String> spawnedAttackerKeys
	) {
		Map<BattleLane, Integer> laneCounts = laneCounts(plan.engineGroups());
		Map<BattleLane, Integer> laneCursor = new EnumMap<>(BattleLane.class);
		for (SiegeBattlePlan.EngineGroup group : plan.engineGroups()) {
			if (group == null || group.count() <= 0) {
				continue;
			}
			int laneCount = laneCounts.getOrDefault(group.lane(), 1);
			for (int i = 0; i < group.count(); i++) {
				int laneIndex = laneCursor.getOrDefault(group.lane(), 0);
				BlockPos spawnPos = anchor.enginePosition(plan.formationId(), group.lane(), laneIndex, laneCount, world);
				laneCursor.put(group.lane(), laneIndex + 1);
				Optional<BattleUnitRegistry.SpawnedUnit> spawned = BattleUnitRegistry.spawn(group, plan.loadoutTier(), world, siegeRandom);
				if (spawned.isEmpty()) {
					continue;
				}

				Entity entity = spawned.get().entity();
				entity.refreshPositionAndAngles(
					spawnPos.getX() + 0.5D,
					spawnPos.getY(),
					spawnPos.getZ() + 0.5D,
					siegeRandom.nextFloat() * 360.0F,
					0.0F
				);
				world.spawnEntity(entity);
				spawnedRams.add(entity.getUuid());
				roleAssignments.put(entity.getUuid(), UnitRole.RAM);
				spawnedAttackerKeys.put(entity.getUuid(), group.engineKind());
			}
		}
	}

	private void recordBattleStats(SiegeBaseState state, SiegeBattlePlan plan, Map<UUID, String> spawnedAttackerKeys) {
		Map<String, Integer> attackerCounts = new HashMap<>();
		for (SiegeBattlePlan.UnitGroup unitGroup : plan.unitGroups()) {
			if (unitGroup != null && unitGroup.count() > 0) {
				attackerCounts.merge(unitGroup.unitKind(), unitGroup.count(), Integer::sum);
			}
		}
		for (SiegeBattlePlan.EngineGroup engineGroup : plan.engineGroups()) {
			if (engineGroup != null && engineGroup.count() > 0) {
				attackerCounts.merge(engineGroup.engineKind(), engineGroup.count(), Integer::sum);
			}
		}
		Map<String, Integer> defenderCounts = new HashMap<>();
		Map<UUID, String> defenderKeys = new HashMap<>();
		for (var defender : state.getPlacedDefenders()) {
			String key = SiegeWarReportService.defenderKey(defender);
			defenderCounts.merge(key, 1, Integer::sum);
			defenderKeys.put(defender.entityUuid(), key);
		}
		state.updateBattleStats(stats -> stats
			.withSpawnedAttackers(attackerCounts)
			.withSpawnedAttackerEntities(spawnedAttackerKeys)
			.withSpawnedDefenders(defenderCounts, defenderKeys));
	}

	private static Map<BattleLane, Integer> laneCounts(List<?> groups) {
		Map<BattleLane, Integer> counts = new EnumMap<>(BattleLane.class);
		for (Object group : groups) {
			BattleLane lane = null;
			int count = 0;
			if (group instanceof SiegeBattlePlan.UnitGroup unitGroup) {
				lane = unitGroup.lane();
				count = unitGroup.count();
			} else if (group instanceof SiegeBattlePlan.EngineGroup engineGroup) {
				lane = engineGroup.lane();
				count = engineGroup.count();
			}
			if (lane == null || count <= 0) {
				continue;
			}
			counts.merge(lane, count, Integer::sum);
		}
		return counts;
	}

	private int countEligibleScreeners(SiegeBattlePlan plan) {
		int count = 0;
		for (SiegeBattlePlan.UnitGroup group : plan.unitGroups()) {
			if (group == null || group.count() <= 0) {
				continue;
			}
			if (isEligibleScreener(group.role(), group)) {
				count += group.count();
			}
		}
		return count;
	}

	private int targetScreeningCount(int eligibleScreeners, int totalCombatants) {
		if (eligibleScreeners <= 1 || totalCombatants <= 2) {
			return 0;
		}
		int target = Math.round(eligibleScreeners * 0.6F);
		return Math.max(1, Math.min(eligibleScreeners - 1, target));
	}

	private boolean shouldAssignScreeningRole(int screeningAssignmentsRemaining, int eligibleScreenersRemaining) {
		if (screeningAssignmentsRemaining <= 0 || eligibleScreenersRemaining <= 0) {
			return false;
		}
		if (screeningAssignmentsRemaining >= eligibleScreenersRemaining) {
			return true;
		}
		return siegeRandom.nextInt(eligibleScreenersRemaining) < screeningAssignmentsRemaining;
	}

	private boolean isEligibleScreener(UnitRole role, SiegeBattlePlan.UnitGroup group) {
		if (group == null) {
			return false;
		}
		if (group.breachCapability() == BreachCapability.PRIMARY) {
			return false;
		}
		return role != UnitRole.BREACHER && role != UnitRole.RAM;
	}

	private UnitRole effectiveRole(UnitRole baseRole, BreachCapability breachCapability) {
		if (breachCapability == BreachCapability.PRIMARY) {
			return UnitRole.BREACHER;
		}
		return baseRole == null ? UnitRole.RANGED : baseRole;
	}
}
