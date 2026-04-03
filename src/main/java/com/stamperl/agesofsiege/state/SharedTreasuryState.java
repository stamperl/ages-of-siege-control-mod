package com.stamperl.agesofsiege.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;

public class SharedTreasuryState extends PersistentState {
	private static final String STATE_KEY = "ages_of_siege_shared_treasury";

	private long balance;
	private final Map<String, Long> depositedCoins = new HashMap<>();

	public static SharedTreasuryState get(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		PersistentStateManager manager = overworld.getPersistentStateManager();
		return manager.getOrCreate(SharedTreasuryState::fromNbt, SharedTreasuryState::new, STATE_KEY);
	}

	private static SharedTreasuryState fromNbt(NbtCompound nbt) {
		SharedTreasuryState state = new SharedTreasuryState();
		state.balance = nbt.getLong("balance");
		NbtCompound coins = nbt.getCompound("coins");
		for (String key : coins.getKeys()) {
			state.depositedCoins.put(key, coins.getLong(key));
		}
		return state;
	}

	public long getBalance() {
		return balance;
	}

	public void depositCoins(String coinKey, int coinValue, int count) {
		if (coinKey == null || coinKey.isBlank() || coinValue <= 0 || count <= 0) {
			return;
		}
		balance += (long) coinValue * count;
		depositedCoins.merge(coinKey, (long) count, Long::sum);
		markDirty();
	}

	public void credit(long amount) {
		if (amount <= 0) {
			return;
		}
		balance += amount;
		markDirty();
	}

	public boolean spend(long amount) {
		if (amount < 0 || balance < amount) {
			return false;
		}
		balance -= amount;
		markDirty();
		return true;
	}

	public void setBalance(long amount) {
		balance = Math.max(0L, amount);
		markDirty();
	}

	public long applyProtectedLoss(long protectionCap, int lossPercent) {
		long protectedFloor = Math.max(0L, protectionCap);
		if (lossPercent <= 0 || balance <= protectedFloor) {
			return 0L;
		}
		long exposed = balance - protectedFloor;
		long lost = Math.max(0L, Math.round(exposed * (lossPercent / 100.0D)));
		if (lost <= 0L) {
			return 0L;
		}
		balance = Math.max(protectedFloor, balance - lost);
		markDirty();
		return lost;
	}

	public NbtCompound toNbtSnapshot() {
		NbtCompound snapshot = new NbtCompound();
		snapshot.putLong("balance", balance);
		NbtCompound coins = new NbtCompound();
		for (Map.Entry<String, Long> entry : depositedCoins.entrySet()) {
			coins.putLong(entry.getKey(), entry.getValue());
		}
		snapshot.put("coins", coins);
		return snapshot;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putLong("balance", balance);
		NbtCompound coins = new NbtCompound();
		for (Map.Entry<String, Long> entry : depositedCoins.entrySet()) {
			coins.putLong(entry.getKey(), entry.getValue());
		}
		nbt.put("coins", coins);
		return nbt;
	}
}
