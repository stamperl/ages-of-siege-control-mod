package com.stamperl.agesofsiege.siege;

import java.util.List;
import java.util.Objects;

public final class SiegeCatalog {
	private static final List<SiegeDefinition> DEFINITIONS = List.of(
		new SiegeDefinition(
			"homestead_raid",
			"Homestead Raid",
			"An early probing attack meant to test the perimeter.",
			0,
			0,
			6,
			false,
			"Pillagers only",
			"Crossbows and light raider gear",
			"Low pressure. Good first defense.",
			3
		),
		new SiegeDefinition(
			"fortified_assault",
			"Fortified Assault",
			"Breachers begin joining the raiding line and punish weak walls.",
			1,
			1,
			7,
			false,
			"Pillagers with breachers",
			"Crossbows, axes, and iron tools",
			"Breachers pressure the shortest route to the banner.",
			4
		),
		new SiegeDefinition(
			"ironkeep_breach",
			"Ironkeep Breach",
			"A full breach attempt with ranged cover and a battering ram.",
			2,
			3,
			9,
			true,
			"Breachers, ranged raiders, and a ram",
			"Axes, crossbows, and siege ram support",
			"Ram and breachers must both be answered.",
			5
		),
		new SiegeDefinition(
			"early_industry_push",
			"Early Industry Push",
			"A heavier combined assault with more escorts and sustained pressure.",
			3,
			6,
			12,
			true,
			"Large breach team, escorts, ranged support, and a ram",
			"Axes, crossbows, escort weapons, and reinforced siege gear",
			"Longer pressure window with stronger breach support.",
			6
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

	public static SiegeDefinition highestUnlocked(int completedSieges) {
		SiegeDefinition unlocked = DEFINITIONS.get(0);
		for (SiegeDefinition definition : DEFINITIONS) {
			if (definition.isUnlocked(completedSieges)) {
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
		int unlockVictories,
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

		public boolean isUnlocked(int completedSieges) {
			return completedSieges >= unlockVictories;
		}
	}
}
