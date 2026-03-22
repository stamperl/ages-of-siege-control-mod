package com.stamperl.agesofsiege.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ItemRegistryExporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ItemRegistryExporter() {
	}

	public static Path export(MinecraftServer server) throws IOException {
		Path outputDir = server.getRunDirectory().toPath()
			.resolve("config")
			.resolve("ages_of_siege");
		Path outputPath = outputDir.resolve("item_registry.generated.json");

		Files.createDirectories(outputDir);

		JsonObject root = new JsonObject();
		root.addProperty("note", "Generated from the live item registry. Use this to build age loadouts, reward tables, and NPC equipment pools.");

		JsonArray allItems = new JsonArray();
		JsonArray epicKnightsItems = new JsonArray();

		List<Identifier> itemIds = Registries.ITEM.getIds().stream()
			.sorted(Comparator.comparing(Identifier::toString))
			.toList();

		for (Identifier id : itemIds) {
			Item item = Registries.ITEM.get(id);
			JsonObject entry = new JsonObject();
			entry.addProperty("id", id.toString());
			entry.addProperty("namespace", id.getNamespace());
			entry.addProperty("path", id.getPath());
			allItems.add(entry);

			if ("epic_knights".equals(id.getNamespace())) {
				epicKnightsItems.add(entry.deepCopy());
			}
		}

		root.add("allItems", allItems);
		root.add("epicKnightsItems", epicKnightsItems);

		try (Writer writer = Files.newBufferedWriter(outputPath)) {
			GSON.toJson(root, writer);
		}

		return outputPath;
	}
}
