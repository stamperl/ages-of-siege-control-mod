package com.stamperl.agesofsiege.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SiegeCatalog {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("ages_of_siege")
		.resolve("siege_quests.json");
	private static final List<SiegeDefinition> BUILT_IN_DEFINITIONS = List.copyOf(buildBuiltInDefinitions());
	private static volatile List<SiegeDefinition> definitions = BUILT_IN_DEFINITIONS;

	private SiegeCatalog() {
	}

	public static synchronized void initialize() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.notExists(CONFIG_PATH)) {
				writeConfig(CONFIG_PATH, BUILT_IN_DEFINITIONS);
				AgesOfSiegeMod.LOGGER.info("Generated default siege quest config at {}", CONFIG_PATH);
			}
			List<SiegeDefinition> loaded = loadConfig(CONFIG_PATH);
			definitions = loaded.isEmpty() ? BUILT_IN_DEFINITIONS : List.copyOf(loaded);
			AgesOfSiegeMod.LOGGER.info("Loaded {} siege quest definitions from {}", definitions.size(), CONFIG_PATH);
		} catch (Exception exception) {
			definitions = BUILT_IN_DEFINITIONS;
			AgesOfSiegeMod.LOGGER.error("Failed to load siege quest config from {}. Falling back to built-in defaults.", CONFIG_PATH, exception);
		}
	}

	public static List<SiegeDefinition> all() {
		return definitions;
	}

	public static List<SiegeDefinition> allForState(SiegeBaseState state) {
		return all().stream()
			.map(definition -> resolveForState(state, definition.id()))
			.toList();
	}

	public static SiegeDefinition byId(String id) {
		for (SiegeDefinition definition : all()) {
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
		int ramCount = definition.ageLevel() >= 1 && variant == 3 ? Math.max(1, definition.ramCount()) : 0;
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
			ramCount,
			enemySummary,
			weaponSummary,
			threatSummary,
			definition.warSuppliesReward(),
			true
		);
	}

	public static SiegeDefinition defaultSiegeForAge(int ageLevel) {
		return all().stream()
			.filter(definition -> definition.ageLevel() == ageLevel && !definition.ageDefining() && !definition.minorRaid())
			.min(Comparator.comparingInt(SiegeDefinition::routeColumn))
			.orElse(all().get(0));
	}

	public static SiegeDefinition highestUnlocked(SiegeBaseState state) {
		SiegeDefinition unlocked = defaultSiegeForAge(state.getAgeLevel());
		for (SiegeDefinition definition : all()) {
			if (definition.isUnlocked(state)) {
				unlocked = definition;
			}
		}
		return unlocked;
	}

	private static List<SiegeDefinition> loadConfig(Path configPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(configPath)) {
			SiegeConfigRoot root = GSON.fromJson(reader, SiegeConfigRoot.class);
			if (root == null || root.sieges == null || root.sieges.isEmpty()) {
				throw new IOException("No siege definitions found in config.");
			}
			List<SiegeDefinition> loaded = new ArrayList<>();
			Map<String, SiegeDefinition> seen = new LinkedHashMap<>();
			for (SiegeDefinitionJson json : root.sieges) {
				SiegeDefinition definition = json.toDefinition();
				if (seen.put(definition.id(), definition) != null) {
					throw new IOException("Duplicate siege definition id: " + definition.id());
				}
				loaded.add(definition);
			}
			loaded.sort(Comparator
				.comparingInt(SiegeDefinition::ageLevel)
				.thenComparingInt(SiegeDefinition::routeColumn)
				.thenComparingInt(SiegeDefinition::routeRow)
				.thenComparing(SiegeDefinition::id));
			return loaded;
		}
	}

	private static void writeConfig(Path configPath, List<SiegeDefinition> sourceDefinitions) throws IOException {
		SiegeConfigRoot root = new SiegeConfigRoot();
		root.note = "Edit siege nodes here. routeColumn controls left-to-right order, routeRow controls the lane, and ageDefining should usually be the final milestone node for an age.";
		root.sieges = sourceDefinitions.stream()
			.map(SiegeDefinitionJson::fromDefinition)
			.toList();
		try (Writer writer = Files.newBufferedWriter(configPath)) {
			GSON.toJson(root, writer);
		}
	}

	private static List<SiegeDefinition> buildBuiltInDefinitions() {
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
				0,
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
				2,
				false,
				0,
				1,
				0,
				6,
				0,
				"Pillagers in a wider line",
				"Crossbows and raider sidearms",
				"Wider pressure with more bodies.",
				3,
				false
			),
			minorRaid("homestead_patrol_ii", "Homestead Patrol II", 0, 3, 1, 1, 6, 2),
			minorRaid("homestead_patrol_iii", "Homestead Patrol III", 0, 4, 2, 2, 6, 2),
			minorRaid("homestead_patrol_iv", "Homestead Patrol IV", 0, 5, 3, 2, 7, 3),
			minorRaid("homestead_patrol_v", "Homestead Patrol V", 0, 6, 4, 1, 7, 3),
			new SiegeDefinition(
				"homestead_age_siege",
				"Homestead Age Siege",
				"The first real push against the settlement, with breachers leading the assault.",
				0,
				7,
				true,
				5,
				0,
				1,
				10,
				0,
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
				0,
				false,
				0,
				0,
				1,
				7,
				0,
				"Pillagers with breachers",
				"Crossbows, axes, and iron tools",
				"Sharper pressure against weak wall angles.",
				4,
				false
			),
			minorRaid("fortified_patrol_i", "Fortified Patrol I", 1, 1, 0, 1, 7, 3),
			new SiegeDefinition(
				"gatehouse_siege",
				"Gatehouse Siege",
				"A stronger line focuses on the gate and front defenses.",
				1,
				2,
				false,
				0,
				2,
				1,
				8,
				0,
				"Breachers and ranged raiders",
				"Axes, crossbows, and reinforced raider gear",
				"Focused gate pressure with better escort cover.",
				4,
				false
			),
			minorRaid("fortified_patrol_ii", "Fortified Patrol II", 1, 3, 1, 2, 8, 3),
			minorRaid("fortified_patrol_iii", "Fortified Patrol III", 1, 4, 2, 1, 8, 4),
			minorRaid("fortified_patrol_iv", "Fortified Patrol IV", 1, 5, 3, 1, 9, 4),
			minorRaid("fortified_patrol_v", "Fortified Patrol V", 1, 6, 4, 2, 9, 4),
			new SiegeDefinition(
				"fortified_age_siege",
				"Fortified Age Siege",
				"A major breach attempt with a ram and larger escort line.",
				1,
				7,
				true,
				5,
				0,
				2,
				13,
				1,
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
				0,
				false,
				0,
				0,
				2,
				9,
				1,
				"Breachers, escorts, and ranged support",
				"Iron breach gear, crossbows, and escort weapons",
				"Steadier pressure with tougher frontliners.",
				5,
				false
			),
			minorRaid("ironkeep_sortie_i", "Ironkeep Sortie I", 2, 1, 0, 1, 9, 4),
			new SiegeDefinition(
				"ram_line_push",
				"Ram Line Push",
				"A ram-supported column advances behind a stronger escort shell.",
				2,
				2,
				false,
				0,
				2,
				2,
				10,
				1,
				"Ram support, escorts, and breachers",
				"Heavy tools, crossbows, and reinforced siege gear",
				"Ram pressure arrives earlier and with better support.",
				5,
				false
			),
			minorRaid("ironkeep_sortie_ii", "Ironkeep Sortie II", 2, 3, 1, 2, 10, 4),
			minorRaid("ironkeep_sortie_iii", "Ironkeep Sortie III", 2, 4, 2, 1, 10, 5),
			minorRaid("ironkeep_sortie_iv", "Ironkeep Sortie IV", 2, 5, 3, 1, 11, 5),
			minorRaid("ironkeep_sortie_v", "Ironkeep Sortie V", 2, 6, 4, 2, 11, 5),
			new SiegeDefinition(
				"ironkeep_age_siege",
				"Ironkeep Age Siege",
				"A long breach battle with a large ram line and punishing front pressure.",
				2,
				7,
				true,
				5,
				0,
				3,
				16,
				2,
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
				0,
				false,
				0,
				0,
				3,
				11,
				1,
				"Veteran breachers, escorts, and ranged support",
				"Reinforced siege weapons and advanced support gear",
				"High pressure with stronger escort coverage.",
				6,
				false
			),
			minorRaid("industry_counterraid_i", "Industry Counterraid I", 3, 1, 0, 1, 11, 5),
			new SiegeDefinition(
				"foundry_break",
				"Foundry Break",
				"A reinforced wave pushes hard on one lane while ranged support pins the walls.",
				3,
				2,
				false,
				0,
				2,
				3,
				12,
				2,
				"Heavy breach team, escorts, and ranged support",
				"Industrial-age crossbows, axes, and reinforced breach kits",
				"Longer industrial pressure with deeper support lines.",
				6,
				false
			),
			minorRaid("industry_counterraid_ii", "Industry Counterraid II", 3, 3, 1, 2, 12, 5),
			minorRaid("industry_counterraid_iii", "Industry Counterraid III", 3, 4, 2, 1, 12, 6),
			minorRaid("industry_counterraid_iv", "Industry Counterraid IV", 3, 5, 3, 1, 13, 6),
			minorRaid("industry_counterraid_v", "Industry Counterraid V", 3, 6, 4, 2, 13, 6),
			new SiegeDefinition(
				"industry_age_siege",
				"Industry Age Siege",
				"The final age-defining siege: a full combined assault meant to feel like a boss raid.",
				3,
				7,
				true,
				5,
				0,
				3,
				18,
				2,
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
			0,
			"Adaptive raiders",
			"Field gear that changes between runs",
			"Variable pressure route.",
			reward,
			true
		);
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
		int ramCount,
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
			return state.getCurrentAgeRegularWins() >= requiredRegularWins;
		}

		public boolean isReplay(SiegeBaseState state) {
			return ageLevel < state.getAgeLevel();
		}
	}

	private static final class SiegeConfigRoot {
		String note;
		List<SiegeDefinitionJson> sieges = List.of();
	}

	private static final class SiegeDefinitionJson {
		String id;
		String displayName;
		String description;
		int ageLevel;
		int routeColumn;
		boolean ageDefining;
		int requiredRegularWins;
		int routeRow;
		int combatTier;
		int waveSize;
		int ramCount;
		String enemySummary;
		String weaponSummary;
		String threatSummary;
		int warSuppliesReward;
		boolean minorRaid;

		private SiegeDefinition toDefinition() {
			return new SiegeDefinition(
				id,
				displayName,
				description,
				ageLevel,
				routeColumn,
				ageDefining,
				requiredRegularWins,
				routeRow,
				combatTier,
				waveSize,
				ramCount,
				enemySummary,
				weaponSummary,
				threatSummary,
				warSuppliesReward,
				minorRaid
			);
		}

		private static SiegeDefinitionJson fromDefinition(SiegeDefinition definition) {
			SiegeDefinitionJson json = new SiegeDefinitionJson();
			json.id = definition.id();
			json.displayName = definition.displayName();
			json.description = definition.description();
			json.ageLevel = definition.ageLevel();
			json.routeColumn = definition.routeColumn();
			json.ageDefining = definition.ageDefining();
			json.requiredRegularWins = definition.requiredRegularWins();
			json.routeRow = definition.routeRow();
			json.combatTier = definition.combatTier();
			json.waveSize = definition.waveSize();
			json.ramCount = definition.ramCount();
			json.enemySummary = definition.enemySummary();
			json.weaponSummary = definition.weaponSummary();
			json.threatSummary = definition.threatSummary();
			json.warSuppliesReward = definition.warSuppliesReward();
			json.minorRaid = definition.minorRaid();
			return json;
		}
	}
}
