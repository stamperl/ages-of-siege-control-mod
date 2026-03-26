package com.stamperl.agesofsiege.siege.runtime;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SiegeSession {
	private final SiegePhase phase;
	private final int sessionAgeLevel;
	private final int sessionVictoryCount;
	private final BlockPos objectivePos;
	private final BlockPos rallyPos;
	private final BlockPos spawnCenter;
	private final long startedGameTime;
	private final long phaseStartedGameTime;
	private final long countdownEndGameTime;
	private final List<UUID> attackerIds;
	private final List<UUID> engineIds;
	private final Map<UUID, UnitRole> roleAssignments;
	private final SiegePlan currentPlan;
	private final BattlefieldObservation lastObservation;
	private final long lastPlanTick;
	private final String fallbackReason;

	public SiegeSession(
		SiegePhase phase,
		int sessionAgeLevel,
		int sessionVictoryCount,
		BlockPos objectivePos,
		BlockPos rallyPos,
		BlockPos spawnCenter,
		long startedGameTime,
		long phaseStartedGameTime,
		long countdownEndGameTime,
		List<UUID> attackerIds,
		List<UUID> engineIds,
		Map<UUID, UnitRole> roleAssignments,
		SiegePlan currentPlan,
		BattlefieldObservation lastObservation,
		long lastPlanTick,
		String fallbackReason
	) {
		this.phase = phase;
		this.sessionAgeLevel = sessionAgeLevel;
		this.sessionVictoryCount = sessionVictoryCount;
		this.objectivePos = objectivePos == null ? null : objectivePos.toImmutable();
		this.rallyPos = rallyPos == null ? null : rallyPos.toImmutable();
		this.spawnCenter = spawnCenter == null ? null : spawnCenter.toImmutable();
		this.startedGameTime = startedGameTime;
		this.phaseStartedGameTime = phaseStartedGameTime;
		this.countdownEndGameTime = countdownEndGameTime;
		this.attackerIds = List.copyOf(attackerIds);
		this.engineIds = List.copyOf(engineIds);
		this.roleAssignments = Map.copyOf(roleAssignments);
		this.currentPlan = currentPlan;
		this.lastObservation = lastObservation;
		this.lastPlanTick = lastPlanTick;
		this.fallbackReason = fallbackReason;
	}

	public SiegePhase getPhase() {
		return phase;
	}

	public int getSessionAgeLevel() {
		return sessionAgeLevel;
	}

	public int getSessionVictoryCount() {
		return sessionVictoryCount;
	}

	public BlockPos getObjectivePos() {
		return objectivePos;
	}

	public BlockPos getRallyPos() {
		return rallyPos;
	}

	public BlockPos getSpawnCenter() {
		return spawnCenter;
	}

	public long getStartedGameTime() {
		return startedGameTime;
	}

	public long getPhaseStartedGameTime() {
		return phaseStartedGameTime;
	}

	public long getCountdownEndGameTime() {
		return countdownEndGameTime;
	}

	public List<UUID> getAttackerIds() {
		return attackerIds;
	}

	public List<UUID> getEngineIds() {
		return engineIds;
	}

	public Map<UUID, UnitRole> getRoleAssignments() {
		return roleAssignments;
	}

	public SiegePlan getCurrentPlan() {
		return currentPlan;
	}

	public BattlefieldObservation getLastObservation() {
		return lastObservation;
	}

	public long getLastPlanTick() {
		return lastPlanTick;
	}

	public String getFallbackReason() {
		return fallbackReason;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("phase", phase.name());
		nbt.putInt("sessionAgeLevel", sessionAgeLevel);
		nbt.putInt("sessionVictoryCount", sessionVictoryCount);
		writeNullableBlockPos(nbt, "objective", objectivePos);
		writeNullableBlockPos(nbt, "rally", rallyPos);
		writeNullableBlockPos(nbt, "spawnCenter", spawnCenter);
		nbt.putLong("startedGameTime", startedGameTime);
		nbt.putLong("phaseStartedGameTime", phaseStartedGameTime);
		nbt.putLong("countdownEndGameTime", countdownEndGameTime);
		nbt.put("attackerIds", writeUuidList(attackerIds));
		nbt.put("engineIds", writeUuidList(engineIds));
		nbt.put("roleAssignments", writeRoleAssignments(roleAssignments));
		if (currentPlan != null) {
			nbt.put("currentPlan", currentPlan.toNbt());
		}
		if (lastObservation != null) {
			nbt.put("lastObservation", lastObservation.toNbt());
		}
		nbt.putLong("lastPlanTick", lastPlanTick);
		if (fallbackReason != null) {
			nbt.putString("fallbackReason", fallbackReason);
		}
		return nbt;
	}

	public static SiegeSession fromNbt(NbtCompound nbt) {
		return new SiegeSession(
			SiegePhase.valueOf(nbt.getString("phase")),
			nbt.getInt("sessionAgeLevel"),
			nbt.getInt("sessionVictoryCount"),
			readNullableBlockPos(nbt, "objective"),
			readNullableBlockPos(nbt, "rally"),
			readNullableBlockPos(nbt, "spawnCenter"),
			nbt.getLong("startedGameTime"),
			nbt.getLong("phaseStartedGameTime"),
			nbt.getLong("countdownEndGameTime"),
			readUuidList(nbt.getList("attackerIds", NbtElement.STRING_TYPE)),
			readUuidList(nbt.getList("engineIds", NbtElement.STRING_TYPE)),
			readRoleAssignments(nbt.getList("roleAssignments", NbtElement.COMPOUND_TYPE)),
			nbt.contains("currentPlan", NbtElement.COMPOUND_TYPE) ? SiegePlan.fromNbt(nbt.getCompound("currentPlan")) : null,
			nbt.contains("lastObservation", NbtElement.COMPOUND_TYPE)
				? BattlefieldObservation.fromNbt(nbt.getCompound("lastObservation"))
				: null,
			nbt.getLong("lastPlanTick"),
			nbt.contains("fallbackReason", NbtElement.STRING_TYPE) ? nbt.getString("fallbackReason") : null
		);
	}

	private static void writeNullableBlockPos(NbtCompound nbt, String key, BlockPos pos) {
		if (pos == null) {
			return;
		}
		nbt.putInt(key + "X", pos.getX());
		nbt.putInt(key + "Y", pos.getY());
		nbt.putInt(key + "Z", pos.getZ());
	}

	private static BlockPos readNullableBlockPos(NbtCompound nbt, String key) {
		if (!nbt.contains(key + "X")) {
			return null;
		}
		return new BlockPos(nbt.getInt(key + "X"), nbt.getInt(key + "Y"), nbt.getInt(key + "Z"));
	}

	private static NbtList writeUuidList(List<UUID> ids) {
		NbtList list = new NbtList();
		for (UUID id : ids) {
			list.add(NbtString.of(id.toString()));
		}
		return list;
	}

	private static List<UUID> readUuidList(NbtList list) {
		List<UUID> ids = new ArrayList<>();
		for (NbtElement element : list) {
			ids.add(UUID.fromString(element.asString()));
		}
		return List.copyOf(ids);
	}

	private static NbtList writeRoleAssignments(Map<UUID, UnitRole> roleAssignments) {
		NbtList list = new NbtList();
		for (Map.Entry<UUID, UnitRole> entry : roleAssignments.entrySet()) {
			NbtCompound assignment = new NbtCompound();
			assignment.putUuid("unitId", entry.getKey());
			assignment.putString("role", entry.getValue().name());
			list.add(assignment);
		}
		return list;
	}

	private static Map<UUID, UnitRole> readRoleAssignments(NbtList list) {
		Map<UUID, UnitRole> assignments = new HashMap<>();
		for (NbtElement element : list) {
			NbtCompound assignment = (NbtCompound) element;
			assignments.put(assignment.getUuid("unitId"), UnitRole.valueOf(assignment.getString("role")));
		}
		return Map.copyOf(assignments);
	}
}
