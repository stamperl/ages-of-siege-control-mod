package com.stamperl.agesofsiege.siege;

import java.util.List;
import java.util.Objects;

public record SiegeBattleTemplate(
	String id,
	int spawnTier,
	int difficultyTier,
	int baseWaveSize,
	int baseRamCount,
	String formationId,
	String enemySummary,
	String weaponSummary,
	String threatSummary,
	List<SiegeUnitGroup> unitGroups,
	List<SiegeEngineGroup> engineGroups,
	List<SiegeBattleVariant> variants
) {
	public SiegeBattleTemplate {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(formationId, "formationId");
		unitGroups = unitGroups == null ? List.of() : List.copyOf(unitGroups);
		engineGroups = engineGroups == null ? List.of() : List.copyOf(engineGroups);
		variants = variants == null ? List.of() : List.copyOf(variants);
		id = id.trim();
		formationId = formationId.trim();
		enemySummary = enemySummary == null ? "" : enemySummary.trim();
		weaponSummary = weaponSummary == null ? "" : weaponSummary.trim();
		threatSummary = threatSummary == null ? "" : threatSummary.trim();
	}
}
