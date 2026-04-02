package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.siege.runtime.BreachCapability;

import java.util.List;
import java.util.Objects;

public record SiegeUnitGroup(
	String id,
	String unitType,
	String entityType,
	String role,
	int count,
	String loadout,
	List<String> tags,
	BreachCapability breachCapability,
	Integer wallDamage
) {
	public SiegeUnitGroup {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(unitType, "unitType");
		id = id.trim();
		unitType = unitType.trim();
		entityType = entityType == null ? "" : entityType.trim();
		role = role == null ? "" : role.trim();
		loadout = loadout == null ? "" : loadout.trim();
		tags = tags == null ? List.of() : List.copyOf(tags);
		breachCapability = breachCapability == null ? BreachCapability.NONE : breachCapability;
		wallDamage = wallDamage == null ? null : Math.max(0, wallDamage);
	}
}
