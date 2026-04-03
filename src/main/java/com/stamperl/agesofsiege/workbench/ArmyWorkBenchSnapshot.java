package com.stamperl.agesofsiege.workbench;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

public record ArmyWorkBenchSnapshot(
	BlockPos benchPos,
	boolean hasToken,
	ItemStack tokenStack,
	String soldierName,
	int level,
	int xp,
	int xpToNextLevel,
	int availablePoints,
	float health,
	float maxHealth,
	double attackPower,
	double armorValue,
	double speedValue,
	int vitality,
	int strength,
	int discipline,
	int agility,
	String armorTierId,
	String armorLabel,
	boolean leatherUnlocked,
	boolean chainmailUnlocked,
	boolean ironUnlocked,
	String statusMessage
) {
	public void write(PacketByteBuf buf) {
		buf.writeBlockPos(benchPos);
		buf.writeBoolean(hasToken);
		buf.writeItemStack(tokenStack);
		buf.writeString(soldierName);
		buf.writeVarInt(level);
		buf.writeVarInt(xp);
		buf.writeVarInt(xpToNextLevel);
		buf.writeVarInt(availablePoints);
		buf.writeFloat(health);
		buf.writeFloat(maxHealth);
		buf.writeDouble(attackPower);
		buf.writeDouble(armorValue);
		buf.writeDouble(speedValue);
		buf.writeVarInt(vitality);
		buf.writeVarInt(strength);
		buf.writeVarInt(discipline);
		buf.writeVarInt(agility);
		buf.writeString(armorTierId);
		buf.writeString(armorLabel);
		buf.writeBoolean(leatherUnlocked);
		buf.writeBoolean(chainmailUnlocked);
		buf.writeBoolean(ironUnlocked);
		buf.writeString(statusMessage);
	}

	public static ArmyWorkBenchSnapshot read(PacketByteBuf buf) {
		return new ArmyWorkBenchSnapshot(
			buf.readBlockPos(),
			buf.readBoolean(),
			buf.readItemStack(),
			buf.readString(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readFloat(),
			buf.readFloat(),
			buf.readDouble(),
			buf.readDouble(),
			buf.readDouble(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readVarInt(),
			buf.readString(),
			buf.readString(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readString()
		);
	}
}
