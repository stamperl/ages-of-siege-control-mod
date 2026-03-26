package com.stamperl.agesofsiege.siege.runtime;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BattlefieldObservation {
	private final boolean objectivePresent;
	private final int totalLivingAttackers;
	private final int livingBreachers;
	private final int livingRanged;
	private final int livingEscorts;
	private final int livingRamCount;
	private final LivingEntity closestAttackerToObjective;
	private final boolean pathToObjectiveExists;
	private final List<BlockPos> candidateBreachOpenings;
	private final List<BlockPos> intactWallSegments;
	private final UUID closestAttackerToObjectiveId;

	public BattlefieldObservation(
		boolean objectivePresent,
		int totalLivingAttackers,
		int livingBreachers,
		int livingRanged,
		int livingEscorts,
		int livingRamCount,
		LivingEntity closestAttackerToObjective,
		boolean pathToObjectiveExists,
		List<BlockPos> candidateBreachOpenings,
		List<BlockPos> intactWallSegments
	) {
		this(
			objectivePresent,
			totalLivingAttackers,
			livingBreachers,
			livingRanged,
			livingEscorts,
			livingRamCount,
			closestAttackerToObjective,
			closestAttackerToObjective == null ? null : closestAttackerToObjective.getUuid(),
			pathToObjectiveExists,
			candidateBreachOpenings,
			intactWallSegments
		);
	}

	private BattlefieldObservation(
		boolean objectivePresent,
		int totalLivingAttackers,
		int livingBreachers,
		int livingRanged,
		int livingEscorts,
		int livingRamCount,
		LivingEntity closestAttackerToObjective,
		UUID closestAttackerToObjectiveId,
		boolean pathToObjectiveExists,
		List<BlockPos> candidateBreachOpenings,
		List<BlockPos> intactWallSegments
	) {
		this.objectivePresent = objectivePresent;
		this.totalLivingAttackers = totalLivingAttackers;
		this.livingBreachers = livingBreachers;
		this.livingRanged = livingRanged;
		this.livingEscorts = livingEscorts;
		this.livingRamCount = livingRamCount;
		this.closestAttackerToObjective = closestAttackerToObjective;
		this.closestAttackerToObjectiveId = closestAttackerToObjectiveId;
		this.pathToObjectiveExists = pathToObjectiveExists;
		this.candidateBreachOpenings = List.copyOf(candidateBreachOpenings);
		this.intactWallSegments = List.copyOf(intactWallSegments);
	}

	public boolean isObjectivePresent() {
		return objectivePresent;
	}

	public int getTotalLivingAttackers() {
		return totalLivingAttackers;
	}

	public int getLivingBreachers() {
		return livingBreachers;
	}

	public int getLivingRanged() {
		return livingRanged;
	}

	public int getLivingEscorts() {
		return livingEscorts;
	}

	public int getLivingRamCount() {
		return livingRamCount;
	}

	public LivingEntity getClosestAttackerToObjective() {
		return closestAttackerToObjective;
	}

	public UUID getClosestAttackerToObjectiveId() {
		return closestAttackerToObjectiveId;
	}

	public boolean isPathToObjectiveExists() {
		return pathToObjectiveExists;
	}

	public List<BlockPos> getCandidateBreachOpenings() {
		return candidateBreachOpenings;
	}

	public List<BlockPos> getIntactWallSegments() {
		return intactWallSegments;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("objectivePresent", objectivePresent);
		nbt.putInt("totalLivingAttackers", totalLivingAttackers);
		nbt.putInt("livingBreachers", livingBreachers);
		nbt.putInt("livingRanged", livingRanged);
		nbt.putInt("livingEscorts", livingEscorts);
		nbt.putInt("livingRamCount", livingRamCount);
		if (closestAttackerToObjectiveId != null) {
			nbt.putUuid("closestAttackerToObjectiveId", closestAttackerToObjectiveId);
		}
		nbt.putBoolean("pathToObjectiveExists", pathToObjectiveExists);
		nbt.put("candidateBreachOpenings", writePositions(candidateBreachOpenings));
		nbt.put("intactWallSegments", writePositions(intactWallSegments));
		return nbt;
	}

	public static BattlefieldObservation fromNbt(NbtCompound nbt) {
		UUID closestId = nbt.containsUuid("closestAttackerToObjectiveId")
			? nbt.getUuid("closestAttackerToObjectiveId")
			: null;
		return new BattlefieldObservation(
			nbt.getBoolean("objectivePresent"),
			nbt.getInt("totalLivingAttackers"),
			nbt.getInt("livingBreachers"),
			nbt.getInt("livingRanged"),
			nbt.getInt("livingEscorts"),
			nbt.getInt("livingRamCount"),
			null,
			closestId,
			nbt.getBoolean("pathToObjectiveExists"),
			readPositions(nbt.getList("candidateBreachOpenings", NbtElement.COMPOUND_TYPE)),
			readPositions(nbt.getList("intactWallSegments", NbtElement.COMPOUND_TYPE))
		);
	}

	private static NbtList writePositions(List<BlockPos> positions) {
		NbtList list = new NbtList();
		for (BlockPos pos : positions) {
			NbtCompound posNbt = new NbtCompound();
			posNbt.putInt("x", pos.getX());
			posNbt.putInt("y", pos.getY());
			posNbt.putInt("z", pos.getZ());
			list.add(posNbt);
		}
		return list;
	}

	private static List<BlockPos> readPositions(NbtList list) {
		List<BlockPos> positions = new ArrayList<>();
		for (NbtElement element : list) {
			NbtCompound posNbt = (NbtCompound) element;
			positions.add(new BlockPos(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z")));
		}
		return List.copyOf(positions);
	}
}
