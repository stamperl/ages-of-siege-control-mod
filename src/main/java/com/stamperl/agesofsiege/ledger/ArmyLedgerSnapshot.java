package com.stamperl.agesofsiege.ledger;

import com.stamperl.agesofsiege.defense.DefenderRole;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ArmyLedgerSnapshot(
	boolean hasBase,
	BlockPos bannerPos,
	String dimensionId,
	String ownerName,
	int objectiveHealth,
	int maxObjectiveHealth,
	String siegePhase,
	List<DefenderEntry> defenders
) {
	public void write(PacketByteBuf buf) {
		buf.writeBoolean(hasBase);
		buf.writeBlockPos(bannerPos);
		buf.writeString(dimensionId);
		buf.writeString(ownerName);
		buf.writeVarInt(objectiveHealth);
		buf.writeVarInt(maxObjectiveHealth);
		buf.writeString(siegePhase);
		buf.writeVarInt(defenders.size());
		for (DefenderEntry defender : defenders) {
			defender.write(buf);
		}
	}

	public static ArmyLedgerSnapshot read(PacketByteBuf buf) {
		boolean hasBase = buf.readBoolean();
		BlockPos bannerPos = buf.readBlockPos();
		String dimensionId = buf.readString();
		String ownerName = buf.readString();
		int objectiveHealth = buf.readVarInt();
		int maxObjectiveHealth = buf.readVarInt();
		String siegePhase = buf.readString();
		int defenderCount = buf.readVarInt();
		List<DefenderEntry> defenders = new ArrayList<>();
		for (int i = 0; i < defenderCount; i++) {
			defenders.add(DefenderEntry.read(buf));
		}
		return new ArmyLedgerSnapshot(hasBase, bannerPos, dimensionId, ownerName, objectiveHealth, maxObjectiveHealth, siegePhase, defenders);
	}

	public record DefenderEntry(
		UUID entityUuid,
		int entityId,
		String name,
		DefenderRole role,
		BlockPos homePost,
		BlockPos currentPos,
		float health,
		float maxHealth,
		int attackPower,
		String armorLabel,
		boolean online
	) {
		public void write(PacketByteBuf buf) {
			buf.writeUuid(entityUuid);
			buf.writeVarInt(entityId);
			buf.writeString(name);
			buf.writeEnumConstant(role);
			buf.writeBlockPos(homePost);
			buf.writeBlockPos(currentPos);
			buf.writeFloat(health);
			buf.writeFloat(maxHealth);
			buf.writeVarInt(attackPower);
			buf.writeString(armorLabel);
			buf.writeBoolean(online);
		}

		public static DefenderEntry read(PacketByteBuf buf) {
			return new DefenderEntry(
				buf.readUuid(),
				buf.readVarInt(),
				buf.readString(),
				buf.readEnumConstant(DefenderRole.class),
				buf.readBlockPos(),
				buf.readBlockPos(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readVarInt(),
				buf.readString(),
				buf.readBoolean()
			);
		}
	}
}
