package com.stamperl.agesofsiege.api;

import com.stamperl.agesofsiege.siege.service.ObjectiveService;
import com.stamperl.agesofsiege.state.SharedTreasuryState;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class AgesOfSiegeIntegrationApi {
	public static final String API_KEY = "ages_of_siege:integration_api";
	public static final String BRIDGE_KEY = "ages_of_siege:integration_bridge";
	public static final int API_VERSION = 1;
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
		AgesOfSiegeIntegrationBridge bridge = new AgesOfSiegeIntegrationBridge();
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
		FabricLoader.getInstance().getObjectShare().put(BRIDGE_KEY, bridge);
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
		new AgesOfSiegeIntegrationBridge().depositCoin(
			server,
			request.getString("coinKey"),
			request.getInt("coinValue"),
			request.getInt("count")
		);
	}

	private static void creditTreasury(MinecraftServer server, Long amount) {
		if (amount == null) {
			return;
		}
		new AgesOfSiegeIntegrationBridge().creditTreasury(server, amount);
	}

	private static Boolean spendTreasury(MinecraftServer server, Long amount) {
		if (amount == null) {
			return Boolean.FALSE;
		}
		return new AgesOfSiegeIntegrationBridge().spendTreasury(server, amount);
	}

	private static void setTreasuryBalance(MinecraftServer server, Long amount) {
		if (amount == null) {
			return;
		}
		new AgesOfSiegeIntegrationBridge().setTreasuryBalance(server, amount);
	}

	private static void setBankPosition(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return;
		}
		new AgesOfSiegeIntegrationBridge().setTrackedBank(
			server,
			new BlockPos(request.getInt("x"), request.getInt("y"), request.getInt("z")),
			request.getString("dimension"),
			request.contains("protectionCap") ? request.getInt("protectionCap") : 100
		);
	}

	private static void clearBankPosition(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return;
		}
		new AgesOfSiegeIntegrationBridge().clearTrackedBank(
			server,
			new BlockPos(request.getInt("x"), request.getInt("y"), request.getInt("z")),
			request.getString("dimension")
		);
	}

	private static Boolean tryTrackBankPosition(MinecraftServer server, NbtCompound request) {
		if (request == null) {
			return Boolean.FALSE;
		}
		return new AgesOfSiegeIntegrationBridge().tryTrackBank(
			server,
			new BlockPos(request.getInt("x"), request.getInt("y"), request.getInt("z")),
			request.getString("dimension"),
			request.contains("protectionCap") ? request.getInt("protectionCap") : 100
		);
	}
}
