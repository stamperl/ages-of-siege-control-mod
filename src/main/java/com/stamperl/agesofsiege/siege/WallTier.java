package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public enum WallTier {
	NONE("", 0),
	FRAGILE("fragile_walls", 6),
	FORTIFIED("fortified_walls", 12),
	REINFORCED("reinforced_walls", 24);

	private final TagKey<net.minecraft.block.Block> tag;
	private final int hitPoints;

	WallTier(String tagPath, int hitPoints) {
		this.tag = tagPath.isEmpty()
			? null
			: TagKey.of(RegistryKeys.BLOCK, new Identifier(AgesOfSiegeMod.MOD_ID, tagPath));
		this.hitPoints = hitPoints;
	}

	public int getHitPoints() {
		return hitPoints;
	}

	public static WallTier from(BlockState state) {
		if (FRAGILE.tag != null && state.isIn(FRAGILE.tag)) {
			return FRAGILE;
		}
		if (FORTIFIED.tag != null && state.isIn(FORTIFIED.tag)) {
			return FORTIFIED;
		}
		if (REINFORCED.tag != null && state.isIn(REINFORCED.tag)) {
			return REINFORCED;
		}
		return NONE;
	}
}
