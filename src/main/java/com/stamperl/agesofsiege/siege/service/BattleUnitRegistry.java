package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.BattleUnitCatalog;
import com.stamperl.agesofsiege.siege.BattleUnitDefinition;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.siege.MedievalLoadouts;
import com.stamperl.agesofsiege.siege.runtime.BreachCapability;
import com.stamperl.agesofsiege.siege.runtime.SiegeBattlePlan;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

public final class BattleUnitRegistry {
	public static final String BREACH_PRIMARY_TAG = "ages_of_siege_breach_primary";
	public static final String BREACH_FALLBACK_TAG = "ages_of_siege_breach_fallback";
	public static final String BREACH_NONE_TAG = "ages_of_siege_breach_none";
	public static final String WALL_DAMAGE_TAG_PREFIX = "ages_of_siege_wall_damage_";

	private BattleUnitRegistry() {
	}

	public static Optional<SpawnedUnit> spawn(SiegeBattlePlan.UnitGroup group, int loadoutTier, ServerWorld world, Random random) {
		if (group == null) {
			return Optional.empty();
		}
		BattleUnitDefinition definition = BattleUnitCatalog.definition(group.unitKind());
		String entityType = firstNonBlank(group.entityType(), definition == null ? "" : definition.entityType());
		if (entityType.isBlank()) {
			return Optional.empty();
		}
		Entity entity = createEntity(entityType, world);
		if (entity == null) {
			AgesOfSiegeMod.LOGGER.warn("Unsupported siege unit entity '{}', skipping group '{}'.", entityType, group.groupId());
			return Optional.empty();
		}
		String loadoutProfile = firstNonBlank(group.loadoutProfile(), definition == null ? "" : definition.loadoutProfile());
		prepareEntity(entity, loadoutTier, group.role(), loadoutProfile, group.breachCapability(), group.wallDamage(), random);
		return Optional.of(new SpawnedUnit(entity, group.role(), false));
	}

	public static Optional<SpawnedUnit> spawn(SiegeBattlePlan.EngineGroup group, int loadoutTier, ServerWorld world, Random random) {
		if (group == null) {
			return Optional.empty();
		}
		BattleUnitDefinition definition = BattleUnitCatalog.definition(group.engineKind());
		String entityType = firstNonBlank(group.entityType(), definition == null ? "" : definition.entityType());
		if (entityType.isBlank()) {
			return Optional.empty();
		}
		Entity entity = createEntity(entityType, world);
		if (entity == null) {
			AgesOfSiegeMod.LOGGER.warn("Unsupported siege engine entity '{}', skipping group '{}'.", entityType, group.groupId());
			return Optional.empty();
		}
		prepareEngine(entity);
		return Optional.of(new SpawnedUnit(entity, UnitRole.RAM, true));
	}

	private static Entity createEntity(String entityTypeId, ServerWorld world) {
		Identifier id = Identifier.tryParse(entityTypeId);
		if (id == null || !Registries.ENTITY_TYPE.containsId(id)) {
			return null;
		}
		EntityType<?> type = Registries.ENTITY_TYPE.get(id);
		return type.create(world);
	}

	private static void prepareEntity(Entity entity, int loadoutTier, UnitRole role, String loadoutProfile, BreachCapability breachCapability, int wallDamage, Random random) {
		if (!(entity instanceof HostileEntity hostile)) {
			return;
		}
		hostile.setCanPickUpLoot(false);
		hostile.setPersistent();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			hostile.setEquipmentDropChance(slot, 0.0F);
		}

		MedievalLoadouts.RaiderRole raiderRole = resolveLoadoutRole(role, loadoutProfile);
		MedievalLoadouts.equipAttacker(hostile, raiderRole, clampTier(loadoutTier), random);

		if (entity instanceof PillagerEntity pillager) {
			pillager.setCanPickUpLoot(false);
		} else if (entity instanceof VindicatorEntity vindicator) {
			vindicator.setCanPickUpLoot(false);
		}
		applyBreachTags(entity, breachCapability, wallDamage);
	}

	private static MedievalLoadouts.RaiderRole resolveLoadoutRole(UnitRole role, String loadoutProfile) {
		String profile = loadoutProfile == null ? "" : loadoutProfile.trim().toLowerCase();
		if (role == UnitRole.BREACHER || profile.contains("break") || profile.contains("breach")) {
			return MedievalLoadouts.RaiderRole.BREACHER;
		}
		return MedievalLoadouts.RaiderRole.RANGED;
	}

	private static void prepareEngine(Entity entity) {
		if (entity instanceof SiegeRamEntity ram) {
			ram.setCustomName(Text.literal("Battering Ram"));
			ram.setCustomNameVisible(true);
			ram.setAiDisabled(true);
			ram.setPersistent();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ram.setEquipmentDropChance(slot, 0.0F);
			}
		}
	}

	private static int clampTier(int loadoutTier) {
		return Math.max(0, Math.min(loadoutTier, 3));
	}

	private static String firstNonBlank(String primary, String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary.trim();
		}
		return fallback == null ? "" : fallback.trim();
	}

	public record SpawnedUnit(Entity entity, UnitRole role, boolean engine) {
	}

	private static void applyBreachTags(Entity entity, BreachCapability breachCapability, int wallDamage) {
		entity.addCommandTag(switch (breachCapability == null ? BreachCapability.NONE : breachCapability) {
			case PRIMARY -> BREACH_PRIMARY_TAG;
			case FALLBACK -> BREACH_FALLBACK_TAG;
			case NONE -> BREACH_NONE_TAG;
		});
		entity.addCommandTag(WALL_DAMAGE_TAG_PREFIX + Math.max(0, wallDamage));
	}
}
