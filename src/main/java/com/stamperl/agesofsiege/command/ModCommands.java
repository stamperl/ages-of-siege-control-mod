package com.stamperl.agesofsiege.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.stamperl.agesofsiege.siege.BlockHpExporter;
import com.stamperl.agesofsiege.siege.ItemRegistryExporter;
import com.stamperl.agesofsiege.siege.SiegeManager;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ModCommands {
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
			.then(CommandManager.literal("setage")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("level", IntegerArgumentType.integer(0, 3))
					.executes(context -> setAgeLevel(
						context.getSource().getPlayerOrThrow(),
						IntegerArgumentType.getInteger(context, "level")
					))))
			.then(CommandManager.literal("startsiege")
				.executes(context -> startSiege(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("endsiege")
				.executes(context -> endSiege(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("dumpblocks")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(context -> dumpBlocks(context.getSource())))
			.then(CommandManager.literal("dumpitems")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(context -> dumpItems(context.getSource())))
			.then(CommandManager.literal("clearbase")
				.executes(context -> clearBase(context.getSource().getPlayerOrThrow()))));
	}

	private static int showStatus(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!state.hasBase()) {
			player.sendMessage(Text.literal("No siege base is set yet. Build normally, then claim one when you're ready.")
				.formatted(Formatting.YELLOW), false);
			return 1;
		}

		player.sendMessage(Text.literal(state.describe()).formatted(Formatting.GREEN), false);
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

	private static int startSiege(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!state.hasBase()) {
			player.sendMessage(Text.literal("Place a Settlement Standard before starting a siege.")
				.formatted(Formatting.YELLOW), false);
			return 0;
		}

		if (!SiegeManager.startSiege(player.getServer(), state)) {
			player.sendMessage(Text.literal("Could not start the siege. Make sure the objective exists and no siege is already active.")
				.formatted(Formatting.RED), false);
			return 0;
		}

		player.sendMessage(Text.literal("Siege countdown started. Hold the line when the attackers arrive.")
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

	private static int clearBase(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.clearBase();
		player.sendMessage(Text.literal("Siege base cleared.").formatted(Formatting.RED), false);
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
