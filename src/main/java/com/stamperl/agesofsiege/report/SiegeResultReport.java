package com.stamperl.agesofsiege.report;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SiegeResultReport(
	long reportId,
	UUID ownerUuid,
	String ownerName,
	String siegeId,
	String siegeName,
	String ageName,
	boolean victory,
	long resolvedGameTime,
	String summaryText,
	List<LossEntry> attackerLosses,
	List<LossEntry> defenderLosses,
	float playerDamageDealt,
	float playerDamageTaken,
	int playerKills,
	List<ItemStack> claimableRewards,
	boolean rewardsClaimed,
	boolean acknowledged
) {
	public SiegeResultReport {
		ownerName = ownerName == null ? "" : ownerName.trim();
		siegeId = siegeId == null ? "" : siegeId.trim();
		siegeName = siegeName == null ? "" : siegeName.trim();
		ageName = ageName == null ? "" : ageName.trim();
		summaryText = summaryText == null ? "" : summaryText.trim();
		attackerLosses = attackerLosses == null ? List.of() : List.copyOf(attackerLosses);
		defenderLosses = defenderLosses == null ? List.of() : List.copyOf(defenderLosses);
		claimableRewards = copyStacks(claimableRewards);
		playerDamageDealt = Math.max(0.0F, playerDamageDealt);
		playerDamageTaken = Math.max(0.0F, playerDamageTaken);
		playerKills = Math.max(0, playerKills);
	}

	public boolean hasClaimableRewards() {
		return !rewardsClaimed && claimableRewards.stream().anyMatch(stack -> !stack.isEmpty());
	}

	public SiegeResultReport withClaimedRewards() {
		return new SiegeResultReport(
			reportId,
			ownerUuid,
			ownerName,
			siegeId,
			siegeName,
			ageName,
			victory,
			resolvedGameTime,
			summaryText,
			attackerLosses,
			defenderLosses,
			playerDamageDealt,
			playerDamageTaken,
			playerKills,
			List.of(),
			true,
			acknowledged
		);
	}

	public SiegeResultReport acknowledged(boolean value) {
		return new SiegeResultReport(
			reportId,
			ownerUuid,
			ownerName,
			siegeId,
			siegeName,
			ageName,
			victory,
			resolvedGameTime,
			summaryText,
			attackerLosses,
			defenderLosses,
			playerDamageDealt,
			playerDamageTaken,
			playerKills,
			claimableRewards,
			rewardsClaimed,
			value
		);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putLong("reportId", reportId);
		if (ownerUuid != null) {
			nbt.putUuid("ownerUuid", ownerUuid);
		}
		nbt.putString("ownerName", ownerName);
		nbt.putString("siegeId", siegeId);
		nbt.putString("siegeName", siegeName);
		nbt.putString("ageName", ageName);
		nbt.putBoolean("victory", victory);
		nbt.putLong("resolvedGameTime", resolvedGameTime);
		nbt.putString("summaryText", summaryText);
		nbt.put("attackerLosses", writeLosses(attackerLosses));
		nbt.put("defenderLosses", writeLosses(defenderLosses));
		nbt.putFloat("playerDamageDealt", playerDamageDealt);
		nbt.putFloat("playerDamageTaken", playerDamageTaken);
		nbt.putInt("playerKills", playerKills);
		nbt.put("claimableRewards", writeStacks(claimableRewards));
		nbt.putBoolean("rewardsClaimed", rewardsClaimed);
		nbt.putBoolean("acknowledged", acknowledged);
		return nbt;
	}

	public static SiegeResultReport fromNbt(NbtCompound nbt) {
		List<ItemStack> rewards;
		boolean claimed;
		if (nbt.contains("claimableRewards", NbtElement.LIST_TYPE)) {
			rewards = readStacks(nbt.getList("claimableRewards", NbtElement.COMPOUND_TYPE));
			claimed = nbt.getBoolean("rewardsClaimed");
		} else {
			List<ItemStack> legacyRewards = readStacks(nbt.getList("overflowRewards", NbtElement.COMPOUND_TYPE));
			if (legacyRewards.isEmpty()) {
				legacyRewards = readStacks(nbt.getList("grantedRewards", NbtElement.COMPOUND_TYPE));
			}
			rewards = legacyRewards;
			claimed = false;
		}
		return new SiegeResultReport(
			nbt.getLong("reportId"),
			nbt.containsUuid("ownerUuid") ? nbt.getUuid("ownerUuid") : null,
			nbt.getString("ownerName"),
			nbt.getString("siegeId"),
			nbt.getString("siegeName"),
			nbt.getString("ageName"),
			nbt.getBoolean("victory"),
			nbt.getLong("resolvedGameTime"),
			nbt.getString("summaryText"),
			readLosses(nbt.getList("attackerLosses", NbtElement.COMPOUND_TYPE)),
			readLosses(nbt.getList("defenderLosses", NbtElement.COMPOUND_TYPE)),
			nbt.getFloat("playerDamageDealt"),
			nbt.getFloat("playerDamageTaken"),
			nbt.getInt("playerKills"),
			rewards,
			claimed,
			nbt.getBoolean("acknowledged")
		);
	}

	private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return List.of();
		}
		List<ItemStack> copy = new ArrayList<>();
		for (ItemStack stack : stacks) {
			if (stack != null && !stack.isEmpty()) {
				copy.add(stack.copy());
			}
		}
		return List.copyOf(copy);
	}

	private static NbtList writeLosses(List<LossEntry> losses) {
		NbtList list = new NbtList();
		for (LossEntry loss : losses) {
			NbtCompound entry = new NbtCompound();
			entry.putString("key", loss.key());
			entry.putString("label", loss.label());
			entry.putInt("count", loss.count());
			list.add(entry);
		}
		return list;
	}

	private static List<LossEntry> readLosses(NbtList list) {
		List<LossEntry> losses = new ArrayList<>();
		for (NbtElement element : list) {
			NbtCompound entry = (NbtCompound) element;
			losses.add(new LossEntry(entry.getString("key"), entry.getString("label"), entry.getInt("count")));
		}
		return List.copyOf(losses);
	}

	private static NbtList writeStacks(List<ItemStack> stacks) {
		NbtList list = new NbtList();
		for (ItemStack stack : stacks) {
			if (stack != null && !stack.isEmpty()) {
				list.add(stack.writeNbt(new NbtCompound()));
			}
		}
		return list;
	}

	private static List<ItemStack> readStacks(NbtList list) {
		List<ItemStack> stacks = new ArrayList<>();
		for (NbtElement element : list) {
			stacks.add(ItemStack.fromNbt((NbtCompound) element));
		}
		return List.copyOf(stacks);
	}

	public record LossEntry(String key, String label, int count) {
		public LossEntry {
			key = key == null ? "" : key.trim();
			label = label == null ? "" : label.trim();
			count = Math.max(0, count);
		}
	}
}
