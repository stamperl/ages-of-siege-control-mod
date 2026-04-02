package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record BattleUnitDefinition(
	String id,
	String displayName,
	String entityType,
	String defaultRole,
	String loadoutProfile,
	List<String> tags,
	int spawnTier,
	BreachCapability breachCapability,
	int wallDamage
) {
	public BattleUnitDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(entityType, "entityType");
		id = id.trim();
		displayName = displayName == null ? "" : displayName.trim();
		entityType = entityType.trim();
		defaultRole = defaultRole == null ? "" : defaultRole.trim();
		loadoutProfile = loadoutProfile == null ? "" : loadoutProfile.trim();
		tags = tags == null ? List.of() : tags.stream()
			.filter(tag -> tag != null && !tag.isBlank())
			.map(tag -> tag.trim().toLowerCase(Locale.ROOT))
			.distinct()
			.toList();
		spawnTier = Math.max(0, spawnTier);
		breachCapability = breachCapability == null ? inferBreachCapability(defaultRole, tags) : breachCapability;
		wallDamage = Math.max(0, wallDamage);
	}

	public boolean hasTag(String tag) {
		if (tag == null || tag.isBlank()) {
			return false;
		}
		return tags.contains(tag.trim().toLowerCase(Locale.ROOT));
	}

	public boolean isEngine() {
		return hasTag("engine") || hasTag("siege") || "ram".equalsIgnoreCase(defaultRole);
	}

	public UnitRole defaultUnitRole() {
		String normalized = defaultRole == null ? "" : defaultRole.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "ranged", "archer", "crossbow" -> UnitRole.RANGED;
			case "breacher", "breaker", "sapper" -> UnitRole.BREACHER;
			case "ram", "engine", "siege_engine" -> UnitRole.RAM;
			default -> UnitRole.ESCORT;
		};
	}

	private static BreachCapability inferBreachCapability(String defaultRole, List<String> tags) {
		String normalizedRole = defaultRole == null ? "" : defaultRole.trim().toLowerCase(Locale.ROOT);
		if ("breacher".equals(normalizedRole) || "breaker".equals(normalizedRole) || "sapper".equals(normalizedRole)) {
			return BreachCapability.PRIMARY;
		}
		if (tags != null) {
			for (String tag : tags) {
				if (tag == null) {
					continue;
				}
				String normalizedTag = tag.trim().toLowerCase(Locale.ROOT);
				if (normalizedTag.equals("engine") || normalizedTag.equals("siege")) {
					return BreachCapability.PRIMARY;
				}
			}
		}
		return BreachCapability.FALLBACK;
	}
}
