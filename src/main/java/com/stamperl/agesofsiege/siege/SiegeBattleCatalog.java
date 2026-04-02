package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import com.stamperl.agesofsiege.siege.runtime.BattleLane;
import com.stamperl.agesofsiege.siege.runtime.SiegeBattlePlan;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import com.stamperl.agesofsiege.state.SiegeBaseState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SiegeBattleCatalog {
	private SiegeBattleCatalog() {
	}

	public static SiegeBattlePlan resolve(SiegeBaseState state, SiegeCatalog.SiegeDefinition definition) {
		if (definition == null) {
			return emptyPlan();
		}

		SiegeCampaignNode campaignNode = SiegeCatalog.campaignNode(definition.id());
		SiegeBattleTemplate template = campaignNode == null ? null : SiegeCatalog.battleTemplate(campaignNode.battleTemplateId());
		if (template == null) {
			return legacyFallback(definition);
		}

		List<SiegeBattlePlan.UnitGroup> unitGroups = buildUnitGroups(definition, template);
		List<SiegeBattlePlan.EngineGroup> engineGroups = buildEngineGroups(definition, template);
		int waveSize = unitGroups.stream().mapToInt(SiegeBattlePlan.UnitGroup::count).sum();
		int ramCount = engineGroups.stream()
			.filter(group -> "ram".equalsIgnoreCase(group.engineKind()))
			.mapToInt(SiegeBattlePlan.EngineGroup::count)
			.sum();

		boolean hasPrimaryBreacher = unitGroups.stream().anyMatch(group -> group.breachCapability() == BreachCapability.PRIMARY);
		boolean hasFallbackBreacher = unitGroups.stream().anyMatch(group -> group.breachCapability() == BreachCapability.FALLBACK);
		int fallbackWallDamageFloor = unitGroups.stream()
			.filter(group -> group.breachCapability() == BreachCapability.FALLBACK)
			.mapToInt(SiegeBattlePlan.UnitGroup::wallDamage)
			.filter(value -> value > 0)
			.min()
			.orElse(0);

		return new SiegeBattlePlan(
			template.id(),
			definition.displayName(),
			blankToDefault(template.formationId(), "line"),
			clampTier(template.spawnTier()),
			Math.max(0, definition.combatTier()),
			waveSize > 0 ? waveSize : Math.max(0, definition.waveSize()),
			ramCount > 0 ? ramCount : Math.max(0, definition.ramCount()),
			definition.enemySummary(),
			definition.weaponSummary(),
			definition.threatSummary(),
			hasPrimaryBreacher,
			hasFallbackBreacher,
			fallbackWallDamageFloor,
			unitGroups,
			engineGroups
		);
	}

	private static SiegeBattlePlan legacyFallback(SiegeCatalog.SiegeDefinition definition) {
		List<SiegeBattlePlan.UnitGroup> unitGroups = new ArrayList<>();
		if (definition.waveSize() > 0) {
			unitGroups.add(new SiegeBattlePlan.UnitGroup(
				definition.id() + "_fallback_front",
				"adaptive_front",
				"minecraft:pillager",
				"field_line",
				UnitRole.RANGED,
				BreachCapability.FALLBACK,
				1,
				Math.max(1, definition.waveSize()),
				BattleLane.FRONT,
				List.of("fallback", "front")
			));
		}
		List<SiegeBattlePlan.EngineGroup> engineGroups = new ArrayList<>();
		if (definition.ramCount() > 0) {
			engineGroups.add(new SiegeBattlePlan.EngineGroup(
				definition.id() + "_fallback_ram",
				"ram",
				"ages_of_siege:ram",
				definition.ramCount(),
				BattleLane.ENGINE,
				List.of("fallback", "engine")
			));
		}
		return new SiegeBattlePlan(
			definition.id(),
			definition.displayName(),
			"line",
			clampTier(definition.ageLevel()),
			Math.max(0, definition.combatTier()),
			Math.max(0, definition.waveSize()),
			Math.max(0, definition.ramCount()),
			definition.enemySummary(),
			definition.weaponSummary(),
			definition.threatSummary(),
			false,
			!unitGroups.isEmpty(),
			unitGroups.isEmpty() ? 0 : 1,
			unitGroups,
			engineGroups
		);
	}

	private static List<SiegeBattlePlan.UnitGroup> buildUnitGroups(SiegeCatalog.SiegeDefinition definition, SiegeBattleTemplate template) {
		List<SiegeUnitGroup> sourceGroups = template.unitGroups() == null ? List.of() : template.unitGroups();
		if (sourceGroups.isEmpty()) {
			return List.of();
		}

		List<MutableUnitGroup> working = new ArrayList<>();
		for (SiegeUnitGroup group : sourceGroups) {
			if (group == null || group.count() <= 0) {
				continue;
			}
			BattleUnitDefinition unitDefinition = BattleUnitCatalog.definition(group.unitType());
			if (unitDefinition == null && group.entityType().isBlank()) {
				AgesOfSiegeMod.LOGGER.warn("Battle template '{}' references unknown unit type '{}' with no entity override; skipping group '{}'.", template.id(), group.unitType(), group.id());
				continue;
			}
			String entityType = firstNonBlank(group.entityType(), unitDefinition == null ? "" : unitDefinition.entityType());
			String roleId = firstNonBlank(group.role(), unitDefinition == null ? "" : unitDefinition.defaultRole());
			String loadoutProfile = firstNonBlank(group.loadout(), unitDefinition == null ? "" : unitDefinition.loadoutProfile());
			List<String> tags = mergeTags(unitDefinition == null ? List.of() : unitDefinition.tags(), group.tags());
			BreachCapability breachCapability = group.breachCapability() == BreachCapability.NONE
				? unitDefinition == null ? BreachCapability.NONE : unitDefinition.breachCapability()
				: group.breachCapability();
			Integer groupWallDamage = group.wallDamage();
			int wallDamage = groupWallDamage != null
				? Math.max(0, groupWallDamage)
				: unitDefinition == null ? 0 : unitDefinition.wallDamage();
			working.add(new MutableUnitGroup(
				group.id(),
				blankToDefault(group.unitType(), "unit"),
				entityType,
				loadoutProfile,
				parseRole(roleId),
				breachCapability,
				wallDamage,
				group.count(),
				resolveLane(roleId, tags, false),
				tags
			));
		}

		int targetCount = Math.max(0, definition.waveSize());
		redistributeUnitCounts(working, targetCount);
		return working.stream()
			.filter(group -> group.count > 0)
			.map(group -> new SiegeBattlePlan.UnitGroup(
				group.id,
				group.unitType,
				group.entityType,
				group.loadoutProfile,
				group.role,
				group.breachCapability,
				group.wallDamage,
				group.count,
				group.lane,
				group.tags
			))
			.toList();
	}

	private static List<SiegeBattlePlan.EngineGroup> buildEngineGroups(SiegeCatalog.SiegeDefinition definition, SiegeBattleTemplate template) {
		List<SiegeEngineGroup> sourceGroups = template.engineGroups() == null ? List.of() : template.engineGroups();
		List<MutableEngineGroup> working = new ArrayList<>();
		for (SiegeEngineGroup group : sourceGroups) {
			if (group == null || group.count() <= 0) {
				continue;
			}
			BattleUnitDefinition unitDefinition = BattleUnitCatalog.definition(group.engineType());
			if (unitDefinition == null && group.entityType().isBlank()) {
				AgesOfSiegeMod.LOGGER.warn("Battle template '{}' references unknown engine type '{}' with no entity override; skipping group '{}'.", template.id(), group.engineType(), group.id());
				continue;
			}
			List<String> tags = mergeTags(unitDefinition == null ? List.of() : unitDefinition.tags(), group.tags());
			working.add(new MutableEngineGroup(
				group.id(),
				blankToDefault(group.engineType(), "engine"),
				firstNonBlank(group.entityType(), unitDefinition == null ? "" : unitDefinition.entityType()),
				group.count(),
				resolveLane(group.engineType(), tags, true),
				tags
			));
		}

		int targetRamCount = Math.max(0, definition.ramCount());
		int currentRamCount = working.stream()
			.filter(group -> "ram".equalsIgnoreCase(group.engineType))
			.mapToInt(group -> group.count)
			.sum();
		if (targetRamCount > currentRamCount) {
			int delta = targetRamCount - currentRamCount;
			MutableEngineGroup ramGroup = working.stream()
				.filter(group -> "ram".equalsIgnoreCase(group.engineType))
				.findFirst()
				.orElse(null);
			if (ramGroup == null) {
				BattleUnitDefinition ramDefinition = BattleUnitCatalog.definition("ram");
				working.add(new MutableEngineGroup(
					definition.id() + "_generated_ram",
					"ram",
					ramDefinition == null ? "ages_of_siege:ram" : ramDefinition.entityType(),
					delta,
					BattleLane.ENGINE,
					mergeTags(ramDefinition == null ? List.of("generated", "ram") : ramDefinition.tags(), List.of("generated"))
				));
			} else {
				ramGroup.count += delta;
			}
		} else if (targetRamCount < currentRamCount) {
			int delta = currentRamCount - targetRamCount;
			List<MutableEngineGroup> ramGroups = working.stream()
				.filter(group -> "ram".equalsIgnoreCase(group.engineType))
				.sorted(Comparator.comparingInt((MutableEngineGroup group) -> group.count).reversed())
				.toList();
			for (MutableEngineGroup group : ramGroups) {
				if (delta <= 0) {
					break;
				}
				int remove = Math.min(delta, group.count);
				group.count -= remove;
				delta -= remove;
			}
		}

		return working.stream()
			.filter(group -> group.count > 0)
			.map(group -> new SiegeBattlePlan.EngineGroup(
				group.id,
				group.engineType,
				group.entityType,
				group.count,
				group.lane,
				group.tags
			))
			.toList();
	}

	private static void redistributeUnitCounts(List<MutableUnitGroup> groups, int targetTotal) {
		if (groups.isEmpty()) {
			return;
		}
		int currentTotal = groups.stream().mapToInt(group -> group.count).sum();
		if (targetTotal == currentTotal) {
			return;
		}

		if (targetTotal <= 0) {
			groups.forEach(group -> group.count = 0);
			return;
		}

		int delta = targetTotal - currentTotal;
		if (delta > 0) {
			int index = 0;
			while (delta > 0) {
				groups.get(index % groups.size()).count++;
				delta--;
				index++;
			}
			return;
		}

		int toRemove = -delta;
		while (toRemove > 0) {
			MutableUnitGroup largest = groups.stream()
				.filter(group -> group.count > 0)
				.max(Comparator.comparingInt(group -> group.count))
				.orElse(null);
			if (largest == null) {
				break;
			}
			largest.count--;
			toRemove--;
		}
	}

	private static UnitRole parseRole(String roleId) {
		if (roleId == null) {
			return UnitRole.ESCORT;
		}
		return switch (roleId.trim().toLowerCase(Locale.ROOT)) {
			case "ranged", "archer", "crossbow" -> UnitRole.RANGED;
			case "breacher", "breaker", "sapper" -> UnitRole.BREACHER;
			case "ram", "engine", "siege_engine" -> UnitRole.RAM;
			default -> UnitRole.ESCORT;
		};
	}

	private static BattleLane resolveLane(String roleOrType, List<String> tags, boolean engine) {
		if (engine) {
			return BattleLane.ENGINE;
		}
		List<String> normalizedTags = tags == null ? List.of() : tags.stream()
			.filter(tag -> tag != null && !tag.isBlank())
			.map(tag -> tag.trim().toLowerCase(Locale.ROOT))
			.toList();
		if (normalizedTags.contains("left_flank")) {
			return BattleLane.LEFT_FLANK;
		}
		if (normalizedTags.contains("right_flank")) {
			return BattleLane.RIGHT_FLANK;
		}
		if (normalizedTags.contains("rear")) {
			return BattleLane.REAR;
		}
		if (normalizedTags.contains("front") || normalizedTags.contains("breach") || normalizedTags.contains("frontline")) {
			return BattleLane.FRONT;
		}
		if (normalizedTags.contains("center") || normalizedTags.contains("escort")) {
			return BattleLane.CENTER;
		}

		String normalizedRole = roleOrType == null ? "" : roleOrType.trim().toLowerCase(Locale.ROOT);
		return switch (normalizedRole) {
			case "breacher", "breaker", "sapper" -> BattleLane.FRONT;
			case "ranged", "archer", "crossbow" -> BattleLane.REAR;
			default -> BattleLane.CENTER;
		};
	}

	private static List<String> mergeTags(List<String> baseTags, List<String> overrideTags) {
		LinkedHashSet<String> merged = new LinkedHashSet<>();
		if (baseTags != null) {
			baseTags.stream()
				.filter(tag -> tag != null && !tag.isBlank())
				.map(tag -> tag.trim().toLowerCase(Locale.ROOT))
				.forEach(merged::add);
		}
		if (overrideTags != null) {
			overrideTags.stream()
				.filter(tag -> tag != null && !tag.isBlank())
				.map(tag -> tag.trim().toLowerCase(Locale.ROOT))
				.forEach(merged::add);
		}
		return List.copyOf(merged);
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String firstNonBlank(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary.trim();
		}
		return fallback == null ? "" : fallback.trim();
	}

	private static int clampTier(int value) {
		return Math.max(0, Math.min(value, 3));
	}

	private static SiegeBattlePlan emptyPlan() {
		return new SiegeBattlePlan("empty", "Empty", "line", 0, 0, 0, 0, "", "", "", false, false, 0, List.of(), List.of());
	}

	private static final class MutableUnitGroup {
		private final String id;
		private final String unitType;
		private final String entityType;
		private final String loadoutProfile;
		private final UnitRole role;
		private final BreachCapability breachCapability;
		private final int wallDamage;
		private final BattleLane lane;
		private final List<String> tags;
		private int count;

		private MutableUnitGroup(
			String id,
			String unitType,
			String entityType,
			String loadoutProfile,
			UnitRole role,
			BreachCapability breachCapability,
			int wallDamage,
			int count,
			BattleLane lane,
			List<String> tags
		) {
			this.id = id;
			this.unitType = unitType;
			this.entityType = entityType;
			this.loadoutProfile = loadoutProfile;
			this.role = role;
			this.breachCapability = breachCapability == null ? BreachCapability.NONE : breachCapability;
			this.wallDamage = Math.max(0, wallDamage);
			this.count = Math.max(0, count);
			this.lane = lane;
			this.tags = tags == null ? List.of() : List.copyOf(tags);
		}
	}

	private static final class MutableEngineGroup {
		private final String id;
		private final String engineType;
		private final String entityType;
		private final BattleLane lane;
		private final List<String> tags;
		private int count;

		private MutableEngineGroup(
			String id,
			String engineType,
			String entityType,
			int count,
			BattleLane lane,
			List<String> tags
		) {
			this.id = id;
			this.engineType = engineType;
			this.entityType = entityType;
			this.count = Math.max(0, count);
			this.lane = lane;
			this.tags = tags == null ? List.of() : List.copyOf(tags);
		}
	}
}
