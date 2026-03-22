package com.stamperl.agesofsiege.siege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class BlockHpExporter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private BlockHpExporter() {
	}

	public static Path export(MinecraftServer server) throws IOException {
		Path outputDir = server.getRunDirectory().toPath()
			.resolve("config")
			.resolve("ages_of_siege");
		Path outputPath = outputDir.resolve("block_hp.generated.json");

		Files.createDirectories(outputDir);

		JsonObject root = new JsonObject();
		root.addProperty("defaultHitPoints", 50);
		root.addProperty("note", "Generated from the live block registry. Edit values as needed, especially for custom or modded wall blocks.");

		JsonObject blocks = new JsonObject();
		List<Identifier> blockIds = Registries.BLOCK.getIds().stream()
			.sorted(Comparator.comparing(Identifier::toString))
			.toList();

		for (Identifier id : blockIds) {
			Block block = Registries.BLOCK.get(id);
			blocks.addProperty(id.toString(), suggestHitPoints(id));
		}

		root.add("blocks", blocks);

		try (Writer writer = Files.newBufferedWriter(outputPath)) {
			GSON.toJson(root, writer);
		}

		return outputPath;
	}

	private static int suggestHitPoints(Identifier id) {
		String path = id.getPath();

		if (containsAny(path, "obsidian", "crying_obsidian", "reinforced_deepslate", "ancient_debris", "netherite")) {
			return 120;
		}

		if (containsAny(path, "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block",
			"redstone_block", "copper_block", "waxed_", "raw_iron_block", "raw_gold_block", "raw_copper_block")) {
			return 80;
		}

		if (containsAny(path, "stone_brick", "deepslate_brick", "deepslate_tile", "cobblestone", "stone", "brick",
			"blackstone", "sandstone", "prismarine", "quartz", "purpur", "end_stone", "basalt", "tuff")) {
			return 35;
		}

		if (containsAny(path, "planks", "_log", "_wood", "_stem", "hyphae", "stripped_", "ladder", "bookshelf",
			"barrel", "chest", "crafting_table", "lectern", "fletching_table", "cartography_table", "smithing_table",
			"loom", "composter")) {
			return 15;
		}

		if (containsAny(path, "_door", "_trapdoor", "_fence", "_fence_gate", "_slab", "_stairs", "_button",
			"_pressure_plate", "_wall_sign", "_sign", "_hanging_sign")) {
			return 10;
		}

		if (containsAny(path, "wool", "carpet", "glass", "pane", "leaves", "hay_block", "target", "torch")) {
			return 5;
		}

		return 50;
	}

	private static boolean containsAny(String value, String... needles) {
		for (String needle : needles) {
			if (value.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
