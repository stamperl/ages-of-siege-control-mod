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
	private static final int MAX_OBJECTIVE_HEALTH = 30;

	private boolean hasBase;
	private BlockPos basePos = BlockPos.ORIGIN;
	private String dimensionId = "minecraft:overworld";
	private String claimedBy = "unknown";
	private int ageLevel;
	private int completedSieges;
	private boolean siegeActive;
	private boolean siegePending;
	private boolean siegeFailed;
	private int countdownTicks;
	private int lastWaveSize;
	private int objectiveHealth = MAX_OBJECTIVE_HEALTH;
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
		state.ageLevel = nbt.getInt("ageLevel");
		state.completedSieges = nbt.getInt("completedSieges");
		state.siegeActive = nbt.getBoolean("siegeActive");
		state.siegePending = nbt.getBoolean("siegePending");
		state.siegeFailed = nbt.getBoolean("siegeFailed");
		state.countdownTicks = nbt.getInt("countdownTicks");
		state.lastWaveSize = nbt.getInt("lastWaveSize");
		state.objectiveHealth = nbt.contains("objectiveHealth") ? nbt.getInt("objectiveHealth") : MAX_OBJECTIVE_HEALTH;
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
		this.ageLevel = Math.max(this.ageLevel, 0);
		this.siegeFailed = false;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
		markDirty();
	}

	public void clearBase() {
		this.hasBase = false;
		this.basePos = BlockPos.ORIGIN;
		this.dimensionId = "minecraft:overworld";
		this.claimedBy = "unknown";
		this.siegePending = false;
		this.siegeActive = false;
		this.siegeFailed = false;
		this.countdownTicks = 0;
		this.lastWaveSize = 0;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
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

	public boolean isSiegePending() {
		return siegePending;
	}

	public int getObjectiveHealth() {
		return objectiveHealth;
	}

	public int getMaxObjectiveHealth() {
		return MAX_OBJECTIVE_HEALTH;
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

	public void beginCountdown(MinecraftServer server, int countdownSeconds) {
		this.siegePending = true;
		this.siegeActive = false;
		this.siegeFailed = false;
		this.countdownTicks = countdownSeconds * 20;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
		this.attackerIds.clear();
		server.getPlayerManager().broadcast(
			Text.literal("A siege is approaching. Defend the Settlement Standard."),
			false
		);
		markDirty();
	}

	public int tickCountdown() {
		if (!siegePending || countdownTicks <= 0) {
			return countdownTicks;
		}

		countdownTicks--;
		markDirty();
		return countdownTicks;
	}

	public void startSiege(MinecraftServer server, List<UUID> attackerIds, int waveSize) {
		this.siegePending = false;
		this.siegeActive = true;
		this.siegeFailed = false;
		this.countdownTicks = 0;
		this.lastWaveSize = waveSize;
		this.attackerIds.clear();
		this.attackerIds.addAll(attackerIds);
		server.getPlayerManager().broadcast(Text.literal("A siege has begun. Defend the Settlement Standard!"), false);
		markDirty();
	}

	public void endSiege(boolean failed, boolean rewardProgress) {
		this.siegePending = false;
		this.siegeActive = false;
		this.siegeFailed = failed;
		this.countdownTicks = 0;
		if (rewardProgress && !failed) {
			this.completedSieges++;
			this.ageLevel = Math.max(this.ageLevel, 1);
		}
		this.attackerIds.clear();
		markDirty();
	}

	public void damageObjective(ServerWorld world, int amount) {
		if (!hasBase) {
			return;
		}

		this.objectiveHealth = Math.max(0, this.objectiveHealth - amount);
		if (this.objectiveHealth == 0) {
			world.breakBlock(basePos, false);
			handleObjectiveDestroyed(world, basePos);
			return;
		}

		if (siegeActive) {
			world.getServer().getPlayerManager().broadcast(
				Text.literal("Settlement banner damaged: " + objectiveHealth + "/" + MAX_OBJECTIVE_HEALTH + " HP"),
				false
			);
		}

		markDirty();
	}

	public void handleObjectiveDestroyed(ServerWorld world, BlockPos pos) {
		if (!hasBase || !basePos.equals(pos) || !world.getRegistryKey().getValue().toString().equals(dimensionId)) {
			return;
		}

		if (siegeActive) {
			endSiege(true, false);
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
			"Current siege base: %s in %s, claimed by %s. Objective HP: %d/%d. Age level: %d. Completed sieges: %d. Siege pending: %s. Siege active: %s. Siege failed: %s. Last wave size: %d.",
			basePos.toShortString(),
			dimensionId,
			claimedBy,
			objectiveHealth,
			MAX_OBJECTIVE_HEALTH,
			ageLevel,
			completedSieges,
			siegePending ? "yes" : "no",
			siegeActive ? "yes" : "no",
			siegeFailed ? "yes" : "no",
			lastWaveSize
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
		nbt.putInt("ageLevel", ageLevel);
		nbt.putInt("completedSieges", completedSieges);
		nbt.putBoolean("siegePending", siegePending);
		nbt.putBoolean("siegeActive", siegeActive);
		nbt.putBoolean("siegeFailed", siegeFailed);
		nbt.putInt("countdownTicks", countdownTicks);
		nbt.putInt("lastWaveSize", lastWaveSize);
		nbt.putInt("objectiveHealth", objectiveHealth);
		NbtList attackerList = new NbtList();
		for (UUID attackerId : attackerIds) {
			attackerList.add(NbtString.of(attackerId.toString()));
		}
		nbt.put("attackers", attackerList);
		return nbt;
	}
}
