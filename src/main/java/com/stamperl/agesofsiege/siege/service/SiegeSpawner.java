package com.stamperl.agesofsiege.siege.service;

import com.stamperl.agesofsiege.entity.ModEntities;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.siege.MedievalLoadouts;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SiegeSpawner {
	private static final int MIN_SPAWN_RADIUS = 28;
	private static final int MAX_SPAWN_RADIUS = 36;
	private static final int FORMATION_SPACING = 3;
	private static final String RAM_ESCORT_TAG = "ages_of_siege_ram_escort";
	private static final String RANGED_ROLE_TAG = "ages_of_siege_ranged";
	private static final String BREACHER_ROLE_TAG = "ages_of_siege_breacher";
	private final Random siegeRandom = Random.create();

	public void spawnWave(MinecraftServer server, ServerWorld world, SiegeBaseState state, SiegeSession session) {
		int siegeAgeLevel = session == null ? state.getAgeLevel() : session.getSessionAgeLevel();
		int waveSize = session == null ? getWaveSize(state.getCompletedSieges()) : getWaveSize(session.getSessionVictoryCount());
		spawnWave(server, world, state, session, siegeAgeLevel, waveSize);
	}

	public void spawnWave(MinecraftServer server, ServerWorld world, SiegeBaseState state, SiegeSession session, SiegeCatalog.SiegeDefinition definition) {
		spawnWave(server, world, state, session, definition.combatTier(), definition.waveSize());
	}

	private void spawnWave(MinecraftServer server, ServerWorld world, SiegeBaseState state, SiegeSession session, int siegeAgeLevel, int waveSize) {
		BlockPos basePos = state.getBasePos();
		FormationSpawn formation = createFormationSpawn(world, state, basePos);
		List<UUID> spawnedAttackers = new ArrayList<>();
		List<UUID> spawnedRams = new ArrayList<>();
		Map<UUID, UnitRole> roleAssignments = new HashMap<>();
		for (int i = 0; i < waveSize; i++) {
			BlockPos spawnPos = formation.positionFor(world, i);
			HostileEntity attacker = createAttacker(world, siegeAgeLevel, i);
			if (attacker == null) {
				continue;
			}

			attacker.refreshPositionAndAngles(
				spawnPos.getX() + 0.5D,
				spawnPos.getY(),
				spawnPos.getZ() + 0.5D,
				siegeRandom.nextFloat() * 360.0F,
				0.0F
			);
			attacker.setCanPickUpLoot(false);
			attacker.setPersistent();
			equipAttacker(attacker, siegeAgeLevel);
			world.spawnEntity(attacker);
			spawnedAttackers.add(attacker.getUuid());
			roleAssignments.put(attacker.getUuid(), attacker instanceof VindicatorEntity ? UnitRole.BREACHER : UnitRole.RANGED);
		}

		if (siegeAgeLevel >= 2) {
			BlockPos ramSpawn = formation.ramPosition(world);
			SiegeRamEntity ram = createBatteringRam(world, ramSpawn);
			if (ram != null) {
				world.spawnEntity(ram);
				spawnedRams.add(ram.getUuid());
				roleAssignments.put(ram.getUuid(), UnitRole.RAM);
				spawnedAttackers.addAll(spawnRamEscort(world, siegeAgeLevel, ramSpawn, roleAssignments));
			}
		}

		if (spawnedAttackers.isEmpty() && spawnedRams.isEmpty()) {
			state.endSiege(session != null && session.getPhase() != SiegePhase.STAGED, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}

		state.stageSiegeWave(spawnedAttackers, spawnedRams, roleAssignments, waveSize, BlockPos.ofFloored(formation.center()));
	}

	public void despawnAttackers(ServerWorld world, List<UUID> attackerIds) {
		for (UUID attackerId : attackerIds) {
			Entity entity = world.getEntity(attackerId);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
	}

	public void despawnRams(ServerWorld world, List<UUID> ramIds) {
		for (UUID ramId : ramIds) {
			Entity entity = world.getEntity(ramId);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
	}

	private int getWaveSize(int victoryCount) {
		return 6 + Math.min(victoryCount, 8);
	}

	private HostileEntity createAttacker(ServerWorld world, int siegeAgeLevel, int index) {
		if (siegeAgeLevel <= 0) {
			return EntityType.PILLAGER.create(world);
		}
		if (siegeAgeLevel == 1) {
			return index % 3 == 0 ? EntityType.VINDICATOR.create(world) : EntityType.PILLAGER.create(world);
		}
		if (siegeAgeLevel == 2) {
			return index % 2 == 0 ? EntityType.VINDICATOR.create(world) : EntityType.PILLAGER.create(world);
		}
		return index % 3 == 2 ? EntityType.PILLAGER.create(world) : EntityType.VINDICATOR.create(world);
	}

	private List<UUID> spawnRamEscort(ServerWorld world, int siegeAgeLevel, BlockPos ramSpawn, Map<UUID, UnitRole> roleAssignments) {
		List<UUID> escortIds = new ArrayList<>();
		int escortCount = siegeAgeLevel >= 3 ? 3 : 2;
		for (int i = 0; i < escortCount; i++) {
			HostileEntity escort = i == escortCount - 1
				? EntityType.PILLAGER.create(world)
				: EntityType.VINDICATOR.create(world);
			if (escort == null) {
				continue;
			}

			BlockPos escortPos = ramSpawn.add((i % 2 == 0 ? 2 : -2), 0, i == 0 ? 2 : -2);
			BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, escortPos).up();
			escort.refreshPositionAndAngles(
				top.getX() + 0.5D,
				top.getY(),
				top.getZ() + 0.5D,
				siegeRandom.nextFloat() * 360.0F,
				0.0F
			);
			escort.addCommandTag(RAM_ESCORT_TAG);
			escort.setCanPickUpLoot(false);
			escort.setPersistent();
			equipAttacker(escort, siegeAgeLevel);
			world.spawnEntity(escort);
			escortIds.add(escort.getUuid());
			roleAssignments.put(escort.getUuid(), UnitRole.ESCORT);
		}
		return escortIds;
	}

	private void equipAttacker(HostileEntity attacker, int siegeAgeLevel) {
		MedievalLoadouts.RaiderRole role = attacker instanceof VindicatorEntity
			? MedievalLoadouts.RaiderRole.BREACHER
			: MedievalLoadouts.RaiderRole.RANGED;
		attacker.addCommandTag(role == MedievalLoadouts.RaiderRole.BREACHER ? BREACHER_ROLE_TAG : RANGED_ROLE_TAG);
		MedievalLoadouts.equipAttacker(attacker, role, siegeAgeLevel, attacker.getRandom());
	}

	private SiegeRamEntity createBatteringRam(ServerWorld world, BlockPos spawnPos) {
		SiegeRamEntity ram = ModEntities.SIEGE_RAM.create(world);
		if (ram == null) {
			return null;
		}

		ram.refreshPositionAndAngles(
			spawnPos.getX() + 0.5D,
			spawnPos.getY(),
			spawnPos.getZ() + 0.5D,
			siegeRandom.nextFloat() * 360.0F,
			0.0F
		);
		ram.setCustomName(Text.literal("Battering Ram"));
		ram.setCustomNameVisible(true);
		ram.setAiDisabled(true);
		ram.setPersistent();
		return ram;
	}

	private FormationSpawn createFormationSpawn(ServerWorld world, SiegeBaseState state, BlockPos basePos) {
		BlockPos rallyPoint = state.getRallyPoint();
		if (rallyPoint != null && isRallyMarkerPresent(world, rallyPoint)) {
			Vec3d center = Vec3d.ofCenter(rallyPoint);
			Vec3d forward = Vec3d.ofCenter(basePos).subtract(center);
			if (forward.lengthSquared() > 0.001D) {
				forward = forward.normalize();
				Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
				return new FormationSpawn(center, forward, right);
			}
		}

		if (rallyPoint != null && !isRallyMarkerPresent(world, rallyPoint)) {
			state.setRallyPoint(null);
		}

		double angle = siegeRandom.nextDouble() * (Math.PI * 2.0D);
		int radius = MathHelper.nextInt(siegeRandom, MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
		Vec3d forward = new Vec3d(-Math.cos(angle), 0.0D, -Math.sin(angle)).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		Vec3d center = Vec3d.ofCenter(basePos).add(forward.multiply(-radius));
		return new FormationSpawn(center, forward, right);
	}

	private boolean isRallyMarkerPresent(ServerWorld world, BlockPos rallyPos) {
		return world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_BANNER)
			|| world.getBlockState(rallyPos).isOf(net.minecraft.block.Blocks.RED_WALL_BANNER);
	}

	private record FormationSpawn(Vec3d center, Vec3d forward, Vec3d right) {
		private BlockPos positionFor(ServerWorld world, int index) {
			int row = index / 4;
			int column = index % 4;
			double lateralOffset = (column - 1.5D) * FORMATION_SPACING;
			double depthOffset = row * FORMATION_SPACING;
			Vec3d raw = center.add(right.multiply(lateralOffset)).add(forward.multiply(-depthOffset));
			return grounded(world, raw.x, raw.z);
		}

		private BlockPos ramPosition(ServerWorld world) {
			Vec3d raw = center.add(forward.multiply(FORMATION_SPACING * 2.0D));
			return grounded(world, raw.x, raw.z);
		}

		private BlockPos grounded(ServerWorld world, double x, double z) {
			BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
			return top.up();
		}
	}
}
