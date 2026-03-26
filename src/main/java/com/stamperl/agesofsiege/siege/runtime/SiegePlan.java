package com.stamperl.agesofsiege.siege.runtime;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public record SiegePlan(
	SiegePlanType planType,
	Vec3d approachVector,
	BlockPos stagingPoint,
	BlockPos primaryBreachAnchor,
	List<BlockPos> targetBlocks,
	BlockPos breachExit,
	boolean objectiveReachableAfterBreach,
	int openingWidth,
	int openingHeight,
	float confidence,
	long expiresAtTick
) {
	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("planType", planType.name());
		nbt.putDouble("approachX", approachVector.x);
		nbt.putDouble("approachY", approachVector.y);
		nbt.putDouble("approachZ", approachVector.z);
		writeBlockPos(nbt, "staging", stagingPoint);
		writeNullableBlockPos(nbt, "primaryBreachAnchor", primaryBreachAnchor);
		writeNullableBlockPos(nbt, "breachExit", breachExit);
		nbt.putBoolean("objectiveReachableAfterBreach", objectiveReachableAfterBreach);
		nbt.putInt("openingWidth", openingWidth);
		nbt.putInt("openingHeight", openingHeight);
		nbt.putFloat("confidence", confidence);
		nbt.putLong("expiresAtTick", expiresAtTick);
		NbtList targetList = new NbtList();
		for (BlockPos targetBlock : targetBlocks) {
			NbtCompound targetNbt = new NbtCompound();
			writeBlockPos(targetNbt, "pos", targetBlock);
			targetList.add(targetNbt);
		}
		nbt.put("targetBlocks", targetList);
		return nbt;
	}

	public static SiegePlan fromNbt(NbtCompound nbt) {
		List<BlockPos> targetBlocks = new ArrayList<>();
		NbtList targetList = nbt.getList("targetBlocks", NbtElement.COMPOUND_TYPE);
		for (NbtElement element : targetList) {
			NbtCompound targetNbt = (NbtCompound) element;
			targetBlocks.add(readBlockPos(targetNbt, "pos"));
		}
		return new SiegePlan(
			SiegePlanType.valueOf(nbt.getString("planType")),
			new Vec3d(nbt.getDouble("approachX"), nbt.getDouble("approachY"), nbt.getDouble("approachZ")),
			readBlockPos(nbt, "staging"),
			readNullableBlockPos(nbt, "primaryBreachAnchor"),
			List.copyOf(targetBlocks),
			readNullableBlockPos(nbt, "breachExit"),
			nbt.getBoolean("objectiveReachableAfterBreach"),
			nbt.getInt("openingWidth"),
			nbt.getInt("openingHeight"),
			nbt.getFloat("confidence"),
			nbt.getLong("expiresAtTick")
		);
	}

	private static void writeNullableBlockPos(NbtCompound nbt, String key, BlockPos pos) {
		if (pos == null) {
			return;
		}
		writeBlockPos(nbt, key, pos);
	}

	private static void writeBlockPos(NbtCompound nbt, String key, BlockPos pos) {
		nbt.putInt(key + "X", pos.getX());
		nbt.putInt(key + "Y", pos.getY());
		nbt.putInt(key + "Z", pos.getZ());
	}

	private static BlockPos readNullableBlockPos(NbtCompound nbt, String key) {
		if (!nbt.contains(key + "X")) {
			return null;
		}
		return readBlockPos(nbt, key);
	}

	private static BlockPos readBlockPos(NbtCompound nbt, String key) {
		return new BlockPos(nbt.getInt(key + "X"), nbt.getInt(key + "Y"), nbt.getInt(key + "Z"));
	}
}
