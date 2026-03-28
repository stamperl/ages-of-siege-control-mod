package com.stamperl.agesofsiege.state;

import com.stamperl.agesofsiege.defense.DefenderRole;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public record PlacedDefender(
	UUID entityUuid,
	String dimensionId,
	DefenderRole role,
	BlockPos homePost,
	float homeYaw,
	double leashRadius,
	BlockPos settlementBannerPos,
	String settlementDimensionId,
	String ownerName,
	UUID ownerUuid,
	String defenderName
) {
	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putUuid("entityUuid", entityUuid);
		nbt.putString("dimensionId", dimensionId);
		nbt.putString("role", role.name());
		nbt.putInt("homeX", homePost.getX());
		nbt.putInt("homeY", homePost.getY());
		nbt.putInt("homeZ", homePost.getZ());
		nbt.putFloat("homeYaw", homeYaw);
		nbt.putDouble("leashRadius", leashRadius);
		if (settlementBannerPos != null) {
			nbt.putInt("bannerX", settlementBannerPos.getX());
			nbt.putInt("bannerY", settlementBannerPos.getY());
			nbt.putInt("bannerZ", settlementBannerPos.getZ());
		}
		if (settlementDimensionId != null) {
			nbt.putString("settlementDimensionId", settlementDimensionId);
		}
		if (ownerName != null) {
			nbt.putString("ownerName", ownerName);
		}
		if (ownerUuid != null) {
			nbt.putUuid("ownerUuid", ownerUuid);
		}
		if (defenderName != null) {
			nbt.putString("defenderName", defenderName);
		}
		return nbt;
	}

	public static PlacedDefender fromNbt(NbtCompound nbt) {
		BlockPos bannerPos = nbt.contains("bannerX")
			? new BlockPos(nbt.getInt("bannerX"), nbt.getInt("bannerY"), nbt.getInt("bannerZ"))
			: null;
		return new PlacedDefender(
			nbt.getUuid("entityUuid"),
			nbt.getString("dimensionId"),
			DefenderRole.valueOf(nbt.getString("role")),
			new BlockPos(nbt.getInt("homeX"), nbt.getInt("homeY"), nbt.getInt("homeZ")),
			nbt.contains("homeYaw") ? nbt.getFloat("homeYaw") : 0.0F,
			nbt.getDouble("leashRadius"),
			bannerPos,
			nbt.contains("settlementDimensionId") ? nbt.getString("settlementDimensionId") : null,
			nbt.contains("ownerName") ? nbt.getString("ownerName") : null,
			nbt.containsUuid("ownerUuid") ? nbt.getUuid("ownerUuid") : null,
			nbt.contains("defenderName") ? nbt.getString("defenderName") : null
		);
	}
}
