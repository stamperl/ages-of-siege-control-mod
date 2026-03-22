package com.stamperl.agesofsiege.block;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Block SETTLEMENT_STANDARD = register(
		"settlement_standard",
		new SettlementStandardBlock(
			AbstractBlock.Settings.create()
				.mapColor(MapColor.OAK_TAN)
				.strength(2.5F, 6.0F)
				.sounds(BlockSoundGroup.WOOD)
				.pistonBehavior(PistonBehavior.BLOCK)
		)
	);

	private ModBlocks() {
	}

	public static void register() {
		AgesOfSiegeMod.LOGGER.info("Registered Ages Of Siege blocks.");
	}

	private static Block register(String path, Block block) {
		return Registry.register(Registries.BLOCK, new Identifier(AgesOfSiegeMod.MOD_ID, path), block);
	}
}
