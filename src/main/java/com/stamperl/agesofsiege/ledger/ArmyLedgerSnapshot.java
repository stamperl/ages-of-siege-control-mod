package com.stamperl.agesofsiege.ledger;

import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
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
	int ageProgressWins,
	int ageProgressTarget,
	int nextAgeRequirement,
	String selectedSiegeId,
	boolean siegeLocked,
	boolean canLockSiege,
	boolean canStartSiege,
	String siegeStatus,
	List<SiegeEntry> sieges,
	List<DefenderEntry> defenders,
	List<AttackerEntry> attackers
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
		buf.writeVarInt(ageProgressWins);
		buf.writeVarInt(ageProgressTarget);
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
		buf.writeVarInt(attackers.size());
		for (AttackerEntry attacker : attackers) {
			attacker.write(buf);
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
		int ageProgressWins = buf.readVarInt();
		int ageProgressTarget = buf.readVarInt();
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
		int attackerCount = buf.readVarInt();
		List<AttackerEntry> attackers = new ArrayList<>();
		for (int i = 0; i < attackerCount; i++) {
			attackers.add(AttackerEntry.read(buf));
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
			ageProgressWins,
			ageProgressTarget,
			nextAgeRequirement,
			selectedSiegeId,
			siegeLocked,
			canLockSiege,
			canStartSiege,
			siegeStatus,
			sieges,
			defenders,
			attackers
		);
	}

	public record SiegeEntry(
		String id,
		String name,
		String description,
		int ageLevel,
		int unlockVictories,
		int waveSize,
		boolean ageDefining,
		boolean unlocked,
		boolean replay,
		int ramCount,
		int routeColumn,
		int routeRow,
		String enemySummary,
		String weaponSummary,
		String threatSummary,
		String breachSummary,
		int warSuppliesReward,
		String battleProfileId,
		List<BattleGroupEntry> battleGroups
	) {
		public void write(PacketByteBuf buf) {
			buf.writeString(id);
			buf.writeString(name);
			buf.writeString(description);
			buf.writeVarInt(ageLevel);
			buf.writeVarInt(unlockVictories);
			buf.writeVarInt(waveSize);
			buf.writeBoolean(ageDefining);
			buf.writeBoolean(unlocked);
			buf.writeBoolean(replay);
			buf.writeVarInt(ramCount);
			buf.writeVarInt(routeColumn);
			buf.writeVarInt(routeRow);
			buf.writeString(enemySummary);
			buf.writeString(weaponSummary);
			buf.writeString(threatSummary);
			buf.writeString(breachSummary);
			buf.writeVarInt(warSuppliesReward);
			buf.writeString(battleProfileId);
			buf.writeVarInt(battleGroups.size());
			for (BattleGroupEntry group : battleGroups) {
				group.write(buf);
			}
		}

		public static SiegeEntry read(PacketByteBuf buf) {
			String id = buf.readString();
			String name = buf.readString();
			String description = buf.readString();
			int ageLevel = buf.readVarInt();
			int unlockVictories = buf.readVarInt();
			int waveSize = buf.readVarInt();
			boolean ageDefining = buf.readBoolean();
			boolean unlocked = buf.readBoolean();
			boolean replay = buf.readBoolean();
			int ramCount = buf.readVarInt();
			int routeColumn = buf.readVarInt();
			int routeRow = buf.readVarInt();
			String enemySummary = buf.readString();
			String weaponSummary = buf.readString();
			String threatSummary = buf.readString();
			String breachSummary = buf.readString();
			int warSuppliesReward = buf.readVarInt();
			String battleProfileId = buf.readString();
			int battleGroupCount = buf.readVarInt();
			List<BattleGroupEntry> battleGroups = new ArrayList<>();
			for (int i = 0; i < battleGroupCount; i++) {
				battleGroups.add(BattleGroupEntry.read(buf));
			}
			return new SiegeEntry(
				id,
				name,
				description,
				ageLevel,
				unlockVictories,
				waveSize,
				ageDefining,
				unlocked,
				replay,
				ramCount,
				routeColumn,
				routeRow,
				enemySummary,
				weaponSummary,
				threatSummary,
				breachSummary,
				warSuppliesReward,
				battleProfileId,
				battleGroups
			);
		}
	}

	public record BattleGroupEntry(
		String kindId,
		String displayName,
		int count,
		boolean engine
	) {
		public void write(PacketByteBuf buf) {
			buf.writeString(kindId);
			buf.writeString(displayName);
			buf.writeVarInt(count);
			buf.writeBoolean(engine);
		}

		public static BattleGroupEntry read(PacketByteBuf buf) {
			return new BattleGroupEntry(
				buf.readString(),
				buf.readString(),
				buf.readVarInt(),
				buf.readBoolean()
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

	public record AttackerEntry(
		UUID entityUuid,
		BlockPos currentPos,
		UnitRole role,
		boolean engine,
		boolean online
	) {
		public void write(PacketByteBuf buf) {
			buf.writeUuid(entityUuid);
			buf.writeBlockPos(currentPos);
			buf.writeEnumConstant(role);
			buf.writeBoolean(engine);
			buf.writeBoolean(online);
		}

		public static AttackerEntry read(PacketByteBuf buf) {
			return new AttackerEntry(
				buf.readUuid(),
				buf.readBlockPos(),
				buf.readEnumConstant(UnitRole.class),
				buf.readBoolean(),
				buf.readBoolean()
			);
		}
	}
}
