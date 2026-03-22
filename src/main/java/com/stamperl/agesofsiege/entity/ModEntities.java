package com.stamperl.agesofsiege.entity;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
	public static final EntityType<SiegeRamEntity> SIEGE_RAM = Registry.register(
		Registries.ENTITY_TYPE,
		new Identifier(AgesOfSiegeMod.MOD_ID, "siege_ram"),
		FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, SiegeRamEntity::new)
			.dimensions(EntityDimensions.fixed(1.95F, 2.2F))
			.trackRangeBlocks(32)
			.trackedUpdateRate(2)
			.build()
	);

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(SIEGE_RAM, SiegeRamEntity.createAttributes());
		AgesOfSiegeMod.LOGGER.info("Registered Ages Of Siege entities.");
	}
}
