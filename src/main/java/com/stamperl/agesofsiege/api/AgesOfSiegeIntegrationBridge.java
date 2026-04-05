package com.stamperl.agesofsiege.api;

import com.stamperl.agesofsiege.siege.service.ObjectiveService;
import com.stamperl.agesofsiege.state.SharedTreasuryState;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class AgesOfSiegeIntegrationBridge {
	public int apiVersion() {
		return AgesOfSiegeIntegrationApi.API_VERSION;
	}

	public AgesOfSiegeAgeSnapshot getAgeSnapshot(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		return new AgesOfSiegeAgeSnapshot(state.getAgeLevel(), state.getAgeName(), state.getCompletedSieges());
	}

	public AgesOfSiegeTreasurySnapshot getTreasurySnapshot(MinecraftServer server) {
		SharedTreasuryState treasury = SharedTreasuryState.get(server);
		return new AgesOfSiegeTreasurySnapshot(treasury.getBalance(), treasury.getDepositedCoins());
	}

	public AgesOfSiegeBankSnapshot getBankSnapshot(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		BlockPos pos = state.getTrackedBankPos();
		return new AgesOfSiegeBankSnapshot(
			pos != null,
			pos == null ? 0 : pos.getX(),
			pos == null ? 0 : pos.getY(),
			pos == null ? 0 : pos.getZ(),
			state.getTrackedBankDimensionId(),
			state.getBankProtectionCap(),
			state.getBankHealth(),
			state.getMaxBankHealth()
		);
	}

	public void depositCoin(MinecraftServer server, String coinKey, int coinValue, int count) {
		if (coinKey == null || coinKey.isBlank()) {
			return;
		}
		SharedTreasuryState.get(server).depositCoins(coinKey, coinValue, count);
	}

	public void creditTreasury(MinecraftServer server, long amount) {
		SharedTreasuryState.get(server).credit(amount);
	}

	public boolean spendTreasury(MinecraftServer server, long amount) {
		return SharedTreasuryState.get(server).spend(amount);
	}

	public void setTreasuryBalance(MinecraftServer server, long amount) {
		SharedTreasuryState.get(server).setBalance(amount);
	}

	public void setTrackedBank(MinecraftServer server, BlockPos pos, String dimensionId, int protectionCap) {
		if (pos == null) {
			return;
		}
		SiegeBaseState.get(server).setTrackedBank(pos, normalizeDimensionId(dimensionId), protectionCap);
	}

	public void clearTrackedBank(MinecraftServer server, BlockPos pos, String dimensionId) {
		if (pos == null) {
			return;
		}
		SiegeBaseState.get(server).clearTrackedBankIfMatches(pos, normalizeDimensionId(dimensionId));
	}

	public boolean tryTrackBank(MinecraftServer server, BlockPos pos, String dimensionId, int protectionCap) {
		if (pos == null) {
			return false;
		}
		SiegeBaseState state = SiegeBaseState.get(server);
		String resolvedDimensionId = normalizeDimensionId(dimensionId);
		if (!state.hasTrackedBank()) {
			state.setTrackedBank(pos, resolvedDimensionId, protectionCap);
			return true;
		}
		if (state.isTrackedBankAt(pos, resolvedDimensionId)) {
			return true;
		}
		Identifier trackedDimension = Identifier.tryParse(state.getTrackedBankDimensionId());
		ServerWorld trackedWorld = trackedDimension == null
			? null
			: server.getWorld(RegistryKey.of(RegistryKeys.WORLD, trackedDimension));
		ObjectiveService objectiveService = new ObjectiveService();
		if (trackedWorld == null || !objectiveService.isTrackedBankPresent(trackedWorld, state)) {
			state.clearTrackedBank();
			state.setTrackedBank(pos, resolvedDimensionId, protectionCap);
			return true;
		}
		return false;
	}

	private String normalizeDimensionId(String dimensionId) {
		return dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
	}
}
