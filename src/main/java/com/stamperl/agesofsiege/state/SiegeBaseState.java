package com.stamperl.agesofsiege.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class SiegeBaseState extends PersistentState {
	private static final String STATE_KEY = "ages_of_siege_base";

	private boolean hasBase;
	private BlockPos basePos = BlockPos.ORIGIN;
	private String dimensionId = "minecraft:overworld";
	private String claimedBy = "unknown";

	public static SiegeBaseState get(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		PersistentStateManager manager = overworld.getPersistentStateManager();
		return manager.getOrCreate(SiegeBaseState::fromNbt, SiegeBaseState::new, STATE_KEY);
	}

	private static SiegeBaseState fromNbt(NbtCompound nbt) {
		SiegeBaseState state = new SiegeBaseState();
		state.hasBase = nbt.getBoolean("hasBase");
		state.basePos = new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
		state.dimensionId = nbt.getString("dimension");
		state.claimedBy = nbt.getString("claimedBy");
		return state;
	}

	public void setBase(BlockPos pos, String dimensionId, String claimedBy) {
		this.hasBase = true;
		this.basePos = pos.toImmutable();
		this.dimensionId = dimensionId;
		this.claimedBy = claimedBy;
		markDirty();
	}

	public void clearBase() {
		this.hasBase = false;
		this.basePos = BlockPos.ORIGIN;
		this.dimensionId = "minecraft:overworld";
		this.claimedBy = "unknown";
		markDirty();
	}

	public boolean hasBase() {
		return hasBase;
	}

	public String describe() {
		return String.format(
			"Current siege base: %s in %s, claimed by %s.",
			basePos.toShortString(),
			dimensionId,
			claimedBy
		);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putBoolean("hasBase", hasBase);
		nbt.putInt("x", basePos.getX());
		nbt.putInt("y", basePos.getY());
		nbt.putInt("z", basePos.getZ());
		nbt.putString("dimension", dimensionId);
		nbt.putString("claimedBy", claimedBy);
		return nbt;
	}
}
