package com.stamperl.agesofsiege.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
	private static final String BUNDLED_DEFAULT_PATH = "/defaults/ages_of_siege/siege_quests.json";
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("ages_of_siege")
		.resolve("siege_quests.json");
	private static final List<SiegeDefinition> BUILT_IN_DEFINITIONS = List.copyOf(buildBuiltInDefinitions());
	private static volatile CatalogState catalogState = CatalogState.fromLegacyDefinitions(BUILT_IN_DEFINITIONS);

	private SiegeCatalog() {
	}

	public static synchronized void initialize() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.notExists(CONFIG_PATH)) {
				copyBundledDefaultConfig(CONFIG_PATH);
				AgesOfSiegeMod.LOGGER.info("Copied bundled siege quest config to {}", CONFIG_PATH);
			}
			CatalogState loaded = loadConfig(CONFIG_PATH);
			catalogState = loaded == null ? CatalogState.fromLegacyDefinitions(BUILT_IN_DEFINITIONS) : loaded;
			AgesOfSiegeMod.LOGGER.info(
				"Loaded {} siege quest nodes and {} battle templates from {}",
				catalogState.campaignNodes().size(),
				catalogState.battleTemplates().size(),
				CONFIG_PATH
			);
		} catch (Exception exception) {
			catalogState = CatalogState.fromLegacyDefinitions(BUILT_IN_DEFINITIONS);
			AgesOfSiegeMod.LOGGER.error("Failed to load siege quest config from {}. Falling back to built-in defaults.", CONFIG_PATH, exception);
		}
	}

	private static void copyBundledDefaultConfig(Path configPath) throws IOException {
		try (InputStream input = SiegeCatalog.class.getResourceAsStream(BUNDLED_DEFAULT_PATH)) {
			if (input == null) {
				throw new IOException("Bundled default siege config is missing: " + BUNDLED_DEFAULT_PATH);
			}
			Files.copy(input, configPath);
		}
	}

	public static List<SiegeDefinition> all() {
		return catalogState.all();
	}

	public static List<SiegeDefinition> allForState(SiegeBaseState state) {
		return catalogState.allForState(state);
	}

	public static SiegeDefinition byId(String id) {
		return catalogState.byId(id);
	}

	public static SiegeDefinition resolveForState(SiegeBaseState state, String id) {
		return catalogState.resolveForState(state, id);
	}

	public static SiegeDefinition defaultSiegeForAge(int ageLevel) {
		return catalogState.defaultSiegeForAge(ageLevel);
	}

	public static SiegeDefinition highestUnlocked(SiegeBaseState state) {
		return catalogState.highestUnlocked(state);
	}

	public static SiegeCampaignNode campaignNode(String id) {
		return catalogState.campaignNode(id);
	}

	public static SiegeBattleTemplate battleTemplate(String id) {
		return catalogState.battleTemplate(id);
	}

	public static boolean isDefinitionUnlocked(SiegeBaseState state, SiegeDefinition definition) {
		if (state == null || definition == null) {
			return false;
		}
		SiegeCampaignNode node = campaignNode(definition.id());
		return node != null && isNodeUnlocked(state, node);
	}

	public static boolean isDefinitionReplay(SiegeBaseState state, SiegeDefinition definition) {
		if (state == null || definition == null) {
			return false;
		}
		SiegeCampaignNode node = campaignNode(definition.id());
		return node != null && isNodeReplay(state, node);
	}

	public static boolean isNodeUnlocked(SiegeBaseState state, SiegeCampaignNode node) {
		if (state == null || node == null) {
			return false;
		}
		if (node.ageLevel() < state.getAgeLevel()) {
			return true;
		}
		if (node.ageLevel() > state.getAgeLevel()) {
			return false;
		}
		if (state.hasCompletedSiege(node.id())) {
			return true;
		}
		List<SiegeCampaignNode> ageNodes = campaignNodesForAge(node.ageLevel());
		if (ageNodes.isEmpty()) {
			return false;
		}
		int index = ageNodes.indexOf(node);
		if (index <= 0) {
			return true;
		}
		for (int i = 0; i < index; i++) {
			if (!state.hasCompletedSiege(ageNodes.get(i).id())) {
				return false;
			}
		}
		return true;
	}

	public static boolean isNodeReplay(SiegeBaseState state, SiegeCampaignNode node) {
		if (state == null || node == null) {
			return false;
		}
		return node.ageLevel() < state.getAgeLevel() || state.hasCompletedSiege(node.id());
	}

	public static SiegeDefinition nextSequentialSiege(SiegeBaseState state, String currentSiegeId) {
		SiegeDefinition current = byId(currentSiegeId);
		if (current == null) {
			return highestUnlocked(state);
		}
		List<SiegeCampaignNode> ageNodes = campaignNodesForAge(current.ageLevel());
		for (int i = 0; i < ageNodes.size(); i++) {
			if (!ageNodes.get(i).id().equals(current.id())) {
				continue;
			}
			for (int nextIndex = i + 1; nextIndex < ageNodes.size(); nextIndex++) {
				SiegeCampaignNode nextNode = ageNodes.get(nextIndex);
				if (!state.hasCompletedSiege(nextNode.id())) {
					return resolveForState(state, nextNode.id());
				}
			}
		}
		return resolveForState(state, current.id());
	}

	private static List<SiegeCampaignNode> campaignNodesForAge(int ageLevel) {
		return catalogState.campaignNodes().stream()
			.filter(node -> node.ageLevel() == ageLevel)
			.sorted(Comparator
				.comparingInt(SiegeCampaignNode::routeColumn)
				.thenComparingInt(SiegeCampaignNode::routeRow)
				.thenComparing(SiegeCampaignNode::displayName)
				.thenComparing(SiegeCampaignNode::id))
			.toList();
	}

	private static CatalogState loadConfig(Path configPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(configPath)) {
			SiegeConfigRoot root = GSON.fromJson(reader, SiegeConfigRoot.class);
			if (root == null) {
				throw new IOException("Siege config is empty or unreadable.");
			}
			return CatalogState.fromRoot(root);
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
			return SiegeCatalog.isDefinitionUnlocked(state, this);
		}

		public boolean isReplay(SiegeBaseState state) {
			return state != null && SiegeCatalog.isDefinitionReplay(state, this);
		}
	}

	private static final class CatalogState {
		private static final int CURRENT_SCHEMA_VERSION = 2;
		private static final List<SiegeBattleVariant> DEFAULT_MINOR_RAID_VARIANTS = List.of(
			new SiegeBattleVariant(
				"scouting_run",
				"Scouting Run",
				"Light pressure with a flexible attack line.",
				0,
				0,
				0,
				"Adaptive raiders",
				"Field gear and light crossbows",
				"Fast probing pressure."
			),
			new SiegeBattleVariant(
				"shield_break",
				"Shield Break",
				"Adds a little more body mass and front pressure.",
				1,
				0,
				0,
				"Adaptive raiders",
				"Field gear and breaking tools",
				"Forward pressure against the line."
			),
			new SiegeBattleVariant(
				"crossfire_push",
				"Crossfire Push",
				"Brings stronger ranged harassment into the mix.",
				1,
				0,
				1,
				"Crossbow raiders",
				"Crossbows, knives, and field gear",
				"Ranged pressure with coordinated cover."
			),
			new SiegeBattleVariant(
				"shock_column",
				"Shock Column",
				"A heavier, more committed push with breach support.",
				2,
				1,
				1,
				"Heavy adaptive raiders",
				"Reinforced tools and siege support",
				"Harder pressure with a stronger breach threat."
			)
		);

		private final List<SiegeCampaignNode> campaignNodes;
		private final List<SiegeBattleTemplate> battleTemplates;
		private final Map<String, SiegeCampaignNode> campaignNodeById;
		private final Map<String, SiegeBattleTemplate> battleTemplateById;
		private final Map<String, SiegeDefinition> definitionById;
		private final List<SiegeDefinition> orderedDefinitions;

		private CatalogState(
			List<SiegeCampaignNode> campaignNodes,
			List<SiegeBattleTemplate> battleTemplates,
			Map<String, SiegeCampaignNode> campaignNodeById,
			Map<String, SiegeBattleTemplate> battleTemplateById,
			Map<String, SiegeDefinition> definitionById,
			List<SiegeDefinition> orderedDefinitions
		) {
			this.campaignNodes = campaignNodes;
			this.battleTemplates = battleTemplates;
			this.campaignNodeById = campaignNodeById;
			this.battleTemplateById = battleTemplateById;
			this.definitionById = definitionById;
			this.orderedDefinitions = orderedDefinitions;
		}

		static CatalogState fromRoot(SiegeConfigRoot root) throws IOException {
			if (root == null) {
				throw new IOException("Siege config root is empty.");
			}
			boolean hasNewSchema = root.hasAnyCampaignData();
			boolean hasLegacySchema = root.sieges != null && !root.sieges.isEmpty();
			if (hasNewSchema) {
				if (root.campaignNodes == null || root.campaignNodes.isEmpty()) {
					throw new IOException("Siege config is missing campaignNodes.");
				}
				if (root.battleTemplates == null || root.battleTemplates.isEmpty()) {
					throw new IOException("Siege config is missing battleTemplates.");
				}
				if (root.schemaVersion != null && root.schemaVersion != CURRENT_SCHEMA_VERSION) {
					throw new IOException("Unsupported siege config schema version " + root.schemaVersion + ". Expected " + CURRENT_SCHEMA_VERSION + ".");
				}
				return fromCampaignSchema(root);
			}
			if (hasLegacySchema) {
				return fromLegacyJson(root.sieges);
			}
			throw new IOException("Siege config must contain either campaignNodes/battleTemplates or legacy sieges.");
		}

		static CatalogState fromLegacyDefinitions(List<SiegeDefinition> definitions) {
			List<SiegeCampaignNode> nodes = new ArrayList<>();
			List<SiegeBattleTemplate> templates = new ArrayList<>();
			for (SiegeDefinition definition : definitions) {
				SiegeCampaignNode node = legacyNodeFromDefinition(definition);
				SiegeBattleTemplate template = legacyTemplateFromDefinition(definition);
				nodes.add(node);
				templates.add(template);
			}
			return build(nodes, templates);
		}

		private static CatalogState fromLegacyJson(List<SiegeDefinitionJson> legacySieges) throws IOException {
			List<SiegeDefinition> definitions = new ArrayList<>();
			for (SiegeDefinitionJson json : legacySieges) {
				definitions.add(json.toDefinition());
			}
			return fromLegacyDefinitions(definitions);
		}

		private static CatalogState fromCampaignSchema(SiegeConfigRoot root) throws IOException {
			List<SiegeCampaignNode> nodes = new ArrayList<>();
			for (SiegeCampaignNodeJson json : safeList(root.campaignNodes)) {
				nodes.add(json.toNode());
			}
			List<SiegeBattleTemplate> templates = new ArrayList<>();
			for (SiegeBattleTemplateJson json : safeList(root.battleTemplates)) {
				templates.add(json.toTemplate());
			}
			return build(nodes, templates);
		}

		private static CatalogState build(List<SiegeCampaignNode> nodes, List<SiegeBattleTemplate> templates) {
			List<String> errors = new ArrayList<>();
			if (nodes == null || nodes.isEmpty()) {
				errors.add("No campaign nodes defined.");
			}
			if (templates == null || templates.isEmpty()) {
				errors.add("No battle templates defined.");
			}
			if (!errors.isEmpty()) {
				throw new IllegalStateException(formatErrors(errors));
			}

			List<SiegeCampaignNode> sortedNodes = nodes.stream()
				.sorted(Comparator
					.comparingInt(SiegeCampaignNode::ageLevel)
					.thenComparingInt(SiegeCampaignNode::routeColumn)
					.thenComparingInt(SiegeCampaignNode::routeRow)
					.thenComparing(SiegeCampaignNode::displayName)
					.thenComparing(SiegeCampaignNode::id))
				.toList();
			List<SiegeBattleTemplate> sortedTemplates = templates.stream()
				.sorted(Comparator.comparing(SiegeBattleTemplate::id))
				.toList();

			Map<String, SiegeCampaignNode> nodeById = new LinkedHashMap<>();
			for (SiegeCampaignNode node : sortedNodes) {
				validateNode(node, errors);
				String id = node.id();
				if (nodeById.putIfAbsent(id, node) != null) {
					errors.add("Duplicate campaign node id: " + id);
				}
			}

			Map<String, SiegeBattleTemplate> templateById = new LinkedHashMap<>();
			for (SiegeBattleTemplate template : sortedTemplates) {
				String id = template.id();
				if (templateById.putIfAbsent(id, template) != null) {
					errors.add("Duplicate battle template id: " + id);
				}
			}

			for (SiegeCampaignNode node : sortedNodes) {
				if (!templateById.containsKey(node.battleTemplateId())) {
					errors.add("Campaign node '" + node.id() + "' references missing battle template '" + node.battleTemplateId() + "'.");
				}
			}

			Map<Integer, Integer> maxRouteColumnByAge = new LinkedHashMap<>();
			for (SiegeCampaignNode node : sortedNodes) {
				maxRouteColumnByAge.merge(node.ageLevel(), node.routeColumn(), Math::max);
			}
			for (SiegeCampaignNode node : sortedNodes) {
				if (node.ageDefining() && node.routeColumn() < maxRouteColumnByAge.getOrDefault(node.ageLevel(), node.routeColumn())) {
					errors.add("Campaign node '" + node.id() + "' is ageDefining but is not terminal in age " + node.ageLevel() + ".");
				}
			}

			for (SiegeBattleTemplate template : sortedTemplates) {
				validateTemplate(template, errors);
			}

			if (!errors.isEmpty()) {
				throw new IllegalStateException(formatErrors(errors));
			}

			Map<String, SiegeDefinition> definitionById = new LinkedHashMap<>();
			List<SiegeDefinition> orderedDefinitions = new ArrayList<>();
			for (SiegeCampaignNode node : sortedNodes) {
				SiegeDefinition definition = resolveInternal(node, templateById.get(node.battleTemplateId()), null);
				orderedDefinitions.add(definition);
				definitionById.put(definition.id(), definition);
			}

			return new CatalogState(
				List.copyOf(sortedNodes),
				List.copyOf(sortedTemplates),
				Map.copyOf(nodeById),
				Map.copyOf(templateById),
				Map.copyOf(definitionById),
				List.copyOf(orderedDefinitions)
			);
		}

		List<SiegeDefinition> all() {
			return orderedDefinitions;
		}

		List<SiegeDefinition> allForState(SiegeBaseState state) {
			List<SiegeDefinition> resolved = new ArrayList<>(campaignNodes.size());
			for (SiegeCampaignNode node : campaignNodes) {
				SiegeBattleTemplate template = battleTemplateById.get(node.battleTemplateId());
				resolved.add(resolveInternal(node, template, state));
			}
			return List.copyOf(resolved);
		}

		SiegeDefinition byId(String id) {
			return definitionById.get(normalizeId(id));
		}

		SiegeDefinition resolveForState(SiegeBaseState state, String id) {
			SiegeCampaignNode node = campaignNodeById.get(normalizeId(id));
			if (node == null) {
				return null;
			}
			return resolveInternal(node, battleTemplateById.get(node.battleTemplateId()), state);
		}

		SiegeDefinition defaultSiegeForAge(int ageLevel) {
			SiegeCampaignNode node = campaignNodes.stream()
				.filter(entry -> entry.ageLevel() == ageLevel)
				.sorted(Comparator
					.comparingInt(SiegeCampaignNode::routeColumn)
					.thenComparingInt(SiegeCampaignNode::routeRow)
					.thenComparing(SiegeCampaignNode::displayName)
					.thenComparing(SiegeCampaignNode::id))
				.findFirst()
				.orElse(campaignNodes.get(0));
			return definitionById.get(node.id());
		}

		SiegeDefinition highestUnlocked(SiegeBaseState state) {
			if (state == null) {
				return orderedDefinitions.isEmpty() ? null : orderedDefinitions.get(0);
			}
			SiegeCampaignNode node = campaignNodes.stream()
				.filter(entry -> entry.isUnlocked(state))
				.sorted(Comparator
					.comparingInt(SiegeCampaignNode::ageLevel)
					.thenComparingInt(SiegeCampaignNode::routeColumn)
					.thenComparingInt(SiegeCampaignNode::routeRow)
					.thenComparing(SiegeCampaignNode::displayName)
					.thenComparing(SiegeCampaignNode::id))
				.reduce((first, second) -> second)
				.orElse(campaignNodes.get(0));
			return resolveInternal(node, battleTemplateById.get(node.battleTemplateId()), state);
		}

		SiegeCampaignNode campaignNode(String id) {
			return campaignNodeById.get(normalizeId(id));
		}

		SiegeBattleTemplate battleTemplate(String id) {
			return battleTemplateById.get(normalizeId(id));
		}

		List<SiegeCampaignNode> campaignNodes() {
			return campaignNodes;
		}

		List<SiegeBattleTemplate> battleTemplates() {
			return battleTemplates;
		}

		private static SiegeCampaignNode legacyNodeFromDefinition(SiegeDefinition definition) {
			return new SiegeCampaignNode(
				definition.id(),
				definition.displayName(),
				definition.description(),
				definition.ageLevel(),
				definition.routeColumn(),
				definition.routeRow(),
				definition.ageDefining(),
				definition.requiredRegularWins(),
				definition.id(),
				definition.warSuppliesReward(),
				definition.minorRaid()
			);
		}

		private static SiegeBattleTemplate legacyTemplateFromDefinition(SiegeDefinition definition) {
			int spawnTier = Math.max(0, Math.min(definition.ageLevel() + (definition.ageDefining() ? 1 : 0), 3));
			List<SiegeUnitGroup> unitGroups = legacyUnitGroups(definition);
			List<SiegeEngineGroup> engineGroups = definition.ramCount() > 0
				? List.of(new SiegeEngineGroup(definition.id() + "_ram", "ram", "ages_of_siege:ram", definition.ramCount(), List.of("legacy", "siege")))
				: List.of();
			List<SiegeBattleVariant> variants = definition.minorRaid() ? DEFAULT_MINOR_RAID_VARIANTS : List.of();
			return new SiegeBattleTemplate(
				definition.id(),
				spawnTier,
				Math.max(definition.combatTier(), spawnTier),
				definition.waveSize(),
				definition.ramCount(),
				legacyFormation(definition),
				definition.enemySummary(),
				definition.weaponSummary(),
				definition.threatSummary(),
				unitGroups,
				engineGroups,
				variants
			);
		}

		private static List<SiegeUnitGroup> legacyUnitGroups(SiegeDefinition definition) {
			int total = Math.max(1, definition.waveSize());
			int front = Math.max(1, total / 3);
			int rear = Math.max(1, total / 3);
			int escort = Math.max(0, total - front - rear);
			List<SiegeUnitGroup> groups = new ArrayList<>();
			groups.add(new SiegeUnitGroup(definition.id() + "_front", definition.minorRaid() ? "adaptive_front" : "front_line", "minecraft:pillager", "ranged", front, definition.ageDefining() ? "milestone_line" : "field_line", List.of("legacy", "front"), BreachCapability.NONE, null));
			groups.add(new SiegeUnitGroup(definition.id() + "_rear", definition.minorRaid() ? "adaptive_rear" : "rear_support", "minecraft:pillager", "escort", rear, definition.ageDefining() ? "milestone_cover" : "field_cover", List.of("legacy", "rear"), BreachCapability.NONE, null));
			if (escort > 0) {
				groups.add(new SiegeUnitGroup(definition.id() + "_escort", "escort_shell", "minecraft:vindicator", "breacher", escort, definition.ageDefining() ? "milestone_break" : "field_break", List.of("legacy", "escort"), BreachCapability.NONE, null));
			}
			return List.copyOf(groups);
		}

		private static String legacyFormation(SiegeDefinition definition) {
			if (definition.ageDefining()) {
				return "milestone";
			}
			if (definition.ramCount() > 0) {
				return "ram_push";
			}
			return switch (definition.ageLevel()) {
				case 0 -> "line";
				case 1 -> "wedge";
				case 2 -> "shell";
				default -> "column";
			};
		}

		private static SiegeDefinition resolveInternal(SiegeCampaignNode node, SiegeBattleTemplate template, SiegeBaseState state) {
			SiegeBattleVariant variant = resolveVariant(node, template, state);
			String displayName = node.displayName();
			String description = node.description();
			String enemySummary = template == null ? "Unknown" : template.enemySummary();
			String weaponSummary = template == null ? "Unknown" : template.weaponSummary();
			String threatSummary = template == null ? "Unknown" : template.threatSummary();
			int combatTier = template == null ? Math.max(0, node.ageLevel()) : template.difficultyTier();
			int waveSize = template == null ? 0 : template.baseWaveSize();
			int ramCount = template == null ? 0 : template.baseRamCount();
			if (variant != null) {
				displayName = node.displayName() + ": " + variant.displayName();
				description = variant.description();
				enemySummary = pickSummary(variant.enemySummary(), template.enemySummary());
				weaponSummary = pickSummary(variant.weaponSummary(), template.weaponSummary());
				threatSummary = pickSummary(variant.threatSummary(), template.threatSummary());
				waveSize = Math.max(0, waveSize + variant.waveDelta());
				ramCount = Math.max(0, ramCount + variant.ramDelta());
				combatTier = Math.max(0, combatTier + variant.difficultyDelta());
			}
			return new SiegeDefinition(
				node.id(),
				displayName,
				description,
				node.ageLevel(),
				node.routeColumn(),
				node.ageDefining(),
				node.requiredRegularWins(),
				node.routeRow(),
				combatTier,
				waveSize,
				ramCount,
				enemySummary,
				weaponSummary,
				threatSummary,
				node.warSuppliesReward(),
				node.minorRaid()
			);
		}

		private static SiegeBattleVariant resolveVariant(SiegeCampaignNode node, SiegeBattleTemplate template, SiegeBaseState state) {
			if (node == null || template == null || state == null || !node.minorRaid()) {
				return null;
			}
			List<SiegeBattleVariant> variants = template.variants().isEmpty() ? DEFAULT_MINOR_RAID_VARIANTS : template.variants();
			if (variants.isEmpty()) {
				return null;
			}
			int seed = state.getCompletedSieges() + node.routeColumn() + (state.getCurrentAgeRegularWins() * 2);
			int index = Math.floorMod(seed, variants.size());
			return variants.get(index);
		}

		private static void validateTemplate(SiegeBattleTemplate template, List<String> errors) {
			if (template.id() == null || template.id().isBlank()) {
				errors.add("Battle template has a blank id.");
			}
			if (template.spawnTier() < 0) {
				errors.add("Battle template '" + template.id() + "' has negative spawnTier.");
			}
			if (template.difficultyTier() < 0) {
				errors.add("Battle template '" + template.id() + "' has negative difficultyTier.");
			}
			if (template.baseWaveSize() < 0) {
				errors.add("Battle template '" + template.id() + "' has negative baseWaveSize.");
			}
			if (template.baseRamCount() < 0) {
				errors.add("Battle template '" + template.id() + "' has negative baseRamCount.");
			}
			if (template.formationId() == null || template.formationId().isBlank()) {
				errors.add("Battle template '" + template.id() + "' has a blank formationId.");
			} else if (!BattleFormationCatalog.hasDefinition(template.formationId())) {
				errors.add("Battle template '" + template.id() + "' references missing formation '" + template.formationId() + "'.");
			}
			for (SiegeUnitGroup group : template.unitGroups()) {
				if (group.id() == null || group.id().isBlank()) {
					errors.add("Battle template '" + template.id() + "' has a unit group with a blank id.");
				}
				if (group.unitType() == null || group.unitType().isBlank()) {
					errors.add("Battle template '" + template.id() + "' has unit group '" + group.id() + "' with a blank unitType.");
				}
				BattleUnitDefinition definition = BattleUnitCatalog.definition(group.unitType());
				if (definition == null) {
					errors.add("Battle template '" + template.id() + "' references missing unitType '" + group.unitType() + "' in group '" + group.id() + "'.");
				} else if (definition.spawnTier() > template.spawnTier()) {
					errors.add("Battle template '" + template.id() + "' spawnTier " + template.spawnTier() + " is lower than required by unit '" + group.unitType() + "' (" + definition.spawnTier() + ").");
				}
				if (group.count() < 0) {
					errors.add("Battle template '" + template.id() + "' has a unit group with negative count: " + group.id());
				}
				if (group.wallDamage() != null && group.wallDamage() < 0) {
					errors.add("Battle template '" + template.id() + "' has unit group '" + group.id() + "' with negative wallDamage.");
				}
			}
			for (SiegeEngineGroup group : template.engineGroups()) {
				if (group.id() == null || group.id().isBlank()) {
					errors.add("Battle template '" + template.id() + "' has an engine group with a blank id.");
				}
				if (group.engineType() == null || group.engineType().isBlank()) {
					errors.add("Battle template '" + template.id() + "' has engine group '" + group.id() + "' with a blank engineType.");
				}
				BattleUnitDefinition definition = BattleUnitCatalog.definition(group.engineType());
				if (definition == null) {
					errors.add("Battle template '" + template.id() + "' references missing engineType '" + group.engineType() + "' in group '" + group.id() + "'.");
				} else {
					if (!definition.isEngine()) {
						errors.add("Battle template '" + template.id() + "' engine group '" + group.id() + "' references non-engine unit '" + group.engineType() + "'.");
					}
					if (definition.spawnTier() > template.spawnTier()) {
						errors.add("Battle template '" + template.id() + "' spawnTier " + template.spawnTier() + " is lower than required by engine '" + group.engineType() + "' (" + definition.spawnTier() + ").");
					}
				}
				if (group.count() < 0) {
					errors.add("Battle template '" + template.id() + "' has an engine group with negative count: " + group.id());
				}
			}
			for (SiegeBattleVariant variant : template.variants()) {
				if (variant.id() == null || variant.id().isBlank()) {
					errors.add("Battle template '" + template.id() + "' has a variant with a blank id.");
				}
				if (template.baseWaveSize() + variant.waveDelta() < 0) {
					errors.add("Battle template '" + template.id() + "' variant '" + variant.id() + "' would create a negative wave size.");
				}
				if (template.baseRamCount() + variant.ramDelta() < 0) {
					errors.add("Battle template '" + template.id() + "' variant '" + variant.id() + "' would create a negative engine count.");
				}
			}
			boolean hasPrimaryBreachPath = false;
			boolean hasFallbackBreachPath = false;
			boolean hasEngineBreachPath = false;
			for (SiegeUnitGroup group : template.unitGroups()) {
				if (group.count() <= 0) {
					continue;
				}
				BattleUnitDefinition definition = BattleUnitCatalog.definition(group.unitType());
				BreachCapability capability = group.breachCapability() == BreachCapability.NONE
					? definition == null ? BreachCapability.NONE : definition.breachCapability()
					: group.breachCapability();
				if (capability == BreachCapability.PRIMARY) {
					hasPrimaryBreachPath = true;
				} else if (capability == BreachCapability.FALLBACK) {
					hasFallbackBreachPath = true;
				}
			}
			for (SiegeEngineGroup group : template.engineGroups()) {
				if (group.count() <= 0) {
					continue;
				}
				BattleUnitDefinition definition = BattleUnitCatalog.definition(group.engineType());
				if (definition != null && definition.isEngine()) {
					hasEngineBreachPath = true;
					break;
				}
			}
			if (!hasPrimaryBreachPath && !hasFallbackBreachPath && !hasEngineBreachPath) {
				errors.add("Battle template '" + template.id() + "' has no authored breach path. Add a primary breacher, fallback-capable unit, or siege engine.");
			}
		}

		private static void validateNode(SiegeCampaignNode node, List<String> errors) {
			if (node.id() == null || node.id().isBlank()) {
				errors.add("Campaign node has a blank id.");
			}
			if (node.displayName() == null || node.displayName().isBlank()) {
				errors.add("Campaign node '" + node.id() + "' has a blank displayName.");
			}
			if (node.description() == null || node.description().isBlank()) {
				errors.add("Campaign node '" + node.id() + "' has a blank description.");
			}
			if (node.ageLevel() < 0) {
				errors.add("Campaign node '" + node.id() + "' has a negative ageLevel.");
			}
			if (node.routeColumn() < 0) {
				errors.add("Campaign node '" + node.id() + "' has a negative routeColumn.");
			}
			if (node.routeRow() < 0) {
				errors.add("Campaign node '" + node.id() + "' has a negative routeRow.");
			}
			if (node.requiredRegularWins() < 0) {
				errors.add("Campaign node '" + node.id() + "' has a negative requiredRegularWins.");
			}
			if (node.warSuppliesReward() < 0) {
				errors.add("Campaign node '" + node.id() + "' has a negative warSuppliesReward.");
			}
			if (node.battleTemplateId() == null || node.battleTemplateId().isBlank()) {
				errors.add("Campaign node '" + node.id() + "' has a blank battleTemplateId.");
			}
		}

		private static String pickSummary(String variantValue, String fallback) {
			return variantValue == null || variantValue.isBlank() ? fallback : variantValue;
		}

		private static <T> List<T> safeList(List<T> values) {
			return values == null ? List.of() : values;
		}

		private static String normalizeId(String id) {
			return id == null ? null : id.trim();
		}

		private static String formatErrors(List<String> errors) {
			StringBuilder builder = new StringBuilder("Siege config validation failed:");
			for (String error : errors) {
				builder.append('\n').append(" - ").append(error);
			}
			return builder.toString();
		}
	}

	private static final class SiegeConfigRoot {
		Integer schemaVersion;
		String note;
		List<SiegeCampaignNodeJson> campaignNodes = List.of();
		List<SiegeBattleTemplateJson> battleTemplates = List.of();
		List<SiegeDefinitionJson> sieges = List.of();

		boolean hasAnyCampaignData() {
			return (campaignNodes != null && !campaignNodes.isEmpty()) || (battleTemplates != null && !battleTemplates.isEmpty());
		}
	}

	private static final class SiegeCampaignNodeJson {
		String id;
		String displayName;
		String description;
		int ageLevel;
		int routeColumn;
		int routeRow;
		boolean ageDefining;
		int requiredRegularWins;
		String battleTemplateId;
		int warSuppliesReward;
		boolean minorRaid;

		private SiegeCampaignNode toNode() {
			return new SiegeCampaignNode(
				id,
				displayName,
				description,
				ageLevel,
				routeColumn,
				routeRow,
				ageDefining,
				requiredRegularWins,
				battleTemplateId,
				warSuppliesReward,
				minorRaid
			);
		}

		private static SiegeCampaignNodeJson fromNode(SiegeCampaignNode node) {
			SiegeCampaignNodeJson json = new SiegeCampaignNodeJson();
			json.id = node.id();
			json.displayName = node.displayName();
			json.description = node.description();
			json.ageLevel = node.ageLevel();
			json.routeColumn = node.routeColumn();
			json.routeRow = node.routeRow();
			json.ageDefining = node.ageDefining();
			json.requiredRegularWins = node.requiredRegularWins();
			json.battleTemplateId = node.battleTemplateId();
			json.warSuppliesReward = node.warSuppliesReward();
			json.minorRaid = node.minorRaid();
			return json;
		}
	}

	private static final class SiegeBattleTemplateJson {
		String id;
		int spawnTier;
		int difficultyTier;
		int baseWaveSize;
		int baseRamCount;
		String formationId;
		String enemySummary;
		String weaponSummary;
		String threatSummary;
		List<SiegeUnitGroupJson> unitGroups = List.of();
		List<SiegeEngineGroupJson> engineGroups = List.of();
		List<SiegeBattleVariantJson> variants = List.of();

		private SiegeBattleTemplate toTemplate() {
			List<SiegeUnitGroup> convertedUnits = new ArrayList<>();
			for (SiegeUnitGroupJson json : CatalogState.safeList(unitGroups)) {
				convertedUnits.add(json.toGroup());
			}
			List<SiegeEngineGroup> convertedEngines = new ArrayList<>();
			for (SiegeEngineGroupJson json : CatalogState.safeList(engineGroups)) {
				convertedEngines.add(json.toGroup());
			}
			List<SiegeBattleVariant> convertedVariants = new ArrayList<>();
			for (SiegeBattleVariantJson json : CatalogState.safeList(variants)) {
				convertedVariants.add(json.toVariant());
			}
			return new SiegeBattleTemplate(
				id,
				spawnTier,
				difficultyTier,
				baseWaveSize,
				baseRamCount,
				formationId,
				enemySummary,
				weaponSummary,
				threatSummary,
				convertedUnits,
				convertedEngines,
				convertedVariants
			);
		}

		private static SiegeBattleTemplateJson fromTemplate(SiegeBattleTemplate template) {
			SiegeBattleTemplateJson json = new SiegeBattleTemplateJson();
			json.id = template.id();
			json.spawnTier = template.spawnTier();
			json.difficultyTier = template.difficultyTier();
			json.baseWaveSize = template.baseWaveSize();
			json.baseRamCount = template.baseRamCount();
			json.formationId = template.formationId();
			json.enemySummary = template.enemySummary();
			json.weaponSummary = template.weaponSummary();
			json.threatSummary = template.threatSummary();
			List<SiegeUnitGroupJson> unitGroups = new ArrayList<>();
			for (SiegeUnitGroup group : template.unitGroups()) {
				unitGroups.add(SiegeUnitGroupJson.fromGroup(group));
			}
			json.unitGroups = List.copyOf(unitGroups);
			List<SiegeEngineGroupJson> engineGroups = new ArrayList<>();
			for (SiegeEngineGroup group : template.engineGroups()) {
				engineGroups.add(SiegeEngineGroupJson.fromGroup(group));
			}
			json.engineGroups = List.copyOf(engineGroups);
			List<SiegeBattleVariantJson> variants = new ArrayList<>();
			for (SiegeBattleVariant variant : template.variants()) {
				variants.add(SiegeBattleVariantJson.fromVariant(variant));
			}
			json.variants = List.copyOf(variants);
			return json;
		}
	}

	private static final class SiegeBattleVariantJson {
		String id;
		String displayName;
		String description;
		int waveDelta;
		int ramDelta;
		int difficultyDelta;
		String enemySummary;
		String weaponSummary;
		String threatSummary;

		private SiegeBattleVariant toVariant() {
			return new SiegeBattleVariant(
				id,
				displayName,
				description,
				waveDelta,
				ramDelta,
				difficultyDelta,
				enemySummary,
				weaponSummary,
				threatSummary
			);
		}

		private static SiegeBattleVariantJson fromVariant(SiegeBattleVariant variant) {
			SiegeBattleVariantJson json = new SiegeBattleVariantJson();
			json.id = variant.id();
			json.displayName = variant.displayName();
			json.description = variant.description();
			json.waveDelta = variant.waveDelta();
			json.ramDelta = variant.ramDelta();
			json.difficultyDelta = variant.difficultyDelta();
			json.enemySummary = variant.enemySummary();
			json.weaponSummary = variant.weaponSummary();
			json.threatSummary = variant.threatSummary();
			return json;
		}
	}

	private static final class SiegeUnitGroupJson {
		String id;
		String unitType;
		String entityType;
		String role;
		int count;
		String loadout;
		List<String> tags = List.of();
		String breachCapability;
		Integer wallDamage;

		private SiegeUnitGroup toGroup() {
			return new SiegeUnitGroup(id, unitType, entityType, role, count, loadout, tags, BreachCapability.parse(breachCapability), wallDamage);
		}

		private static SiegeUnitGroupJson fromGroup(SiegeUnitGroup group) {
			SiegeUnitGroupJson json = new SiegeUnitGroupJson();
			json.id = group.id();
			json.unitType = group.unitType();
			json.entityType = group.entityType();
			json.role = group.role();
			json.count = group.count();
			json.loadout = group.loadout();
			json.tags = group.tags();
			json.breachCapability = group.breachCapability().name().toLowerCase();
			json.wallDamage = group.wallDamage();
			return json;
		}
	}

	private static final class SiegeEngineGroupJson {
		String id;
		String engineType;
		String entityType;
		int count;
		List<String> tags = List.of();

		private SiegeEngineGroup toGroup() {
			return new SiegeEngineGroup(id, engineType, entityType, count, tags);
		}

		private static SiegeEngineGroupJson fromGroup(SiegeEngineGroup group) {
			SiegeEngineGroupJson json = new SiegeEngineGroupJson();
			json.id = group.id();
			json.engineType = group.engineType();
			json.entityType = group.entityType();
			json.count = group.count();
			json.tags = group.tags();
			return json;
		}
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
