package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.state.SiegeBaseState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SiegeCatalog {
	private static final List<SiegeDefinition> DEFINITIONS = List.copyOf(buildDefinitions());

	private static List<SiegeDefinition> buildDefinitions() {
		List<SiegeDefinition> definitions = new ArrayList<>();
		definitions.addAll(List.of(
		new SiegeDefinition(
			"homestead_watch",
			"Homestead Watch",
			"A light raid probing the outer farms and fences.",
			0,
			0,
			false,
			0,
			0,
			0,
			5,
			false,
			"Pillagers only",
			"Simple crossbows and wooden tools",
			"Low pressure farm-edge raid.",
			3,
			false
		),
		minorRaid("homestead_patrol_i", "Homestead Patrol I", 0, 1, 0, 0, 5, 2),
		new SiegeDefinition(
			"fieldside_raid",
			"Fieldside Raid",
			"A broader skirmish that tests two lanes at once.",
			0,
			1,
			false,
			0,
			1,
			0,
			6,
			false,
			"Pillagers in a wider line",
			"Crossbows and raider sidearms",
			"Wider pressure with more bodies.",
			3,
			false
		),
		minorRaid("homestead_patrol_ii", "Homestead Patrol II", 0, 3, 1, 1, 6, 2),
		minorRaid("homestead_patrol_iii", "Homestead Patrol III", 0, 4, 2, 1, 6, 2),
		minorRaid("homestead_patrol_iv", "Homestead Patrol IV", 0, 5, 3, 2, 7, 3),
		minorRaid("homestead_patrol_v", "Homestead Patrol V", 0, 6, 4, 2, 7, 3),
		new SiegeDefinition(
			"homestead_age_siege",
			"Homestead Age Siege",
			"The first real push against the settlement, with breachers leading the assault.",
			0,
			2,
			true,
			5,
			2,
			1,
			10,
			false,
			"Breachers, raiders, and heavy pressure",
			"Axes, crossbows, and early breach tools",
			"First age-defining boss raid.",
			6,
			false
		),
		new SiegeDefinition(
			"fort_wall_probe",
			"Fort Wall Probe",
			"Raiders pressure the walls to measure the new fortifications.",
			1,
			4,
			false,
			0,
			0,
			1,
			7,
			false,
			"Pillagers with breachers",
			"Crossbows, axes, and iron tools",
			"Sharper pressure against weak wall angles.",
			4,
			false
		),
		minorRaid("fortified_patrol_i", "Fortified Patrol I", 1, 1, 0, 0, 7, 3),
		new SiegeDefinition(
			"gatehouse_siege",
			"Gatehouse Siege",
			"A stronger line focuses on the gate and front defenses.",
			1,
			5,
			false,
			0,
			1,
			1,
			8,
			false,
			"Breachers and ranged raiders",
			"Axes, crossbows, and reinforced raider gear",
			"Focused gate pressure with better escort cover.",
			4,
			false
		),
		minorRaid("fortified_patrol_ii", "Fortified Patrol II", 1, 3, 1, 1, 8, 3),
		minorRaid("fortified_patrol_iii", "Fortified Patrol III", 1, 4, 2, 1, 8, 4),
		minorRaid("fortified_patrol_iv", "Fortified Patrol IV", 1, 5, 3, 2, 9, 4),
		minorRaid("fortified_patrol_v", "Fortified Patrol V", 1, 6, 4, 2, 9, 4),
		new SiegeDefinition(
			"fortified_age_siege",
			"Fortified Age Siege",
			"A major breach attempt with a ram and larger escort line.",
			1,
			6,
			true,
			5,
			2,
			2,
			13,
			true,
			"Breachers, ranged raiders, escorts, and a ram",
			"Axes, crossbows, iron gear, and siege support",
			"Fortified age boss raid with a true breach threat.",
			7,
			false
		),
		new SiegeDefinition(
			"ironkeep_skirmish",
			"Ironkeep Skirmish",
			"A hardened strike force pushes into the perimeter.",
			2,
			8,
			false,
			0,
			0,
			2,
			9,
			true,
			"Breachers, escorts, and ranged support",
			"Iron breach gear, crossbows, and escort weapons",
			"Steadier pressure with tougher frontliners.",
			5,
			false
		),
		minorRaid("ironkeep_sortie_i", "Ironkeep Sortie I", 2, 1, 0, 0, 9, 4),
		new SiegeDefinition(
			"ram_line_push",
			"Ram Line Push",
			"A ram-supported column advances behind a stronger escort shell.",
			2,
			9,
			false,
			0,
			1,
			2,
			10,
			true,
			"Ram support, escorts, and breachers",
			"Heavy tools, crossbows, and reinforced siege gear",
			"Ram pressure arrives earlier and with better support.",
			5,
			false
		),
		minorRaid("ironkeep_sortie_ii", "Ironkeep Sortie II", 2, 3, 1, 1, 10, 4),
		minorRaid("ironkeep_sortie_iii", "Ironkeep Sortie III", 2, 4, 2, 1, 10, 5),
		minorRaid("ironkeep_sortie_iv", "Ironkeep Sortie IV", 2, 5, 3, 2, 11, 5),
		minorRaid("ironkeep_sortie_v", "Ironkeep Sortie V", 2, 6, 4, 2, 11, 5),
		new SiegeDefinition(
			"ironkeep_age_siege",
			"Ironkeep Age Siege",
			"A long breach battle with a large ram line and punishing front pressure.",
			2,
			10,
			true,
			5,
			2,
			3,
			16,
			true,
			"Large breach line, escorts, ranged support, and heavy ram pressure",
			"Reinforced iron siege gear and veteran raider weapons",
			"Boss raid with sustained breach pressure and a larger assault line.",
			9,
			false
		),
		new SiegeDefinition(
			"smokehouse_pressure",
			"Smokehouse Pressure",
			"Industrial-age raiders stretch the defense with a longer attack line.",
			3,
			12,
			false,
			0,
			0,
			3,
			11,
			true,
			"Veteran breachers, escorts, and ranged support",
			"Reinforced siege weapons and advanced support gear",
			"High pressure with stronger escort coverage.",
			6,
			false
		),
		minorRaid("industry_counterraid_i", "Industry Counterraid I", 3, 1, 0, 0, 11, 5),
		new SiegeDefinition(
			"foundry_break",
			"Foundry Break",
			"A reinforced wave pushes hard on one lane while ranged support pins the walls.",
			3,
			13,
			false,
			0,
			1,
			3,
			12,
			true,
			"Heavy breach team, escorts, and ranged support",
			"Industrial-age crossbows, axes, and reinforced breach kits",
			"Longer industrial pressure with deeper support lines.",
			6,
			false
		),
		minorRaid("industry_counterraid_ii", "Industry Counterraid II", 3, 3, 1, 1, 12, 5),
		minorRaid("industry_counterraid_iii", "Industry Counterraid III", 3, 4, 2, 1, 12, 6),
		minorRaid("industry_counterraid_iv", "Industry Counterraid IV", 3, 5, 3, 2, 13, 6),
		minorRaid("industry_counterraid_v", "Industry Counterraid V", 3, 6, 4, 2, 13, 6),
		new SiegeDefinition(
			"industry_age_siege",
			"Industry Age Siege",
			"The final age-defining siege: a full combined assault meant to feel like a boss raid.",
			3,
			14,
			true,
			5,
			2,
			3,
			18,
			true,
			"Mass breach team, ram support, escorts, and ranged cover",
			"Heavy industrial siege gear and veteran raider loadouts",
			"End-tier boss raid with the heaviest sustained pressure in the campaign.",
			12,
			false
		)
		));
		return definitions;
	}

	private static SiegeDefinition minorRaid(String id, String displayName, int ageLevel, int routeColumn, int requiredRegularWins, int routeRow, int combatTier, int reward) {
		return new SiegeDefinition(
			id,
			displayName,
			"A shifting war-path raid whose composition changes each time it is staged.",
			ageLevel,
			routeColumn,
			false,
			requiredRegularWins,
			routeRow,
			combatTier,
			Math.max(4, combatTier),
			false,
			"Adaptive raiders",
			"Field gear that changes between runs",
			"Variable pressure route.",
			reward,
			true
		);
	}

	private SiegeCatalog() {
	}

	public static List<SiegeDefinition> all() {
		return DEFINITIONS;
	}

	public static List<SiegeDefinition> allForState(SiegeBaseState state) {
		return DEFINITIONS.stream()
			.map(definition -> resolveForState(state, definition.id()))
			.toList();
	}

	public static SiegeDefinition byId(String id) {
		for (SiegeDefinition definition : DEFINITIONS) {
			if (definition.id().equals(id)) {
				return definition;
			}
		}
		return null;
	}

	public static SiegeDefinition resolveForState(SiegeBaseState state, String id) {
		SiegeDefinition definition = byId(id);
		if (definition == null || state == null || !definition.minorRaid()) {
			return definition;
		}
		int variant = Math.floorMod(state.getCompletedSieges() + definition.routeColumn() + (state.getCurrentAgeRegularWins() * 2), 4);
		String variantLabel = switch (variant) {
			case 0 -> "Scouting Run";
			case 1 -> "Shield Break";
			case 2 -> "Crossfire Push";
			default -> "Shock Column";
		};
		int waveSize = definition.waveSize() + variant;
		boolean hasRam = definition.ageLevel() >= 1 && variant == 3;
		String enemySummary = switch (definition.ageLevel()) {
			case 0 -> switch (variant) {
				case 0 -> "Scouts and fence-line raiders";
				case 1 -> "Melee raiders with light shield cover";
				case 2 -> "Crossbow raiders and flankers";
				default -> "Mixed farm raiders with a heavy front";
			};
			case 1 -> switch (variant) {
				case 0 -> "Wall scouts with axe teams";
				case 1 -> "Gate raiders with shield escorts";
				case 2 -> "Crossbow teams and breachers";
				default -> "Heavy breach team with escort cover";
			};
			case 2 -> switch (variant) {
				case 0 -> "Iron raiders probing the line";
				case 1 -> "Escort wedge with breach tools";
				case 2 -> "Ranged support behind a hard front";
				default -> "Ram escort column and breach crew";
			};
			default -> switch (variant) {
				case 0 -> "Industrial scouts and saboteurs";
				case 1 -> "Press gangs with shielded escorts";
				case 2 -> "Foundry shooters and breach teams";
				default -> "Heavy industrial column with assault support";
			};
		};
		String weaponSummary = switch (definition.ageLevel()) {
			case 0 -> switch (variant) {
				case 0 -> "Wooden tools and light crossbows";
				case 1 -> "Axes, clubs, and rough shields";
				case 2 -> "Crossbows, knives, and farm blades";
				default -> "Mixed melee gear with a harder front";
			};
			case 1 -> switch (variant) {
				case 0 -> "Crossbows, axes, and iron sidearms";
				case 1 -> "Axes and reinforced shields";
				case 2 -> "Crossbows with breach cutters";
				default -> "Heavy tools, crossbows, and breach kits";
			};
			case 2 -> switch (variant) {
				case 0 -> "Iron weapons and ranged cover";
				case 1 -> "Heavy breach tools and escorts";
				case 2 -> "Veteran crossbows and swords";
				default -> "Ram tools, axes, and iron escort gear";
			};
			default -> switch (variant) {
				case 0 -> "Industrial tools and raid bows";
				case 1 -> "Reinforced axes and support shields";
				case 2 -> "Crossbows, charges, and breach gear";
				default -> "Heavy industrial breach tools and ram support";
			};
		};
		String threatSummary = switch (variant) {
			case 0 -> "Fast scouting pressure that shifts lanes.";
			case 1 -> "Front-loaded melee pressure into one weak angle.";
			case 2 -> "Ranged harassment with a timed breach push.";
			default -> "Heavier assault timing with the toughest escort shell.";
		};
		return new SiegeDefinition(
			definition.id(),
			definition.displayName() + ": " + variantLabel,
			"A generated raid contract that rotates its formation based on recent victories.",
			definition.ageLevel(),
			definition.routeColumn(),
			definition.ageDefining(),
			definition.requiredRegularWins(),
			definition.routeRow(),
			definition.combatTier() + (variant / 2),
			waveSize,
			hasRam,
			enemySummary,
			weaponSummary,
			threatSummary,
			definition.warSuppliesReward(),
			true
		);
	}

	public static SiegeDefinition defaultSiegeForAge(int ageLevel) {
		return DEFINITIONS.stream()
			.filter(definition -> definition.ageLevel() == ageLevel && !definition.ageDefining() && !definition.minorRaid())
			.min(Comparator.comparingInt(SiegeDefinition::routeColumn))
			.orElse(DEFINITIONS.get(0));
	}

	public static SiegeDefinition highestUnlocked(SiegeBaseState state) {
		SiegeDefinition unlocked = defaultSiegeForAge(state.getAgeLevel());
		for (SiegeDefinition definition : DEFINITIONS) {
			if (definition.isUnlocked(state)) {
				unlocked = definition;
			}
		}
		return unlocked;
	}

	public record SiegeDefinition(
		String id,
		String displayName,
		String description,
		int ageLevel,
		int routeColumn,
		boolean ageDefining,
		int requiredRegularWins,
		int routeRow,
		int combatTier,
		int waveSize,
		boolean hasRam,
		String enemySummary,
		String weaponSummary,
		String threatSummary,
		int warSuppliesReward,
		boolean minorRaid
	) {
		public SiegeDefinition {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(displayName, "displayName");
			Objects.requireNonNull(description, "description");
			Objects.requireNonNull(enemySummary, "enemySummary");
			Objects.requireNonNull(weaponSummary, "weaponSummary");
			Objects.requireNonNull(threatSummary, "threatSummary");
		}

		public boolean isUnlocked(SiegeBaseState state) {
			if (ageLevel < state.getAgeLevel()) {
				return true;
			}
			if (ageLevel > state.getAgeLevel()) {
				return false;
			}
			return !ageDefining || state.getCurrentAgeRegularWins() >= requiredRegularWins;
		}

		public boolean isReplay(SiegeBaseState state) {
			return ageLevel < state.getAgeLevel();
		}
	}
}
