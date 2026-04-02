package com.stamperl.agesofsiege.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BattleUnitCatalog {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String BUNDLED_DEFAULT_PATH = "/defaults/ages_of_siege/battle_units.json";
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("ages_of_siege")
		.resolve("battle_units.json");
	private static volatile CatalogState catalogState = CatalogState.empty();

	private BattleUnitCatalog() {
	}

	public static synchronized void initialize() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.notExists(CONFIG_PATH)) {
				copyBundledDefaultConfig(CONFIG_PATH);
				AgesOfSiegeMod.LOGGER.info("Copied bundled battle unit config to {}", CONFIG_PATH);
			}
			catalogState = loadConfig(CONFIG_PATH);
			AgesOfSiegeMod.LOGGER.info("Loaded {} battle unit definitions from {}", catalogState.definitions().size(), CONFIG_PATH);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to load battle unit config from " + CONFIG_PATH, exception);
		}
	}

	public static BattleUnitDefinition definition(String id) {
		return catalogState.definition(id);
	}

	public static boolean hasDefinition(String id) {
		return definition(id) != null;
	}

	private static CatalogState loadConfig(Path configPath) throws IOException {
		try (Reader reader = Files.newBufferedReader(configPath)) {
			BattleUnitsRoot root = GSON.fromJson(reader, BattleUnitsRoot.class);
			if (root == null) {
				throw new IOException("Battle unit config is empty or unreadable.");
			}
			return CatalogState.fromRoot(root);
		}
	}

	private static void copyBundledDefaultConfig(Path configPath) throws IOException {
		try (InputStream input = BattleUnitCatalog.class.getResourceAsStream(BUNDLED_DEFAULT_PATH)) {
			if (input == null) {
				throw new IOException("Bundled default battle unit config is missing: " + BUNDLED_DEFAULT_PATH);
			}
			Files.copy(input, configPath);
		}
	}

	private record CatalogState(
		List<BattleUnitDefinition> definitions,
		Map<String, BattleUnitDefinition> byId
	) {
		private static final int CURRENT_SCHEMA_VERSION = 2;

		private static CatalogState empty() {
			return new CatalogState(List.of(), Map.of());
		}

		private static CatalogState fromRoot(BattleUnitsRoot root) throws IOException {
			if (root.schemaVersion != null && root.schemaVersion != CURRENT_SCHEMA_VERSION) {
				throw new IOException("Unsupported battle unit schema version " + root.schemaVersion + ". Expected " + CURRENT_SCHEMA_VERSION + ".");
			}
			List<String> errors = new ArrayList<>();
			List<BattleUnitDefinition> definitions = new ArrayList<>();
			Map<String, BattleUnitDefinition> byId = new LinkedHashMap<>();
			for (BattleUnitDefinitionJson json : safeList(root.units)) {
				BattleUnitDefinition definition = json.toDefinition();
				validateDefinition(definition, errors);
				if (byId.putIfAbsent(definition.id(), definition) != null) {
					errors.add("Duplicate battle unit id: " + definition.id());
				} else {
					definitions.add(definition);
				}
			}
			if (definitions.isEmpty()) {
				errors.add("No battle unit definitions found.");
			}
			if (!errors.isEmpty()) {
				throw new IOException(formatErrors(errors));
			}
			return new CatalogState(List.copyOf(definitions), Map.copyOf(byId));
		}

		private BattleUnitDefinition definition(String id) {
			return id == null ? null : byId.get(id.trim());
		}

		private static void validateDefinition(BattleUnitDefinition definition, List<String> errors) {
			if (definition.id().isBlank()) {
				errors.add("Battle unit has a blank id.");
			}
			if (definition.entityType().isBlank()) {
				errors.add("Battle unit '" + definition.id() + "' has a blank entityType.");
			} else if (Identifier.tryParse(definition.entityType()) == null) {
				errors.add("Battle unit '" + definition.id() + "' has invalid entityType '" + definition.entityType() + "'.");
			}
			if (definition.defaultRole().isBlank()) {
				errors.add("Battle unit '" + definition.id() + "' has a blank defaultRole.");
			}
			if (definition.spawnTier() < 0) {
				errors.add("Battle unit '" + definition.id() + "' has negative spawnTier.");
			}
			if (definition.wallDamage() < 0) {
				errors.add("Battle unit '" + definition.id() + "' has negative wallDamage.");
			}
		}

		private static <T> List<T> safeList(List<T> values) {
			return values == null ? List.of() : values;
		}

		private static String formatErrors(List<String> errors) {
			StringBuilder builder = new StringBuilder("Battle unit config validation failed:");
			for (String error : errors) {
				builder.append('\n').append(" - ").append(error);
			}
			return builder.toString();
		}
	}

	private static final class BattleUnitsRoot {
		Integer schemaVersion;
		String note;
		List<BattleUnitDefinitionJson> units = List.of();
	}

	private static final class BattleUnitDefinitionJson {
		String id;
		String displayName;
		String entityType;
		String defaultRole;
		String loadoutProfile;
		List<String> tags = List.of();
		int spawnTier;
		String breachCapability;
		Boolean canFallbackBreach;
		int wallDamage;

		private BattleUnitDefinition toDefinition() {
			BreachCapability capability = BreachCapability.parse(breachCapability);
			if (capability == BreachCapability.NONE && Boolean.TRUE.equals(canFallbackBreach)) {
				capability = BreachCapability.FALLBACK;
			}
			return new BattleUnitDefinition(id, displayName, entityType, defaultRole, loadoutProfile, tags, spawnTier, capability, wallDamage);
		}
	}
}
