package com.stamperl.agesofsiege.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.runtime.BattleLane;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class BattleFormationCatalog {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String BUNDLED_DEFAULT_PATH = "/defaults/ages_of_siege/formations.json";
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("ages_of_siege")
		.resolve("formations.json");
	private static volatile CatalogState catalogState = CatalogState.empty();

	private BattleFormationCatalog() {
	}

	public static synchronized void initialize() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.notExists(CONFIG_PATH)) {
				copyBundledDefaultConfig(CONFIG_PATH);
				AgesOfSiegeMod.LOGGER.info("Copied bundled formation config to {}", CONFIG_PATH);
			}
			catalogState = loadConfig(CONFIG_PATH);
			AgesOfSiegeMod.LOGGER.info("Loaded {} battle formations from {}", catalogState.definitions().size(), CONFIG_PATH);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to load formation config from " + CONFIG_PATH, exception);
		}
	}

	public static SiegeFormationDefinition definition(String id) {
		return catalogState.definition(id);
	}

	public static boolean hasDefinition(String id) {
		return definition(id) != null;
	}

	private static CatalogState loadConfig(Path configPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(configPath)) {
			FormationsRoot root = GSON.fromJson(reader, FormationsRoot.class);
			if (root == null) {
				throw new IOException("Formation config is empty or unreadable.");
			}
			return CatalogState.fromRoot(root);
		}
	}

	private static void copyBundledDefaultConfig(Path configPath) throws IOException {
		try (InputStream input = BattleFormationCatalog.class.getResourceAsStream(BUNDLED_DEFAULT_PATH)) {
			if (input == null) {
				throw new IOException("Bundled default formation config is missing: " + BUNDLED_DEFAULT_PATH);
			}
			Files.copy(input, configPath);
		}
	}

	private record CatalogState(
		List<SiegeFormationDefinition> definitions,
		Map<String, SiegeFormationDefinition> byId
	) {
		private static final int CURRENT_SCHEMA_VERSION = 1;

		private static CatalogState empty() {
			return new CatalogState(List.of(), Map.of());
		}

		private static CatalogState fromRoot(FormationsRoot root) throws IOException {
			if (root.schemaVersion != null && root.schemaVersion != CURRENT_SCHEMA_VERSION) {
				throw new IOException("Unsupported formation schema version " + root.schemaVersion + ". Expected " + CURRENT_SCHEMA_VERSION + ".");
			}
			List<String> errors = new ArrayList<>();
			List<SiegeFormationDefinition> definitions = new ArrayList<>();
			Map<String, SiegeFormationDefinition> byId = new LinkedHashMap<>();
			for (SiegeFormationDefinitionJson json : safeList(root.formations)) {
				SiegeFormationDefinition definition = json.toDefinition();
				validateDefinition(definition, errors);
				if (byId.putIfAbsent(definition.id(), definition) != null) {
					errors.add("Duplicate formation id: " + definition.id());
				} else {
					definitions.add(definition);
				}
			}
			if (definitions.isEmpty()) {
				errors.add("No formations defined.");
			}
			if (!errors.isEmpty()) {
				throw new IOException(formatErrors(errors));
			}
			return new CatalogState(List.copyOf(definitions), Map.copyOf(byId));
		}

		private SiegeFormationDefinition definition(String id) {
			return id == null ? null : byId.get(id.trim());
		}

		private static void validateDefinition(SiegeFormationDefinition definition, List<String> errors) {
			if (definition.id().isBlank()) {
				errors.add("Formation has a blank id.");
			}
			if (definition.lanes().isEmpty()) {
				errors.add("Formation '" + definition.id() + "' has no lane definitions.");
			}
			Map<BattleLane, Integer> laneCounts = new LinkedHashMap<>();
			for (SiegeFormationLaneDefinition lane : definition.lanes()) {
				if (lane.rowWidth() <= 0) {
					errors.add("Formation '" + definition.id() + "' has lane '" + lane.lane() + "' with non-positive rowWidth.");
				}
				laneCounts.merge(lane.lane(), 1, Integer::sum);
			}
			laneCounts.forEach((lane, count) -> {
				if (count > 1) {
					errors.add("Formation '" + definition.id() + "' defines lane '" + lane + "' more than once.");
				}
			});
		}

		private static <T> List<T> safeList(List<T> values) {
			return values == null ? List.of() : values;
		}

		private static String formatErrors(List<String> errors) {
			StringBuilder builder = new StringBuilder("Formation config validation failed:");
			for (String error : errors) {
				builder.append('\n').append(" - ").append(error);
			}
			return builder.toString();
		}
	}

	private static final class FormationsRoot {
		Integer schemaVersion;
		String note;
		List<SiegeFormationDefinitionJson> formations = List.of();
	}

	private static final class SiegeFormationDefinitionJson {
		String id;
		String displayName;
		List<SiegeFormationLaneJson> lanes = List.of();

		private SiegeFormationDefinition toDefinition() {
			List<SiegeFormationLaneDefinition> converted = new ArrayList<>();
			for (SiegeFormationLaneJson lane : lanes == null ? List.<SiegeFormationLaneJson>of() : lanes) {
				converted.add(lane.toDefinition());
			}
			return new SiegeFormationDefinition(id, displayName, converted);
		}
	}

	private static final class SiegeFormationLaneJson {
		String lane;
		double lateralOffset;
		double depthOffset;
		double lateralStep;
		double depthStep;
		int rowWidth = 1;
		List<String> preferredTags = List.of();

		private SiegeFormationLaneDefinition toDefinition() {
			return new SiegeFormationLaneDefinition(parseLane(lane), lateralOffset, depthOffset, lateralStep, depthStep, rowWidth, preferredTags);
		}

		private static BattleLane parseLane(String laneId) {
			if (laneId == null || laneId.isBlank()) {
				return BattleLane.CENTER;
			}
			return switch (laneId.trim().toLowerCase(Locale.ROOT)) {
				case "front" -> BattleLane.FRONT;
				case "rear" -> BattleLane.REAR;
				case "left_flank", "leftflank" -> BattleLane.LEFT_FLANK;
				case "right_flank", "rightflank" -> BattleLane.RIGHT_FLANK;
				case "engine" -> BattleLane.ENGINE;
				default -> BattleLane.CENTER;
			};
		}
	}
}
