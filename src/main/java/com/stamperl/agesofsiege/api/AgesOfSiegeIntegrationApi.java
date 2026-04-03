package com.stamperl.agesofsiege.api;

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
}
