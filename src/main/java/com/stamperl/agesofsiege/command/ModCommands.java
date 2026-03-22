package com.stamperl.agesofsiege.command;

import com.mojang.brigadier.CommandDispatcher;
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
			.then(CommandManager.literal("startsiege")
				.executes(context -> startSiege(context.getSource().getPlayerOrThrow())))
			.then(CommandManager.literal("endsiege")
				.executes(context -> endSiege(context.getSource().getPlayerOrThrow())))
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

		state.startSiege(player.getServer());
		player.sendMessage(Text.literal("Test siege started. If the Settlement Standard is destroyed, the siege fails.")
			.formatted(Formatting.GOLD), false);
		return 1;
	}

	private static int endSiege(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.endSiege(false);
		player.sendMessage(Text.literal("Active siege cleared.").formatted(Formatting.GREEN), false);
		return 1;
	}

	private static int clearBase(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.clearBase();
		player.sendMessage(Text.literal("Siege base cleared.").formatted(Formatting.RED), false);
		return 1;
	}
}
