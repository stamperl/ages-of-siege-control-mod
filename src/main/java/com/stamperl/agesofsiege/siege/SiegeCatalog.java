package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.state.SiegeBaseState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SiegeCatalog {
	private static final List<SiegeDefinition> DEFINITIONS = List.of(
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
			3
		),
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
			3
		),
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
			6
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
			4
		),
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
			4
		),
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
			7
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
			5
		),
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
			5
		),
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
			9
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
			6
		),
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
			6
		),
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
			12
		)
	);

	private SiegeCatalog() {
	}

	public static List<SiegeDefinition> all() {
		return DEFINITIONS;
	}

	public static SiegeDefinition byId(String id) {
		for (SiegeDefinition definition : DEFINITIONS) {
			if (definition.id().equals(id)) {
				return definition;
			}
		}
		return null;
	}

	public static SiegeDefinition defaultSiegeForAge(int ageLevel) {
		return DEFINITIONS.stream()
			.filter(definition -> definition.ageLevel() == ageLevel && !definition.ageDefining())
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
		int warSuppliesReward
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
