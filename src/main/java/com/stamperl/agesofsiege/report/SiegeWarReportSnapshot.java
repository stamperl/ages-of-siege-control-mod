package com.stamperl.agesofsiege.report;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

public record SiegeWarReportSnapshot(
	long reportId,
	boolean victory,
	String siegeName,
	String ageName,
	String summaryText,
	List<LossEntry> attackerLosses,
	List<LossEntry> defenderLosses,
	float playerDamageDealt,
	float playerDamageTaken,
	int playerKills,
	List<ItemStack> claimableRewards,
	boolean rewardsClaimed
) {
	public void write(PacketByteBuf buf) {
		buf.writeVarLong(reportId);
		buf.writeBoolean(victory);
		buf.writeString(siegeName);
		buf.writeString(ageName);
		buf.writeString(summaryText);
		buf.writeVarInt(attackerLosses.size());
		for (LossEntry loss : attackerLosses) {
			loss.write(buf);
		}
		buf.writeVarInt(defenderLosses.size());
		for (LossEntry loss : defenderLosses) {
			loss.write(buf);
		}
		buf.writeFloat(playerDamageDealt);
		buf.writeFloat(playerDamageTaken);
		buf.writeVarInt(playerKills);
		writeStacks(buf, claimableRewards);
		buf.writeBoolean(rewardsClaimed);
	}

	public static SiegeWarReportSnapshot read(PacketByteBuf buf) {
		long reportId = buf.readVarLong();
		boolean victory = buf.readBoolean();
		String siegeName = buf.readString();
		String ageName = buf.readString();
		String summaryText = buf.readString();
		List<LossEntry> attackerLosses = readLosses(buf);
		List<LossEntry> defenderLosses = readLosses(buf);
		float playerDamageDealt = buf.readFloat();
		float playerDamageTaken = buf.readFloat();
		int playerKills = buf.readVarInt();
		List<ItemStack> claimableRewards = readStacks(buf);
		boolean rewardsClaimed = buf.readBoolean();
		return new SiegeWarReportSnapshot(
			reportId,
			victory,
			siegeName,
			ageName,
			summaryText,
			attackerLosses,
			defenderLosses,
			playerDamageDealt,
			playerDamageTaken,
			playerKills,
			claimableRewards,
			rewardsClaimed
		);
	}

	public static SiegeWarReportSnapshot fromReport(SiegeResultReport report) {
		return new SiegeWarReportSnapshot(
			report.reportId(),
			report.victory(),
			report.siegeName(),
			report.ageName(),
			report.summaryText(),
			report.attackerLosses().stream().map(loss -> new LossEntry(loss.key(), loss.label(), loss.count())).toList(),
			report.defenderLosses().stream().map(loss -> new LossEntry(loss.key(), loss.label(), loss.count())).toList(),
			report.playerDamageDealt(),
			report.playerDamageTaken(),
			report.playerKills(),
			copyStacks(report.claimableRewards()),
			report.rewardsClaimed()
		);
	}

	public boolean hasClaimableRewards() {
		return !claimableRewards.isEmpty() && !rewardsClaimed;
	}

	private static void writeStacks(PacketByteBuf buf, List<ItemStack> stacks) {
		buf.writeVarInt(stacks.size());
		for (ItemStack stack : stacks) {
			buf.writeItemStack(stack);
		}
	}

	private static List<ItemStack> readStacks(PacketByteBuf buf) {
		int count = buf.readVarInt();
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			stacks.add(buf.readItemStack());
		}
		return List.copyOf(stacks);
	}

	private static List<LossEntry> readLosses(PacketByteBuf buf) {
		int count = buf.readVarInt();
		List<LossEntry> entries = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			entries.add(LossEntry.read(buf));
		}
		return List.copyOf(entries);
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

	public record LossEntry(String key, String label, int count) {
		public void write(PacketByteBuf buf) {
			buf.writeString(key);
			buf.writeString(label);
			buf.writeVarInt(count);
		}

		public static LossEntry read(PacketByteBuf buf) {
			return new LossEntry(buf.readString(), buf.readString(), buf.readVarInt());
		}
	}
}
