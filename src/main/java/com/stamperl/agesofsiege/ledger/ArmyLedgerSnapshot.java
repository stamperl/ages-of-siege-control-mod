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
	boolean hasRally,
	BlockPos rallyPos,
	String dimensionId,
	String ownerName,
	int objectiveHealth,
	int maxObjectiveHealth,
	String siegePhase,
	int currentAgeLevel,
	String currentAgeName,
	int completedSieges,
	int nextAgeRequirement,
	String selectedSiegeId,
	boolean siegeLocked,
	boolean canLockSiege,
	boolean canStartSiege,
	String siegeStatus,
	List<SiegeEntry> sieges,
	List<DefenderEntry> defenders
) {
	public void write(PacketByteBuf buf) {
		buf.writeBoolean(hasBase);
		buf.writeBlockPos(bannerPos);
		buf.writeBoolean(hasRally);
		buf.writeBlockPos(rallyPos);
		buf.writeString(dimensionId);
		buf.writeString(ownerName);
		buf.writeVarInt(objectiveHealth);
		buf.writeVarInt(maxObjectiveHealth);
		buf.writeString(siegePhase);
		buf.writeVarInt(currentAgeLevel);
		buf.writeString(currentAgeName);
		buf.writeVarInt(completedSieges);
		buf.writeVarInt(nextAgeRequirement);
		buf.writeString(selectedSiegeId);
		buf.writeBoolean(siegeLocked);
		buf.writeBoolean(canLockSiege);
		buf.writeBoolean(canStartSiege);
		buf.writeString(siegeStatus);
		buf.writeVarInt(sieges.size());
		for (SiegeEntry siege : sieges) {
			siege.write(buf);
		}
		buf.writeVarInt(defenders.size());
		for (DefenderEntry defender : defenders) {
			defender.write(buf);
		}
	}

	public static ArmyLedgerSnapshot read(PacketByteBuf buf) {
		boolean hasBase = buf.readBoolean();
		BlockPos bannerPos = buf.readBlockPos();
		boolean hasRally = buf.readBoolean();
		BlockPos rallyPos = buf.readBlockPos();
		String dimensionId = buf.readString();
		String ownerName = buf.readString();
		int objectiveHealth = buf.readVarInt();
		int maxObjectiveHealth = buf.readVarInt();
		String siegePhase = buf.readString();
		int currentAgeLevel = buf.readVarInt();
		String currentAgeName = buf.readString();
		int completedSieges = buf.readVarInt();
		int nextAgeRequirement = buf.readVarInt();
		String selectedSiegeId = buf.readString();
		boolean siegeLocked = buf.readBoolean();
		boolean canLockSiege = buf.readBoolean();
		boolean canStartSiege = buf.readBoolean();
		String siegeStatus = buf.readString();
		int siegeCount = buf.readVarInt();
		List<SiegeEntry> sieges = new ArrayList<>();
		for (int i = 0; i < siegeCount; i++) {
			sieges.add(SiegeEntry.read(buf));
		}
		int defenderCount = buf.readVarInt();
		List<DefenderEntry> defenders = new ArrayList<>();
		for (int i = 0; i < defenderCount; i++) {
			defenders.add(DefenderEntry.read(buf));
		}
		return new ArmyLedgerSnapshot(
			hasBase,
			bannerPos,
			hasRally,
			rallyPos,
			dimensionId,
			ownerName,
			objectiveHealth,
			maxObjectiveHealth,
			siegePhase,
			currentAgeLevel,
			currentAgeName,
			completedSieges,
			nextAgeRequirement,
			selectedSiegeId,
			siegeLocked,
			canLockSiege,
			canStartSiege,
			siegeStatus,
			sieges,
			defenders
		);
	}

	public record SiegeEntry(
		String id,
		String name,
		String description,
		int ageLevel,
		int unlockVictories,
		int waveSize,
		boolean unlocked,
		boolean replay,
		boolean hasRam,
		String enemySummary,
		String weaponSummary,
		String threatSummary,
		int warSuppliesReward
	) {
		public void write(PacketByteBuf buf) {
			buf.writeString(id);
			buf.writeString(name);
			buf.writeString(description);
			buf.writeVarInt(ageLevel);
			buf.writeVarInt(unlockVictories);
			buf.writeVarInt(waveSize);
			buf.writeBoolean(unlocked);
			buf.writeBoolean(replay);
			buf.writeBoolean(hasRam);
			buf.writeString(enemySummary);
			buf.writeString(weaponSummary);
			buf.writeString(threatSummary);
			buf.writeVarInt(warSuppliesReward);
		}

		public static SiegeEntry read(PacketByteBuf buf) {
			return new SiegeEntry(
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readString(),
				buf.readString(),
				buf.readString(),
				buf.readVarInt()
			);
		}
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
