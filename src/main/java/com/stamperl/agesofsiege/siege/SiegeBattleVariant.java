package com.stamperl.agesofsiege.siege;

import java.util.Objects;

public record SiegeBattleVariant(
	String id,
	String displayName,
	String description,
	int waveDelta,
	int ramDelta,
	int difficultyDelta,
	String enemySummary,
	String weaponSummary,
	String threatSummary
) {
	public SiegeBattleVariant {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(description, "description");
		id = id.trim();
		displayName = displayName.trim();
		description = description.trim();
		enemySummary = enemySummary == null ? "" : enemySummary.trim();
		weaponSummary = weaponSummary == null ? "" : weaponSummary.trim();
		threatSummary = threatSummary == null ? "" : threatSummary.trim();
	}
}
