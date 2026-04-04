package com.stamperl.agesofsiege.api;

import com.stamperl.agesofsiege.siege.service.ObjectiveService;
import com.stamperl.agesofsiege.state.SharedTreasuryState;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class AgesOfSiegeIntegrationApi {
	public static final String API_KEY = "ages_of_siege:integration_api";
	public static final String AGE_SNAPSHOT_KEY = "age_snapshot";
	public static final String TREASURY_SNAPSHOT_KEY = "treasury_snapshot";
	public static final String DEPOSIT_COIN_KEY = "deposit_coin";
	public static final String CREDIT_TREASURY_KEY = "credit_treasury";
	public static final String SPEND_TREASURY_KEY = "spend_treasury";
	public static final String SET_TREASURY_KEY = "set_treasury";
	public static final String SET_BANK_POSITION_KEY = "set_bank_position";
	public static final String CLEAR_BANK_POSITION_KEY = "clear_bank_position";
	public static final String TRY_TRACK_BANK_KEY = "try_track_bank";

	private AgesOfSiegeIntegrationApi() {
	}

	public static void register() {
		Map<String, Object> api = new HashMap<>();
		api.put(AGE_SNAPSHOT_KEY, (Function<MinecraftServer, NbtCompound>) AgesOfSiegeIntegrationApi::ageSnapshot);
		api.put(TREASURY_SNAPSHOT_KEY, (Function<MinecraftServer, NbtCompound>) AgesOfSiegeIntegrationApi::treasurySnapshot);
		api.put(DEPOSIT_COIN_KEY, (BiConsumer<MinecraftServer, NbtCompound>) AgesOfSiegeIntegrationApi::depositCoin);
		api.put(CREDIT_TREASURY_KEY, (BiConsumer<MinecraftServer, Long>) AgesOfSiegeIntegrationApi::creditTreasury);
		api.put(SPEND_TREASURY_KEY, (BiFunction<MinecraftServer, Long, Boolean>) AgesOfSiegeIntegrationApi::spendTreasury);
		api.put(SET_TREASURY_KEY, (BiConsumer<MinecraftServer, Long>) AgesOfSiegeIntegrationApi::setTreasuryBalance);
		api.put(SET_BANK_POSITION_KEY, (BiConsumer<MinecraftServer, NbtCompound>) AgesOfSiegeIntegrationApi::setBankPosition);
		api.put(CLEAR_BANK_POSITION_KEY, (BiConsumer<MinecraftServer, NbtCompound>) AgesOfSiegeIntegrationApi::clearBankPosition);
		api.put(TRY_TRACK_BANK_KEY, (BiFunction<MinecraftServer, NbtCompound, Boolean>) AgesOfSiegeIntegrationApi::tryTrackBankPosition);
		FabricLoader.getInstance().getObjectShare().put(API_KEY, api);
	}

	private static NbtCompound ageSnapshot(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		NbtCompound snapshot = new NbtCompound();
		snapshot.putInt("ageLevel", state.getAgeLevel());
		snapshot.putString("ageName", state.getAgeName());
		snapshot.putInt("completedSieges", state.getCompletedSieges());
		return snapshot;
	}

	private static NbtCompound treasurySnapshot(MinecraftServer server) {
		return SharedTreasuryState.get(server).toNbtSnapshot();
	}

	private static void depositCoin(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return;
		}
		String coinKey = request.getString("coinKey");
		int coinValue = request.getInt("coinValue");
		int count = request.getInt("count");
		SharedTreasuryState.get(server).depositCoins(coinKey, coinValue, count);
	}

	private static void creditTreasury(MinecraftServer server, Long amount) {
		if (amount == null) {
			return;
		}
		SharedTreasuryState.get(server).credit(amount);
	}

	private static Boolean spendTreasury(MinecraftServer server, Long amount) {
		if (amount == null) {
			return Boolean.FALSE;
		}
		return SharedTreasuryState.get(server).spend(amount);
	}

	private static void setTreasuryBalance(MinecraftServer server, Long amount) {
		if (amount == null) {
			return;
		}
		SharedTreasuryState.get(server).setBalance(amount);
	}

	private static void setBankPosition(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return;
		}
		SiegeBaseState.get(server).setTrackedBank(
			new net.minecraft.util.math.BlockPos(request.getInt("x"), request.getInt("y"), request.getInt("z")),
			request.getString("dimension"),
			request.contains("protectionCap") ? request.getInt("protectionCap") : 100
		);
	}

	private static void clearBankPosition(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return;
		}
		SiegeBaseState.get(server).clearTrackedBankIfMatches(
			new net.minecraft.util.math.BlockPos(request.getInt("x"), request.getInt("y"), request.getInt("z")),
			request.getString("dimension")
		);
	}

	private static Boolean tryTrackBankPosition(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return Boolean.FALSE;
		}
		SiegeBaseState state = SiegeBaseState.get(server);
		net.minecraft.util.math.BlockPos requestedPos = new net.minecraft.util.math.BlockPos(
			request.getInt("x"),
			request.getInt("y"),
			request.getInt("z")
		);
		String dimensionId = request.getString("dimension");
		int protectionCap = request.contains("protectionCap") ? request.getInt("protectionCap") : 100;
		if (!state.hasTrackedBank()) {
			state.setTrackedBank(requestedPos, dimensionId, protectionCap);
			return Boolean.TRUE;
		}
		if (state.isTrackedBankAt(requestedPos, dimensionId)) {
			return Boolean.TRUE;
		}
		net.minecraft.util.Identifier trackedDimension = net.minecraft.util.Identifier.tryParse(state.getTrackedBankDimensionId());
		net.minecraft.server.world.ServerWorld trackedWorld = trackedDimension == null ? null : server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, trackedDimension));
		ObjectiveService objectiveService = new ObjectiveService();
		if (trackedWorld == null || !objectiveService.isTrackedBankPresent(trackedWorld, state)) {
			state.clearTrackedBank();
			state.setTrackedBank(requestedPos, dimensionId, protectionCap);
			return Boolean.TRUE;
		}
		return Boolean.FALSE;
	}
}
