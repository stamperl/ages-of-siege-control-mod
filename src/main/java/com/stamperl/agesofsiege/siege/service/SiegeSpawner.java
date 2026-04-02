package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.SiegeBattleCatalog;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.runtime.BattleLane;
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
		spawnUnitGroups(world, plan, anchor, spawnedAttackers, roleAssignments);
		spawnEngineGroups(world, plan, anchor, spawnedRams, roleAssignments);

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
		Map<UUID, UnitRole> roleAssignments
	) {
		Map<BattleLane, Integer> laneCounts = laneCounts(plan.unitGroups());
		Map<BattleLane, Integer> laneCursor = new EnumMap<>(BattleLane.class);
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
				roleAssignments.put(entity.getUuid(), spawned.get().role() == null ? group.role() : spawned.get().role());
			}
		}
	}

	private void spawnEngineGroups(
		ServerWorld world,
		SiegeBattlePlan plan,
		BattleFormationPlanner.FormationAnchor anchor,
		List<UUID> spawnedRams,
		Map<UUID, UnitRole> roleAssignments
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
			}
		}
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
}
