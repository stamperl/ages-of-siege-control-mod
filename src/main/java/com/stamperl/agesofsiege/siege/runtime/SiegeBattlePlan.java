package com.stamperl.agesofsiege.siege.runtime;

import java.util.List;

public record SiegeBattlePlan(
	String profileId,
	String displayName,
	String formationId,
	int loadoutTier,
	int difficultyTier,
	int waveSize,
	int ramCount,
	String enemySummary,
	String weaponSummary,
	String threatSummary,
	boolean hasPrimaryBreacher,
	boolean hasFallbackBreacher,
	int fallbackWallDamageFloor,
	List<UnitGroup> unitGroups,
	List<EngineGroup> engineGroups
) {
	public SiegeBattlePlan {
		profileId = profileId == null ? "" : profileId.trim();
		displayName = displayName == null ? "" : displayName.trim();
		formationId = formationId == null ? "line" : formationId.trim();
		enemySummary = enemySummary == null ? "" : enemySummary.trim();
		weaponSummary = weaponSummary == null ? "" : weaponSummary.trim();
		threatSummary = threatSummary == null ? "" : threatSummary.trim();
		waveSize = Math.max(0, waveSize);
		ramCount = Math.max(0, ramCount);
		fallbackWallDamageFloor = Math.max(0, fallbackWallDamageFloor);
		unitGroups = List.copyOf(unitGroups);
		engineGroups = List.copyOf(engineGroups);
	}

	public int totalCombatants() {
		return unitGroups.stream().mapToInt(UnitGroup::count).sum();
	}

	public int totalEngines() {
		return engineGroups.stream().mapToInt(EngineGroup::count).sum();
	}

	public boolean isEmpty() {
		return totalCombatants() == 0 && totalEngines() == 0;
	}

	public String breachSummary() {
		if (ramCount > 0 && hasPrimaryBreacher && hasFallbackBreacher) {
			return "primary + fallback + ram";
		}
		if (ramCount > 0 && hasPrimaryBreacher) {
			return "primary + ram";
		}
		if (ramCount > 0 && hasFallbackBreacher) {
			return "fallback + ram";
		}
		if (hasPrimaryBreacher && hasFallbackBreacher) {
			return "primary + fallback";
		}
		if (hasPrimaryBreacher) {
			return "primary";
		}
		if (hasFallbackBreacher) {
			return "fallback";
		}
		if (ramCount > 0) {
			return "ram only";
		}
		return "none";
	}

	public record UnitGroup(
		String groupId,
		String unitKind,
		String entityType,
		String loadoutProfile,
		UnitRole role,
		BreachCapability breachCapability,
		int wallDamage,
		int count,
		BattleLane lane,
		List<String> tags
	) {
		public UnitGroup {
			groupId = groupId == null ? "" : groupId.trim();
			unitKind = unitKind == null ? "" : unitKind.trim();
			entityType = entityType == null ? "" : entityType.trim();
			loadoutProfile = loadoutProfile == null ? "" : loadoutProfile.trim();
			breachCapability = breachCapability == null ? BreachCapability.NONE : breachCapability;
			wallDamage = Math.max(0, wallDamage);
			count = Math.max(0, count);
			tags = tags == null ? List.of() : List.copyOf(tags);
		}
	}

	public record EngineGroup(
		String groupId,
		String engineKind,
		String entityType,
		int count,
		BattleLane lane,
		List<String> tags
	) {
		public EngineGroup {
			groupId = groupId == null ? "" : groupId.trim();
			engineKind = engineKind == null ? "" : engineKind.trim();
			entityType = entityType == null ? "" : entityType.trim();
			count = Math.max(0, count);
			tags = tags == null ? List.of() : List.copyOf(tags);
		}
	}
}
