package com.stamperl.agesofsiege.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.defense.DefenderSpawnerService;
import com.stamperl.agesofsiege.siege.BlockHpExporter;
import com.stamperl.agesofsiege.siege.ItemRegistryExporter;
import com.stamperl.agesofsiege.siege.SiegeDebug;
import com.stamperl.agesofsiege.siege.SiegeDirector;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ModCommands {
	private static final DefenderSpawnerService DEFENDER_SPAWNER = new DefenderSpawnerService();

	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("agesofsiege")
			.then(CommandManager.literal("status")
				.executes(context -> showStatus(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("setbase")
				.executes(context -> setBaseAtPlayer(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("setrally")
				.executes(context -> setRallyAtPlayer(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("setage")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("level", IntegerArgumentType.integer(0, 3))
					.executes(context -> setAgeLevel(
						context.getSource().getPlayerOrThrow(),
						IntegerArgumentType.getInteger(context, "level")
					))))
			.then(CommandManager.literal("startsiege")
				.executes(context -> startSiege(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("debugpath")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("enabled", BoolArgumentType.bool())
					.executes(context -> setDebugPath(
						context.getSource().getPlayerOrThrow(),
						BoolArgumentType.getBool(context, "enabled")
					))))
			.then(CommandManager.literal("spawndefenders")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("role", StringArgumentType.word())
					.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 16))
						.executes(context -> spawnDefenders(
							context.getSource().getPlayerOrThrow(),
							StringArgumentType.getString(context, "role"),
							IntegerArgumentType.getInteger(context, "count")
						)))))
			.then(CommandManager.literal("devtokens")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(context -> giveDevTokens(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("endsiege")
				.executes(context -> endSiege(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("dumpblocks")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(context -> dumpBlocks(context.getSource())))
			.then(CommandManager.literal("dumpitems")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(context -> dumpItems(context.getSource())))
			.then(CommandManager.literal("clearrally")
				.executes(context -> clearRally(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("clearbase")
				.executes(context -> clearBase(context.getSource().getPlayerOrThrow()))));
	}

	private static int showStatus(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!state.hasBase()) {
			String rallyText = state.getRallyPoint() == null
				? "No raid rally point is set."
				: "Current raid rally point: " + state.getRallyPoint().toShortString() + ".";
			player.sendMessage(Text.literal("No siege base is set yet. Build normally, then claim one when you're ready. " + rallyText)
				.formatted(Formatting.YELLOW), false);
			return 1;
		}

		player.sendMessage(Text.literal(state.describe()).formatted(Formatting.GREEN), false);
		player.sendMessage(Text.literal(state.describeDefenders()).formatted(Formatting.AQUA), false);
		return 1;
	}

	private static int setBaseAtPlayer(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.setBase(
			player.getBlockPos(),
			player.getWorld().getRegistryKey().getValue().toString(),
			player.getGameProfile().getName()
		);
		player.sendMessage(Text.literal("Siege base set at your current position.").formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int setRallyAtPlayer(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.setRallyPoint(player.getBlockPos());
		player.sendMessage(Text.literal("Raid rally point set at your current position.").formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int startSiege(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!state.hasBase()) {
			player.sendMessage(Text.literal("Place a Settlement Standard before starting a siege.")
				.formatted(Formatting.YELLOW), false);
			return 0;
		}

		if (!SiegeDirector.startSiege(player.getServer(), state)) {
			player.sendMessage(Text.literal("Could not start the siege. Make sure the objective and rally banner exist and no siege is already active.")
				.formatted(Formatting.RED), false);
			return 0;
		}

		player.sendMessage(Text.literal("Siege started from the selected operation. Hold the line.")
			.formatted(Formatting.GOLD), false);
		return 1;
	}

	private static int setAgeLevel(ServerPlayerEntity player, int ageLevel) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.setTestingAgeLevel(ageLevel);
		player.sendMessage(
			Text.literal("Testing age set to " + state.getAgeName() + " (" + state.getAgeLevel() + ").")
				.formatted(Formatting.GREEN),
			false
		);
		return 1;
	}

	private static int endSiege(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.endSiege(false, false);
		player.sendMessage(Text.literal("Active siege cleared.").formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int setDebugPath(ServerPlayerEntity player, boolean enabled) {
		SiegeDebug.setPathRenderEnabled(enabled);
		player.sendMessage(
			Text.literal("Siege path debug render " + (enabled ? "enabled" : "disabled") + ".")
				.formatted(enabled ? Formatting.GREEN : Formatting.YELLOW),
			false
		);
		return 1;
	}

	private static int spawnDefenders(ServerPlayerEntity player, String role, int count) {
		return DEFENDER_SPAWNER.spawnDefenders(player, role, count);
	}

	private static int giveDevTokens(ServerPlayerEntity player) {
		player.giveItemStack(new ItemStack(ModItems.ARMY_LEDGER));
		player.giveItemStack(new ItemStack(ModItems.ARCHER_TOKEN, 8));
		player.giveItemStack(new ItemStack(ModItems.SOLDIER_TOKEN, 8));
		player.giveItemStack(new ItemStack(ModItems.DEFENDER_RECALL_TOOL));
		player.sendMessage(Text.literal("Granted an Army Ledger, 8 Archer Tokens, 8 Soldier Tokens, and a Defender Recall Tool.").formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int clearBase(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.clearBase();
		player.sendMessage(Text.literal("Siege base cleared.").formatted(Formatting.RED), false);
		return 1;
	}

	private static int clearRally(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.setRallyPoint(null);
		player.sendMessage(Text.literal("Raid rally point cleared.").formatted(Formatting.RED), false);
		return 1;
	}

	private static int dumpBlocks(ServerCommandSource source) {
		try {
			String outputPath = BlockHpExporter.export(source.getServer()).toString();
			source.sendFeedback(() -> Text.literal("Exported loaded block ids to " + outputPath)
				.formatted(Formatting.GREEN), false);
			return 1;
		} catch (Exception exception) {
			source.sendError(Text.literal("Could not export block HP seed file: " + exception.getMessage()));
			return 0;
		}
	}

	private static int dumpItems(ServerCommandSource source) {
		try {
			String outputPath = ItemRegistryExporter.export(source.getServer()).toString();
			source.sendFeedback(() -> Text.literal("Exported loaded item ids to " + outputPath)
				.formatted(Formatting.GREEN), false);
			return 1;
		} catch (Exception exception) {
			source.sendError(Text.literal("Could not export item registry seed file: " + exception.getMessage()));
			return 0;
		}
	}
}
