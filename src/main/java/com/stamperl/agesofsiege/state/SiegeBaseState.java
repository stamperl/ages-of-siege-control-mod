package com.stamperl.agesofsiege.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SiegeBaseState extends PersistentState {
	private static final String STATE_KEY = "ages_of_siege_base";

	private boolean hasBase;
	private BlockPos basePos = BlockPos.ORIGIN;
	private String dimensionId = "minecraft:overworld";
	private String claimedBy = "unknown";
	private boolean siegeActive;
	private boolean siegeFailed;
	private final List<UUID> attackerIds = new ArrayList<>();

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
		state.siegeActive = nbt.getBoolean("siegeActive");
		state.siegeFailed = nbt.getBoolean("siegeFailed");
		NbtList attackerList = nbt.getList("attackers", NbtElement.STRING_TYPE);
		for (NbtElement element : attackerList) {
			state.attackerIds.add(UUID.fromString(element.asString()));
		}
		return state;
	}

	public void setBase(BlockPos pos, String dimensionId, String claimedBy) {
		this.hasBase = true;
		this.basePos = pos.toImmutable();
		this.dimensionId = dimensionId;
		this.claimedBy = claimedBy;
		this.siegeFailed = false;
		markDirty();
	}

	public void clearBase() {
		this.hasBase = false;
		this.basePos = BlockPos.ORIGIN;
		this.dimensionId = "minecraft:overworld";
		this.claimedBy = "unknown";
		this.siegeActive = false;
		this.siegeFailed = false;
		this.attackerIds.clear();
		markDirty();
	}

	public boolean hasBase() {
		return hasBase;
	}

	public BlockPos getBasePos() {
		return basePos;
	}

	public boolean isSiegeActive() {
		return siegeActive;
	}

	public List<UUID> getAttackerIds() {
		return List.copyOf(attackerIds);
	}

	public void replaceAttackers(List<UUID> attackerIds) {
		this.attackerIds.clear();
		this.attackerIds.addAll(attackerIds);
		markDirty();
	}

	public ServerWorld getBaseWorld(MinecraftServer server) {
		Identifier id = Identifier.tryParse(dimensionId);
		if (id == null) {
			return null;
		}

		return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
	}

	public void startSiege(MinecraftServer server, List<UUID> attackerIds) {
		this.siegeActive = true;
		this.siegeFailed = false;
		this.attackerIds.clear();
		this.attackerIds.addAll(attackerIds);
		server.getPlayerManager().broadcast(Text.literal("A siege has begun. Defend the Settlement Standard!"), false);
		markDirty();
	}

	public void endSiege(boolean failed) {
		this.siegeActive = false;
		this.siegeFailed = failed;
		this.attackerIds.clear();
		markDirty();
	}

	public void handleObjectiveDestroyed(ServerWorld world, BlockPos pos) {
		if (!hasBase || !basePos.equals(pos) || !world.getRegistryKey().getValue().toString().equals(dimensionId)) {
			return;
		}

		if (siegeActive) {
			endSiege(true);
			world.getServer().getPlayerManager().broadcast(
				Text.literal("The Settlement Standard was destroyed. The siege is lost."),
				false
			);
			return;
		}

		clearBase();
		world.getServer().getPlayerManager().broadcast(
			Text.literal("The Settlement Standard was destroyed. This base is no longer claimed."),
			false
		);
	}

	public String describe() {
		return String.format(
			"Current siege base: %s in %s, claimed by %s. Siege active: %s. Siege failed: %s.",
			basePos.toShortString(),
			dimensionId,
			claimedBy,
			siegeActive ? "yes" : "no",
			siegeFailed ? "yes" : "no"
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
		nbt.putBoolean("siegeActive", siegeActive);
		nbt.putBoolean("siegeFailed", siegeFailed);
		NbtList attackerList = new NbtList();
		for (UUID attackerId : attackerIds) {
			attackerList.add(NbtString.of(attackerId.toString()));
		}
		nbt.put("attackers", attackerList);
		return nbt;
	}
}
