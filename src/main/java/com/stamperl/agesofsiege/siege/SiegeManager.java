package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.entity.ModEntities;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SiegeManager {
	private static final boolean DEBUG_LOGGING = true;
	private static final int MIN_SPAWN_RADIUS = 28;
	private static final int MAX_SPAWN_RADIUS = 36;
	private static final double PLAYER_AGGRO_RANGE = 10.0D;
	private static final double SUPPORT_PLAYER_AGGRO_RANGE = 18.0D;
	private static final double OBJECTIVE_ATTACK_RANGE = 2.25D;
	private static final double BREACH_CHECK_RANGE = 3.0D;
	private static final double BREACH_SLOT_RADIUS = 1.75D;
	private static final double BREACH_SIDESTEP_DISTANCE = 1.5D;
	private static final double SUPPORT_FORMATION_RADIUS = 6.0D;
	private static final double SUPPORT_FORMATION_DEPTH = 4.0D;
	private static final double BREACHER_GUARD_RADIUS = 3.0D;
	private static final double BREACHER_GUARD_DEPTH = 2.0D;
	private static final double MAX_BREACH_SCAN_DEPTH = 96.0D;
	private static final double ASSAULT_LANE_CHECK_DISTANCE = 250.0D;
	private static final int INFANTRY_PATH_SEARCH_LIMIT = 4096;
	private static final double LANE_CHECK_INITIAL_SKIP = 6.0D;
	private static final double[] BREACH_LANES = {-6.0D, -3.0D, 0.0D, 3.0D, 6.0D};
	private static final double[] LANE_WIDTH_SAMPLES = {-2.0D, -1.0D, 0.0D, 1.0D, 2.0D};
	private static final double[] INFANTRY_LANE_WIDTH_SAMPLES = {-0.75D, 0.0D, 0.75D};
	private static final double INFANTRY_APPROACH_STEP = 2.0D;
	private static final double INFANTRY_MAX_STEP_UP = 0.75D;
	private static final double RAM_MOVE_STEP = 0.06D;
	private static final double RAM_BACKOFF_DISTANCE = 4.0D;
	private static final int RAM_BACKOFF_TICKS = 60;
	private static final double RAM_ATTACK_RANGE = 3.2D;
	private static final int RAM_ATTACK_INTERVAL_TICKS = 30;
	private static final int RAM_BASE_ATTACK_DAMAGE = 25;
	private static final int RAM_SPLASH_ATTACK_DAMAGE = 12;
	private static final int RAM_MAX_ATTACK_BONUS = 20;
	private static final double RAM_ESCORT_DEFEND_RANGE = 7.0D;
	private static final double RAM_ESCORT_LEASH_RANGE = 4.0D;
	private static final double PLAYER_CHASE_WALL_CHECK_DEPTH = 16.0D;
	private static final String RAM_ESCORT_TAG = "ages_of_siege_ram_escort";
	private static final String RANGED_ROLE_TAG = "ages_of_siege_ranged";
	private static final String BREACHER_ROLE_TAG = "ages_of_siege_breacher";
	private static final int FORMATION_SPACING = 3;

	private enum AssaultMode {
		RUSH_BANNER,
		SUPPORT_RAM,
		INFANTRY_BREACH
	}

	private record AssaultDecision(
		AssaultMode mode,
		UUID leadUnitId,
		BlockPos primaryBreachTarget,
		boolean pathReachable,
		boolean activeRam,
		boolean breachTeamAlive
	) {
	}

	private record InfantryPathResult(boolean reachable, BlockPos startFoot, int explored, BlockPos frontierTarget) {
	}

	private record PathNode(BlockPos pos, int steps) {
	}

	private record BreachGridPlan(BlockPos anchorBase, Vec3d forward, Vec3d right, List<BlockPos> cells) {
	}

	private SiegeManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SiegeManager::tickServer);
	}

	private static void tickServer(MinecraftServer server) {
		SiegeBaseState state = SiegeBaseState.get(server);
		if (!state.hasBase()) {
			return;
		}

		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			state.endSiege(true, false);
			return;
		}

		BlockPos objectivePos = state.getBasePos();
		if (!isObjectivePresent(world, objectivePos)) {
			failActiveSiege(world, state, "The Settlement Standard was destroyed. The siege is lost.");
			return;
		}

		if (state.isSiegePending()) {
			tickCountdown(server, state);
			return;
		}

		if (!state.isSiegeActive()) {
			return;
		}

		AssaultDecision decision = evaluateAssaultDecision(world, state, objectivePos);
		AssaultMode assaultMode = decision.mode();
		state.setBreachOpen(assaultMode == AssaultMode.RUSH_BANNER);
		state.setRushTicks(assaultMode == AssaultMode.RUSH_BANNER ? state.getRushTicks() + 1 : 0);
		if (DEBUG_LOGGING && world.getTime() % 20L == 0L) {
			AgesOfSiegeMod.LOGGER.info(
				"[SiegeDebug] mode={} age={} attackers={} rams={} breachOpen={} rushTicks={} activeBreachTeam={} breachedWalls={}",
				assaultMode,
				state.getAgeLevel(),
				state.getAttackerIds().size(),
				state.getRamIds().size(),
				state.isBreachOpen(),
				state.getRushTicks(),
				hasActiveBreachTeam(world, state),
				state.getBreachedWallBlocks()
			);
		}

		List<UUID> livingAttackers = new ArrayList<>();
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}

			livingAttackers.add(attackerId);
			updateAttacker(hostile, world, objectivePos, state, decision);
		}

		state.replaceAttackers(livingAttackers);

		List<UUID> livingRams = new ArrayList<>();
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}

			livingRams.add(ramId);
			updateRam(ram, world, objectivePos, decision);
		}

		state.replaceRams(livingRams);

		if (livingAttackers.isEmpty() && livingRams.isEmpty()) {
			int previousAge = state.getAgeLevel();
			state.endSiege(false, true);
			dropVictoryReward(world, objectivePos);
			server.getPlayerManager().broadcast(Text.literal("The siege wave has been defeated."), false);
			if (state.getAgeLevel() > previousAge) {
				server.getPlayerManager().broadcast(
					Text.literal("Age advanced: " + state.getAgeName() + " unlocked."),
					false
				);
			}
			sendAgeProgressMessage(server, state);
		}
	}

	private static void tickCountdown(MinecraftServer server, SiegeBaseState state) {
		int remainingTicks = state.tickCountdown();
		if (remainingTicks <= 0) {
			spawnWave(server, state);
			return;
		}

		int remainingSeconds = remainingTicks / 20;
		if (remainingTicks % 20 == 0 && (remainingSeconds <= 5 || remainingSeconds == 10)) {
			server.getPlayerManager().broadcast(
				Text.literal("Siege begins in " + remainingSeconds + " seconds."),
				false
			);
		}
	}

	private static void updateAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, SiegeBaseState state, AssaultDecision decision) {
		suppressPotionUse(hostile);
		if (hostile.getCommandTags().contains(RAM_ESCORT_TAG)) {
			updateRamEscort(hostile, world, state, decision);
			return;
		}

		if (hostile.getCommandTags().contains(RANGED_ROLE_TAG)) {
			updateRangedAttacker(hostile, world, objectivePos, state, decision);
			return;
		}

		updateBreacherAttacker(hostile, world, objectivePos, decision);
	}

	public static boolean startSiege(MinecraftServer server, SiegeBaseState state) {
		if (!state.hasBase() || state.isSiegeActive() || state.isSiegePending()) {
			return false;
		}

		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			return false;
		}

		if (!isObjectivePresent(world, state.getBasePos())) {
			return false;
		}

		state.beginCountdown(server, 10);
		return true;
	}

	public static void failActiveSiege(ServerWorld world, SiegeBaseState state, String message) {
		despawnAttackers(world, state.getAttackerIds());
		despawnRams(world, state.getRamIds());
		state.endSiege(true, false);
		world.getServer().getPlayerManager().broadcast(Text.literal(message), false);
	}

	private static void spawnWave(MinecraftServer server, SiegeBaseState state) {
		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			state.endSiege(true, false);
			return;
		}

		BlockPos basePos = state.getBasePos();
		int waveSize = getWaveSize(state);
		FormationSpawn formation = createFormationSpawn(world, basePos);
		List<UUID> spawnedAttackers = new ArrayList<>();
		List<UUID> spawnedRams = new ArrayList<>();
		for (int i = 0; i < waveSize; i++) {
			BlockPos spawnPos = formation.positionFor(world, i);
			HostileEntity attacker = createAttacker(world, state, i);
			if (attacker == null) {
				continue;
			}

			attacker.refreshPositionAndAngles(
				spawnPos.getX() + 0.5D,
				spawnPos.getY(),
				spawnPos.getZ() + 0.5D,
				world.random.nextFloat() * 360.0F,
				0.0F
			);
			attacker.setCanPickUpLoot(false);
			attacker.setPersistent();
			equipAttacker(attacker, state);
			world.spawnEntity(attacker);
			spawnedAttackers.add(attacker.getUuid());
		}

		if (state.getAgeLevel() >= 2) {
			BlockPos ramSpawn = formation.ramPosition(world);
			SiegeRamEntity ram = createBatteringRam(world, ramSpawn);
			if (ram != null) {
				world.spawnEntity(ram);
				spawnedRams.add(ram.getUuid());
				spawnedAttackers.addAll(spawnRamEscort(world, state, ramSpawn));
			}
		}

		if (spawnedAttackers.isEmpty() && spawnedRams.isEmpty()) {
			state.endSiege(true, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}

		state.startSiege(server, spawnedAttackers, spawnedRams, waveSize, BlockPos.ofFloored(formation.center()));
		sendAgeProgressMessage(server, state);
	}

	private static void despawnAttackers(ServerWorld world, List<UUID> attackerIds) {
		for (UUID attackerId : attackerIds) {
			Entity entity = world.getEntity(attackerId);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
	}

	private static void despawnRams(ServerWorld world, List<UUID> ramIds) {
		for (UUID ramId : ramIds) {
			Entity entity = world.getEntity(ramId);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
		}
	}

	private static void dropVictoryReward(ServerWorld world, BlockPos objectivePos) {
		SiegeBaseState state = SiegeBaseState.get(world.getServer());
		spawnReward(world, objectivePos, new ItemStack(Items.BREAD, 4));
		spawnReward(world, objectivePos, new ItemStack(Items.IRON_INGOT, 6 + (state.getAgeLevel() * 2)));
		for (ItemStack stack : MedievalLoadouts.getVictoryRewards(state.getAgeLevel(), world.random)) {
			spawnReward(world, objectivePos, stack);
		}

		if (state.getAgeLevel() >= 1) {
			spawnReward(world, objectivePos, new ItemStack(Items.ARROW, 12));
			spawnReward(world, objectivePos, new ItemStack(Items.BOW, 1));
		}

		if (state.getAgeLevel() >= 2) {
			spawnReward(world, objectivePos, new ItemStack(Items.REDSTONE, 8));
			spawnReward(world, objectivePos, new ItemStack(Items.BLAST_FURNACE, 1));
		}

		if (state.getAgeLevel() >= 3) {
			spawnReward(world, objectivePos, new ItemStack(Items.LIGHTNING_ROD, 4));
			spawnReward(world, objectivePos, new ItemStack(Items.CROSSBOW, 1));
		}
	}

	private static void spawnReward(ServerWorld world, BlockPos objectivePos, ItemStack stack) {
		ItemEntity reward = new ItemEntity(
			world,
			objectivePos.getX() + 0.5D,
			objectivePos.getY() + 1.0D,
			objectivePos.getZ() + 0.5D,
			stack
		);
		world.spawnEntity(reward);
	}

	private static int getWaveSize(SiegeBaseState state) {
		// Scale waves from actual victories, not failed attempts.
		// Age unlocks still change composition and rewards, but each win should
		// make the next siege feel tougher even within the same age tier.
		return 6 + Math.min(state.getCompletedSieges(), 8);
	}

	private static HostileEntity createAttacker(ServerWorld world, SiegeBaseState state, int index) {
		int age = state.getAgeLevel();
		if (age <= 0) {
			return EntityType.PILLAGER.create(world);
		}
		if (age == 1) {
			return index % 3 == 0 ? EntityType.VINDICATOR.create(world) : EntityType.PILLAGER.create(world);
		}
		if (age == 2) {
			return index % 2 == 0 ? EntityType.VINDICATOR.create(world) : EntityType.PILLAGER.create(world);
		}
		return index % 3 == 2 ? EntityType.PILLAGER.create(world) : EntityType.VINDICATOR.create(world);
	}

	private static List<UUID> spawnRamEscort(ServerWorld world, SiegeBaseState state, BlockPos ramSpawn) {
		List<UUID> escortIds = new ArrayList<>();
		int escortCount = state.getAgeLevel() >= 3 ? 3 : 2;
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
				world.random.nextFloat() * 360.0F,
				0.0F
			);
			escort.addCommandTag(RAM_ESCORT_TAG);
			escort.setCanPickUpLoot(false);
			escort.setPersistent();
			equipAttacker(escort, state);
			world.spawnEntity(escort);
			escortIds.add(escort.getUuid());
		}
		return escortIds;
	}

	private static void equipAttacker(HostileEntity attacker, SiegeBaseState state) {
		MedievalLoadouts.RaiderRole role = attacker instanceof VindicatorEntity
			? MedievalLoadouts.RaiderRole.BREACHER
			: MedievalLoadouts.RaiderRole.RANGED;
		attacker.addCommandTag(role == MedievalLoadouts.RaiderRole.BREACHER ? BREACHER_ROLE_TAG : RANGED_ROLE_TAG);
		MedievalLoadouts.equipAttacker(attacker, role, state.getAgeLevel(), attacker.getRandom());
	}

	private static SiegeRamEntity createBatteringRam(ServerWorld world, BlockPos spawnPos) {
		SiegeRamEntity ram = ModEntities.SIEGE_RAM.create(world);
		if (ram == null) {
			return null;
		}

		ram.refreshPositionAndAngles(
			spawnPos.getX() + 0.5D,
			spawnPos.getY(),
			spawnPos.getZ() + 0.5D,
			world.random.nextFloat() * 360.0F,
			0.0F
		);
		ram.setCustomName(Text.literal("Battering Ram"));
		ram.setCustomNameVisible(true);
		ram.setAiDisabled(true);
		ram.setPersistent();
		return ram;
	}

	private static void updateRam(SiegeRamEntity ram, ServerWorld world, BlockPos objectivePos, AssaultDecision decision) {
		SiegeBaseState state = SiegeBaseState.get(world.getServer());
		Vec3d ramPos = ram.getPos();
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		AssaultMode assaultMode = decision.mode();
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			ram.setBreachTarget(null);
			if (state.getRushTicks() <= RAM_BACKOFF_TICKS) {
				Vec3d retreatDirection = ramPos.subtract(objectiveCenter);
				if (retreatDirection.lengthSquared() > 0.0001D) {
					Vec3d retreatTarget = ramPos.add(retreatDirection.normalize().multiply(RAM_BACKOFF_DISTANCE));
					moveRam(ram, world, ramPos, retreatTarget.subtract(ramPos).normalize());
					faceTowards(ram, objectiveCenter);
					return;
				}
			}
			ram.setVelocity(0.0D, 0.0D, 0.0D);
			faceTowards(ram, objectiveCenter);
			return;
		}
		BlockPos ramTarget = decision.primaryBreachTarget() != null ? decision.primaryBreachTarget() : getEffectiveRamTarget(world, ram, objectivePos);
		Vec3d targetCenter = ramTarget != null ? Vec3d.ofCenter(ramTarget) : objectiveCenter;
		Vec3d flatDirection = new Vec3d(targetCenter.x - ramPos.x, 0.0D, targetCenter.z - ramPos.z);
		if (flatDirection.lengthSquared() < 0.0001D) {
			return;
		}

		Vec3d normalized = flatDirection.normalize();
		if (ramTarget != null) {
			if (ram.squaredDistanceTo(Vec3d.ofCenter(ramTarget)) <= RAM_ATTACK_RANGE * RAM_ATTACK_RANGE) {
				faceTowards(ram, targetCenter);
				if (world.getTime() % RAM_ATTACK_INTERVAL_TICKS == 0L) {
					applyRamImpact(state, world, ram, ramTarget);
				}
			} else {
				moveRam(ram, world, ramPos, normalized);
			}
			return;
		}

		if (ram.squaredDistanceTo(targetCenter) <= RAM_ATTACK_RANGE * RAM_ATTACK_RANGE) {
			faceTowards(ram, targetCenter);
			if (world.getTime() % RAM_ATTACK_INTERVAL_TICKS == 0L) {
				state.damageObjective(world, 4);
			}
			return;
		}

		moveRam(ram, world, ramPos, normalized);
	}

	private static void moveRam(SiegeRamEntity ram, ServerWorld world, Vec3d ramPos, Vec3d direction) {
		double nextX = ramPos.x + direction.x * RAM_MOVE_STEP;
		double nextZ = ramPos.z + direction.z * RAM_MOVE_STEP;
		double currentY = getGroundY(world, ramPos.x, ramPos.z);
		double nextY = getGroundY(world, nextX, nextZ);
		if (nextY > currentY + 1.0D || isBlockedByWallTop(world, nextX, nextY, nextZ) || !hasRoomForRam(ram, world, nextX, nextY, nextZ)) {
			return;
		}
		float yaw = (float) (MathHelper.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
		ram.refreshPositionAndAngles(nextX, nextY, nextZ, yaw, 0.0F);
	}

	private static void updateRamEscort(HostileEntity escort, ServerWorld world, SiegeBaseState state, AssaultDecision decision) {
		AssaultMode assaultMode = decision.mode();
		SiegeRamEntity ram = getNearestRam(world, state, escort.getPos());
		if (ram == null) {
			escort.setTarget(null);
			if (!hasActiveBreachTeam(world, state) && tryFallbackWallAssault(escort, world, state.getBasePos(), 1, 40)) {
				return;
			}
			return;
		}

		PlayerEntity playerTarget = world.getClosestPlayer(
			ram.getX(),
			ram.getY(),
			ram.getZ(),
			RAM_ESCORT_DEFEND_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, escort.getPos(), candidate.getPos())
		);

		if (playerTarget != null) {
			escort.setTarget(playerTarget);
			moveInfantryToward(escort, world, playerTarget.getPos(), 1.0D);
			return;
		}

		escort.setTarget(null);
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			advanceOnObjective(escort, world, state.getBasePos(), 1);
			return;
		}
		if (!hasActiveBreachTeam(world, state) && tryFallbackWallAssault(escort, world, state.getBasePos(), 1, 40)) {
			return;
		}
		Vec3d ramPos = ram.getPos();
		if (escort.squaredDistanceTo(ramPos) > RAM_ESCORT_LEASH_RANGE * RAM_ESCORT_LEASH_RANGE) {
			moveInfantryToward(escort, world, ramPos, 1.0D);
		} else {
			escort.getNavigation().stop();
		}
	}

	private static void updateBreacherAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, AssaultDecision decision) {
		AssaultMode assaultMode = decision.mode();
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		PlayerEntity playerTarget = world.getClosestPlayer(
			hostile.getX(),
			hostile.getY(),
			hostile.getZ(),
			PLAYER_AGGRO_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, hostile.getPos(), candidate.getPos())
		);

		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			moveInfantryToward(hostile, world, playerTarget.getPos(), 1.0D);
			return;
		}

		hostile.setTarget(null);
		SiegeRamEntity ram = getNearestLiveRam(world, hostile.getPos());
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			advanceOnObjective(hostile, world, objectivePos, 1);
			return;
		}

		if (assaultMode == AssaultMode.SUPPORT_RAM && ram != null) {
			Vec3d guardPosition = getBreacherGuardPosition(world, hostile, objectivePos, ram);
			if (hostile.squaredDistanceTo(guardPosition) > 3.0D) {
				moveInfantryToward(hostile, world, guardPosition, 1.0D);
			} else {
				hostile.getNavigation().stop();
				faceTowards(hostile, Vec3d.ofCenter(objectivePos));
			}
			return;
		}

		BlockPos breachAnchor = decision.primaryBreachTarget() != null
			? decision.primaryBreachTarget()
			: findBestRamBreachTarget(world, hostile.getPos(), objectivePos);
		BreachGridPlan breachPlan = breachAnchor != null ? buildBreachGridPlan(world, breachAnchor, objectivePos) : null;
		BlockPos breachTarget = getAssignedBreachFaceTarget(breachPlan, hostile);
		Vec3d breachApproach = getAssignedBreachApproachPosition(world, breachPlan, hostile, breachTarget);
		if (DEBUG_LOGGING && world.getTime() % 40L == 0L) {
			AgesOfSiegeMod.LOGGER.info(
				"[SiegeDebug] breacher={} mode={} breachAnchor={} breachTarget={} breachApproach={} pos={}",
				hostile.getType().getUntranslatedName(),
				assaultMode,
				breachAnchor,
				breachTarget,
				breachApproach,
				hostile.getBlockPos()
			);
		}
		if (breachTarget != null && breachApproach != null && tryAttackWallTarget(hostile, world, breachTarget, breachApproach, 1, 15)) {
			return;
		}

		advanceOnObjective(hostile, world, objectivePos, 1);
	}

	private static void updateRangedAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, SiegeBaseState state, AssaultDecision decision) {
		AssaultMode assaultMode = decision.mode();
		if (assaultMode == AssaultMode.RUSH_BANNER) {
			PlayerEntity playerTarget = world.getClosestPlayer(
				hostile.getX(),
				hostile.getY(),
				hostile.getZ(),
				SUPPORT_PLAYER_AGGRO_RANGE,
				candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, hostile.getPos(), candidate.getPos())
			);
			if (playerTarget != null) {
				hostile.setTarget(playerTarget);
				return;
			}

			hostile.setTarget(null);
			advanceOnObjective(hostile, world, objectivePos, 1);
			return;
		}

		if (!hasActiveBreachTeam(world, state) && tryFallbackWallAssault(hostile, world, objectivePos, 1, 40)) {
			if (DEBUG_LOGGING && world.getTime() % 40L == 0L) {
				AgesOfSiegeMod.LOGGER.info(
					"[SiegeDebug] fallback-wall-assault attacker={} pos={}",
					hostile.getType().getUntranslatedName(),
					hostile.getBlockPos()
				);
			}
			return;
		}

		Vec3d supportFocus = getSupportFocus(world, hostile, objectivePos, state);
		PlayerEntity playerTarget = world.getClosestPlayer(
			supportFocus.x,
			supportFocus.y,
			supportFocus.z,
			SUPPORT_PLAYER_AGGRO_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator() && hasClearGroundApproach(world, hostile.getPos(), candidate.getPos())
		);

		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			if (hostile.squaredDistanceTo(playerTarget) < 36.0D) {
				Vec3d retreat = hostile.getPos().subtract(playerTarget.getPos()).normalize();
				Vec3d retreatPos = hostile.getPos().add(retreat.multiply(3.0D));
				moveInfantryToward(hostile, world, retreatPos, 1.0D);
			}
			return;
		}

		hostile.setTarget(null);
		Vec3d stagingPos = getSupportFormationPosition(world, hostile, objectivePos, supportFocus);
		if (hostile.squaredDistanceTo(stagingPos) > 4.0D) {
			moveInfantryToward(hostile, world, stagingPos, 1.0D);
		} else {
			hostile.getNavigation().stop();
			faceTowards(hostile, Vec3d.ofCenter(objectivePos));
		}
	}

	private static AssaultDecision evaluateAssaultDecision(ServerWorld world, SiegeBaseState state, BlockPos objectivePos) {
		if (!state.isAssaultModePrimed()) {
			state.setAssaultModePrimed(true);
			return new AssaultDecision(
				getDefaultAssaultMode(state),
				null,
				null,
				false,
				!state.getRamIds().isEmpty(),
				hasActiveBreachTeam(world, state)
			);
		}

		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		HostileEntity furthestBreacher = getFurthestBreacher(world, state, objectiveCenter);
		SiegeRamEntity furthestRam = getFurthestRam(world, state, objectiveCenter);
		HostileEntity furthestAttacker = getFurthestAttacker(world, state, objectiveCenter);
		Vec3d pathSource = state.getAssaultOrigin() != null ? Vec3d.ofCenter(state.getAssaultOrigin()) : null;
		Entity pathLead = furthestBreacher != null ? furthestBreacher : (furthestAttacker != null ? furthestAttacker : furthestRam);
		boolean breachTeamAlive = hasActiveBreachTeam(world, state);
		if (pathLead == null) {
			return new AssaultDecision(AssaultMode.RUSH_BANNER, null, null, true, furthestRam != null, breachTeamAlive);
		}
		if (pathSource == null) {
			pathSource = pathLead.getPos();
		}

		BlockPos primaryBreachTarget = getStablePrimaryBreachTarget(world, state, objectivePos, furthestRam, pathSource);
		Vec3d reachabilitySource = primaryBreachTarget != null && WallTier.from(world.getBlockState(primaryBreachTarget)) == WallTier.NONE
			? Vec3d.ofCenter(primaryBreachTarget)
			: pathSource;
		InfantryPathResult pathResult = findInfantryPath(world, reachabilitySource, objectiveCenter, ASSAULT_LANE_CHECK_DISTANCE, 2);
		if (!pathResult.reachable() && furthestRam == null && pathResult.frontierTarget() != null) {
			primaryBreachTarget = getWallBase(pathResult.frontierTarget(), world);
		}
		if (pathResult.reachable()) {
			state.setPrimaryBreachTarget(null);
		}
		if (pathResult.reachable()) {
			logAssaultDecision(world, pathLead, reachabilitySource, pathResult, furthestRam != null, primaryBreachTarget, AssaultMode.RUSH_BANNER);
			return new AssaultDecision(AssaultMode.RUSH_BANNER, pathLead.getUuid(), primaryBreachTarget, true, furthestRam != null, breachTeamAlive);
		}
		if (primaryBreachTarget != null) {
			state.setPrimaryBreachTarget(primaryBreachTarget);
		}

		AssaultMode blockedMode = furthestRam != null ? AssaultMode.SUPPORT_RAM : AssaultMode.INFANTRY_BREACH;
		logAssaultDecision(world, pathLead, reachabilitySource, pathResult, furthestRam != null, primaryBreachTarget, blockedMode);
		return new AssaultDecision(blockedMode, pathLead.getUuid(), primaryBreachTarget, false, furthestRam != null, breachTeamAlive);
	}

	private static BlockPos getStablePrimaryBreachTarget(ServerWorld world, SiegeBaseState state, BlockPos objectivePos, SiegeRamEntity furthestRam, Vec3d pathSource) {
		BlockPos current = state.getPrimaryBreachTarget();
		if (current != null) {
			BlockPos anchorBase = getWallBase(current, world);
			if (isBreachZoneActive(world, anchorBase, objectivePos)) {
				return anchorBase;
			}
		}
		BlockPos next = furthestRam != null
			? getEffectiveRamTarget(world, furthestRam, objectivePos)
			: findBestRamBreachTarget(world, pathSource, objectivePos);
		state.setPrimaryBreachTarget(next);
		return next;
	}

	private static BlockPos getAssignedBreachFaceTarget(BreachGridPlan plan, HostileEntity hostile) {
		if (plan == null || plan.cells().isEmpty()) {
			return null;
		}

		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3) - 1;
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		Vec3d anchorCenter = Vec3d.ofCenter(plan.anchorBase());
		for (BlockPos cell : plan.cells()) {
			Vec3d offset = Vec3d.ofCenter(cell).subtract(anchorCenter);
			double lateral = offset.dotProduct(plan.right());
			double depth = offset.dotProduct(plan.forward());
			double height = cell.getY() - plan.anchorBase().getY();
			double score = Math.abs(lateral - slot) * 2.0D + depth * 32.0D + height * 3.0D;
			if (best == null || score < bestScore) {
				best = cell;
				bestScore = score;
			}
		}
		return best;
	}

	private static Vec3d getAssignedBreachApproachPosition(ServerWorld world, BreachGridPlan plan, HostileEntity hostile, BlockPos target) {
		if (plan == null || target == null) {
			return null;
		}

		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3) - 1;
		Vec3d targetCenter = Vec3d.ofCenter(target);
		Vec3d baseApproach = targetCenter
			.subtract(plan.forward().multiply(1.85D))
			.add(plan.right().multiply(slot * (BREACH_SLOT_RADIUS * 0.8D)));
		double groundedY = getGroundY(world, baseApproach.x, baseApproach.z);
		return new Vec3d(baseApproach.x, groundedY, baseApproach.z);
	}

	private static boolean isBreachZoneActive(ServerWorld world, BlockPos anchorBase, BlockPos objectivePos) {
		return !buildBreachGridPlan(world, anchorBase, objectivePos).cells().isEmpty();
	}

	private static BlockPos findNearestWallAroundAnchor(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		BreachGridPlan plan = buildBreachGridPlan(world, breachAnchor, objectivePos);
		return plan.cells().isEmpty() ? null : plan.cells().get(0);
	}

	private static Vec3d[] getBreachAxes(BlockPos breachAnchor, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d anchorCenter = Vec3d.ofCenter(breachAnchor);
		Vec3d forward = new Vec3d(objectiveCenter.x - anchorCenter.x, 0.0D, objectiveCenter.z - anchorCenter.z);
		if (forward.lengthSquared() < 0.001D) {
			forward = new Vec3d(1.0D, 0.0D, 0.0D);
		}
		forward = forward.normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		return new Vec3d[] {forward, right};
	}

	private static BreachGridPlan buildBreachGridPlan(ServerWorld world, BlockPos breachAnchor, BlockPos objectivePos) {
		Vec3d[] axes = getBreachAxes(breachAnchor, objectivePos);
		Vec3d forward = axes[0];
		Vec3d right = axes[1];
		int baseHeight = Math.max(2, getRamWallHeight(world, breachAnchor));
		List<BlockPos> cells = new ArrayList<>();
		for (int depth = 0; depth <= 3; depth++) {
			for (int lateral = -2; lateral <= 2; lateral++) {
				BlockPos lateralBase = breachAnchor.add(
					MathHelper.floor(right.x * lateral + forward.x * depth),
					0,
					MathHelper.floor(right.z * lateral + forward.z * depth)
				);
				for (int yOffset = 0; yOffset <= baseHeight; yOffset++) {
					BlockPos candidate = lateralBase.up(yOffset);
					if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
						cells.add(candidate.toImmutable());
					}
				}
			}
		}
		cells.sort((a, b) -> compareBreachCells(a, b, breachAnchor, forward, right));
		return new BreachGridPlan(breachAnchor, forward, right, cells);
	}

	private static int compareBreachCells(BlockPos a, BlockPos b, BlockPos anchorBase, Vec3d forward, Vec3d right) {
		Vec3d anchorCenter = Vec3d.ofCenter(anchorBase);
		Vec3d aOffset = Vec3d.ofCenter(a).subtract(anchorCenter);
		Vec3d bOffset = Vec3d.ofCenter(b).subtract(anchorCenter);
		double aDepth = aOffset.dotProduct(forward);
		double bDepth = bOffset.dotProduct(forward);
		if (aDepth != bDepth) {
			return Double.compare(aDepth, bDepth);
		}
		double aLateral = Math.abs(aOffset.dotProduct(right));
		double bLateral = Math.abs(bOffset.dotProduct(right));
		if (aLateral != bLateral) {
			return Double.compare(aLateral, bLateral);
		}
		return Integer.compare(a.getY(), b.getY());
	}

	private static AssaultMode getDefaultAssaultMode(SiegeBaseState state) {
		return state.getRamIds().isEmpty()
			? AssaultMode.INFANTRY_BREACH
			: AssaultMode.SUPPORT_RAM;
	}

	private static SiegeRamEntity getFurthestRam(ServerWorld world, SiegeBaseState state, Vec3d objectiveCenter) {
		SiegeRamEntity furthest = null;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}
			double distance = ram.squaredDistanceTo(objectiveCenter);
			if (distance > bestDistance) {
				bestDistance = distance;
				furthest = ram;
			}
		}
		return furthest;
	}

	private static HostileEntity getFurthestBreacher(ServerWorld world, SiegeBaseState state, Vec3d objectiveCenter) {
		HostileEntity furthest = null;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			if (!hostile.getCommandTags().contains(BREACHER_ROLE_TAG)) {
				continue;
			}
			double distance = hostile.squaredDistanceTo(objectiveCenter);
			if (distance > bestDistance) {
				bestDistance = distance;
				furthest = hostile;
			}
		}
		return furthest;
	}

	private static HostileEntity getFurthestAttacker(ServerWorld world, SiegeBaseState state, Vec3d objectiveCenter) {
		HostileEntity furthest = null;
		double bestDistance = Double.NEGATIVE_INFINITY;
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			double distance = hostile.squaredDistanceTo(objectiveCenter);
			if (distance > bestDistance) {
				bestDistance = distance;
				furthest = hostile;
			}
		}
		return furthest;
	}

	private static SiegeRamEntity getNearestRam(ServerWorld world, SiegeBaseState state, Vec3d fromPos) {
		SiegeRamEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (UUID ramId : state.getRamIds()) {
			Entity entity = world.getEntity(ramId);
			if (!(entity instanceof SiegeRamEntity ram) || !ram.isAlive()) {
				continue;
			}

			double distance = ram.squaredDistanceTo(fromPos);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = ram;
			}
		}
		return nearest;
	}

	private static SiegeRamEntity getNearestLiveRam(ServerWorld world, Vec3d fromPos) {
		SiegeRamEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (SiegeRamEntity ram : world.getEntitiesByClass(SiegeRamEntity.class, new Box(fromPos.x - 24.0D, fromPos.y - 8.0D, fromPos.z - 24.0D, fromPos.x + 24.0D, fromPos.y + 8.0D, fromPos.z + 24.0D), Entity::isAlive)) {
			double distance = ram.squaredDistanceTo(fromPos);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = ram;
			}
		}
		return nearest;
	}

	private static HostileEntity getNearestFriendlyBreacher(ServerWorld world, SiegeBaseState state, Vec3d fromPos) {
		HostileEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}
			if (!hostile.getCommandTags().contains(BREACHER_ROLE_TAG)) {
				continue;
			}

			double distance = hostile.squaredDistanceTo(fromPos);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = hostile;
			}
		}
		return nearest;
	}

	private static boolean hasActiveBreachTeam(ServerWorld world, SiegeBaseState state) {
		if (getNearestRam(world, state, Vec3d.ofCenter(state.getBasePos())) != null) {
			return true;
		}
		return getNearestFriendlyBreacher(world, state, Vec3d.ofCenter(state.getBasePos())) != null;
	}

	private static Vec3d getSupportFocus(ServerWorld world, HostileEntity hostile, BlockPos objectivePos, SiegeBaseState state) {
		SiegeRamEntity ram = getNearestRam(world, state, hostile.getPos());
		if (ram != null) {
			return ram.getPos();
		}

		HostileEntity breacher = getNearestFriendlyBreacher(world, state, hostile.getPos());
		if (breacher != null) {
			return breacher.getPos();
		}

		BlockPos breachTarget = findBestBreachTarget(world, hostile.getPos(), objectivePos);
		return breachTarget != null ? Vec3d.ofCenter(breachTarget) : Vec3d.ofCenter(objectivePos);
	}

	private static void suppressPotionUse(HostileEntity hostile) {
		if (isPotionItem(hostile.getMainHandStack())) {
			hostile.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		if (isPotionItem(hostile.getOffHandStack())) {
			hostile.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
	}

	private static boolean isPotionItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof PotionItem
			|| stack.getItem() instanceof SplashPotionItem
			|| stack.getItem() instanceof LingeringPotionItem
			|| stack.getItem() instanceof ThrowablePotionItem;
	}

	private static Vec3d getSupportFormationPosition(ServerWorld world, HostileEntity hostile, BlockPos objectivePos, Vec3d supportFocus) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(supportFocus);
		if (approach.lengthSquared() < 0.001D) {
			approach = objectiveCenter.subtract(hostile.getPos());
		}
		if (approach.lengthSquared() < 0.001D) {
			approach = new Vec3d(1.0D, 0.0D, 0.0D);
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int slot = Math.floorMod(hostile.getUuid().hashCode(), 4);
		double lateral = switch (slot) {
			case 0 -> -SUPPORT_FORMATION_RADIUS;
			case 1 -> -SUPPORT_FORMATION_RADIUS / 3.0D;
			case 2 -> SUPPORT_FORMATION_RADIUS / 3.0D;
			default -> SUPPORT_FORMATION_RADIUS;
		};
		double depth = (slot % 2 == 0) ? SUPPORT_FORMATION_DEPTH : SUPPORT_FORMATION_DEPTH + 2.0D;
		Vec3d raw = supportFocus.subtract(forward.multiply(depth)).add(right.multiply(lateral));
		double groundedY = getGroundY(world, raw.x, raw.z);
		return new Vec3d(raw.x, groundedY, raw.z);
	}

	private static Vec3d getBreacherGuardPosition(ServerWorld world, HostileEntity hostile, BlockPos objectivePos, SiegeRamEntity ram) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(ram.getPos());
		if (approach.lengthSquared() < 0.001D) {
			approach = objectiveCenter.subtract(hostile.getPos());
		}
		if (approach.lengthSquared() < 0.001D) {
			approach = new Vec3d(1.0D, 0.0D, 0.0D);
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3);
		double lateral = switch (slot) {
			case 0 -> -BREACHER_GUARD_RADIUS;
			case 1 -> 0.0D;
			default -> BREACHER_GUARD_RADIUS;
		};
		double depth = slot == 1 ? BREACHER_GUARD_DEPTH + 1.0D : BREACHER_GUARD_DEPTH;
		Vec3d raw = ram.getPos().subtract(forward.multiply(depth)).add(right.multiply(lateral));
		double groundedY = getGroundY(world, raw.x, raw.z);
		return new Vec3d(raw.x, groundedY, raw.z);
	}

	private static double getGroundY(ServerWorld world, double x, double z) {
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z));
		return top.getY();
	}

	private static boolean isBlockedByWallTop(ServerWorld world, double x, double y, double z) {
		BlockPos supportPos = BlockPos.ofFloored(x, y - 1.0D, z);
		return WallTier.from(world.getBlockState(supportPos)) != WallTier.NONE;
	}

	private static boolean hasRoomForRam(SiegeRamEntity ram, ServerWorld world, double x, double y, double z) {
		Box currentBox = ram.getBoundingBox();
		double halfWidth = (currentBox.maxX - currentBox.minX) / 2.0D;
		double halfDepth = (currentBox.maxZ - currentBox.minZ) / 2.0D;
		double height = currentBox.maxY - currentBox.minY;
		Box targetBox = new Box(
			x - halfWidth,
			y,
			z - halfDepth,
			x + halfWidth,
			y + height,
			z + halfDepth
		);
		return world.isSpaceEmpty(ram, targetBox);
	}

	private static void faceTowards(Entity entity, Vec3d target) {
		Vec3d delta = target.subtract(entity.getPos());
		if (delta.lengthSquared() < 0.0001D) {
			return;
		}

		float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
		entity.setYaw(yaw);
		entity.setBodyYaw(yaw);
		entity.setHeadYaw(yaw);
		entity.setPitch(0.0F);
	}

	private static void applyRamImpact(SiegeBaseState state, ServerWorld world, SiegeRamEntity ram, BlockPos impactTarget) {
		BlockPos base = getWallBase(impactTarget, world);
		Vec3d right = Vec3d.fromPolar(0.0F, ram.getYaw() + 90.0F).normalize();
		int primaryDamage = getRamAttackDamage(state);
		int splashDamage = getRamSplashDamage(state);
		for (int lateralOffset = -1; lateralOffset <= 1; lateralOffset++) {
			for (int yOffset = 0; yOffset <= 2; yOffset++) {
				BlockPos target = base.add(
					MathHelper.floor(right.x * lateralOffset),
					yOffset,
					MathHelper.floor(right.z * lateralOffset)
				);
				if (WallTier.from(world.getBlockState(target)) == WallTier.NONE) {
					continue;
				}
				state.damageWall(world, target, lateralOffset == 0 ? primaryDamage : splashDamage);
			}
		}
	}

	private static BlockPos getWallBase(BlockPos impactTarget, ServerWorld world) {
		BlockPos cursor = impactTarget;
		while (cursor.getY() > world.getBottomY() && WallTier.from(world.getBlockState(cursor.down())) != WallTier.NONE) {
			cursor = cursor.down();
		}
		return cursor.toImmutable();
	}

	private static int getRamAttackDamage(SiegeBaseState state) {
		int bonus = Math.min(state.getCompletedSieges() * 2, RAM_MAX_ATTACK_BONUS);
		return RAM_BASE_ATTACK_DAMAGE + bonus;
	}

	private static int getRamSplashDamage(SiegeBaseState state) {
		int bonus = Math.min(state.getCompletedSieges(), RAM_MAX_ATTACK_BONUS / 2);
		return RAM_SPLASH_ATTACK_DAMAGE + bonus;
	}

	private static FormationSpawn createFormationSpawn(ServerWorld world, BlockPos basePos) {
		double angle = world.random.nextDouble() * (Math.PI * 2.0D);
		int radius = MathHelper.nextInt(world.random, MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
		Vec3d forward = new Vec3d(-Math.cos(angle), 0.0D, -Math.sin(angle)).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		Vec3d center = Vec3d.ofCenter(basePos).add(forward.multiply(-radius));
		return new FormationSpawn(center, forward, right);
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

	private static void sendAgeProgressMessage(MinecraftServer server, SiegeBaseState state) {
		int nextRequirement = state.getNextAgeSiegeRequirement();
		String progressText = nextRequirement < 0
			? "You are already at the current maximum age tier."
			: "Next age unlocks at " + nextRequirement + " total siege victories.";
		server.getPlayerManager().broadcast(
			Text.literal("Current age: " + state.getAgeName() + ". " + progressText),
			false
		);
	}

	private static boolean isObjectivePresent(ServerWorld world, BlockPos objectivePos) {
		return world.getBlockState(objectivePos).isIn(BlockTags.BANNERS);
	}

	private static BlockPos findBestBreachTarget(ServerWorld world, Vec3d fromPos, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(fromPos);
		if (approach.lengthSquared() < 0.001D) {
			return null;
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		BlockPos bestTarget = null;
		double bestScore = Double.MAX_VALUE;
		int maxStep = Math.max(2, Math.min((int) Math.ceil(Math.sqrt(approach.lengthSquared())), (int) MAX_BREACH_SCAN_DEPTH));

		for (double laneOffset : BREACH_LANES) {
			for (int step = 2; step <= maxStep; step += 2) {
				Vec3d sample = fromPos.add(forward.multiply(step)).add(right.multiply(laneOffset));
				BlockPos surface = getSurfaceBlock(world, sample.x, sample.z);
				for (BlockPos candidate : new BlockPos[] {surface, surface.up(), surface.down()}) {
					WallTier tier = WallTier.from(world.getBlockState(candidate));
					if (tier == WallTier.NONE) {
						continue;
					}

					double score = tier.getHitPoints() * 10.0D + step + Math.abs(laneOffset) * 2.0D;
					if (score < bestScore) {
						bestScore = score;
						bestTarget = candidate.toImmutable();
					}
				}
			}
		}

		return bestTarget;
	}

	private static BlockPos getEffectiveRamTarget(ServerWorld world, SiegeRamEntity ram, BlockPos objectivePos) {
		BlockPos impactTarget = findRamImpactTarget(world, ram);
		if (impactTarget != null) {
			ram.setBreachTarget(getWallBase(impactTarget, world));
			return impactTarget;
		}

		BlockPos assigned = ram.getBreachTarget();
		if (isValidRamBreachTarget(world, assigned)) {
			return assigned;
		}

		BlockPos bestTarget = findBestRamBreachTarget(world, ram.getPos(), objectivePos);
		ram.setBreachTarget(bestTarget);
		return bestTarget;
	}

	private static BlockPos findBestRamBreachTarget(ServerWorld world, Vec3d fromPos, BlockPos objectivePos) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		Vec3d approach = objectiveCenter.subtract(fromPos);
		if (approach.lengthSquared() < 0.001D) {
			return null;
		}

		Vec3d forward = new Vec3d(approach.x, 0.0D, approach.z).normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		BlockPos bestTarget = null;
		double bestScore = Double.MAX_VALUE;
		int maxStep = Math.max(2, Math.min((int) Math.ceil(Math.sqrt(approach.lengthSquared())), (int) MAX_BREACH_SCAN_DEPTH));

		for (double laneOffset : BREACH_LANES) {
			for (int step = 2; step <= maxStep; step += 2) {
				Vec3d sample = fromPos.add(forward.multiply(step)).add(right.multiply(laneOffset));
				BlockPos surface = getSurfaceBlock(world, sample.x, sample.z);
				for (BlockPos candidate : new BlockPos[] {surface, surface.up(), surface.down()}) {
					WallTier tier = WallTier.from(world.getBlockState(candidate));
					if (tier == WallTier.NONE) {
						continue;
					}

					BlockPos base = getWallBase(candidate, world);
					int frontage = getRamFrontageWidth(world, base, right);
					int height = getRamWallHeight(world, base);
					double score = step + Math.abs(laneOffset) * 1.5D + tier.getHitPoints() * 6.0D - frontage * 12.0D - height * 4.0D;
					if (score < bestScore) {
						bestScore = score;
						bestTarget = base.toImmutable();
					}
				}
			}
		}

		return bestTarget;
	}

	private static int getRamFrontageWidth(ServerWorld world, BlockPos base, Vec3d right) {
		int width = 1;
		for (int direction : new int[] {-1, 1}) {
			for (int step = 1; step <= 2; step++) {
				BlockPos offset = base.add(
					MathHelper.floor(right.x * direction * step),
					0,
					MathHelper.floor(right.z * direction * step)
				);
				if (WallTier.from(world.getBlockState(offset)) == WallTier.NONE) {
					break;
				}
				width++;
			}
		}
		return width;
	}

	private static int getRamWallHeight(ServerWorld world, BlockPos base) {
		int height = 0;
		for (int yOffset = 0; yOffset <= 3; yOffset++) {
			if (WallTier.from(world.getBlockState(base.up(yOffset))) == WallTier.NONE) {
				break;
			}
			height++;
		}
		return height;
	}

	private static boolean isValidRamBreachTarget(ServerWorld world, BlockPos target) {
		return target != null && WallTier.from(world.getBlockState(target)) != WallTier.NONE;
	}

	private static BlockPos getSurfaceBlock(ServerWorld world, double x, double z) {
		return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).down();
	}

	private static BlockPos findRamImpactTarget(ServerWorld world, SiegeRamEntity ram) {
		Vec3d forward = Vec3d.fromPolar(0.0F, ram.getYaw()).multiply(1.8D);
		Vec3d probe = ram.getPos().add(forward);
		BlockPos origin = BlockPos.ofFloored(probe.x, probe.y, probe.z);
		for (int yOffset = 0; yOffset <= 2; yOffset++) {
			for (int xOffset = -1; xOffset <= 1; xOffset++) {
				for (int zOffset = -1; zOffset <= 1; zOffset++) {
					BlockPos candidate = origin.add(xOffset, yOffset, zOffset);
					if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
						return candidate.toImmutable();
					}
				}
			}
		}
		return null;
	}

	private static boolean tryAttackWallTarget(HostileEntity hostile, ServerWorld world, BlockPos target, Vec3d approachPos, int damage, int attackIntervalTicks) {
		if (WallTier.from(world.getBlockState(target)) == WallTier.NONE) {
			return false;
		}

		Vec3d targetCenter = Vec3d.ofCenter(target);
		double targetDistanceSq = hostile.squaredDistanceTo(targetCenter);
		double approachDistanceSq = hostile.squaredDistanceTo(approachPos);
		if (approachDistanceSq > 2.75D * 2.75D) {
			moveInfantryToward(hostile, world, approachPos, 1.0D);
			return true;
		}
		if (targetDistanceSq > BREACH_CHECK_RANGE * BREACH_CHECK_RANGE) {
			moveInfantryToward(hostile, world, approachPos, 1.0D);
			return true;
		}

		hostile.getNavigation().stop();
		faceTowards(hostile, targetCenter);
		hostile.swingHand(hostile.getMainHandStack().isEmpty() ? hostile.getActiveHand() : net.minecraft.util.Hand.MAIN_HAND);
		if (hostile.age % attackIntervalTicks == 0) {
			if (DEBUG_LOGGING) {
				AgesOfSiegeMod.LOGGER.info(
					"[SiegeDebug] wall-hit attacker={} target={} damage={} targetDistanceSq={} approachDistanceSq={}",
					hostile.getType().getUntranslatedName(),
					target,
					damage,
					targetDistanceSq,
					approachDistanceSq
				);
			}
			SiegeBaseState.get(world.getServer()).damageWall(world, target, damage);
		}
		return true;
	}

	private static boolean tryFallbackWallAssault(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, int damage, int attackIntervalTicks) {
		BlockPos breachTarget = findBestBreachTarget(world, hostile.getPos(), objectivePos);
		return breachTarget != null
			&& tryAttackWallTarget(hostile, world, breachTarget, getBreachApproachPosition(world, hostile, breachTarget), damage, attackIntervalTicks);
	}

	private static void advanceOnObjective(HostileEntity hostile, ServerWorld world, BlockPos objectivePos, int objectiveDamage) {
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		if (hostile.squaredDistanceTo(objectiveCenter) > OBJECTIVE_ATTACK_RANGE * OBJECTIVE_ATTACK_RANGE) {
			moveInfantryToward(hostile, world, objectiveCenter, 1.0D);
			return;
		}

		hostile.getNavigation().stop();
		hostile.swingHand(hostile.getActiveHand());
		if (hostile.age % 20 == 0) {
			SiegeBaseState.get(world.getServer()).damageObjective(world, objectiveDamage);
		}
	}

	private static void moveInfantryToward(HostileEntity hostile, ServerWorld world, Vec3d target, double speed) {
		Vec3d from = hostile.getPos();
		Vec3d delta = new Vec3d(target.x - from.x, 0.0D, target.z - from.z);
		if (delta.lengthSquared() < 0.0001D) {
			hostile.getNavigation().stop();
			return;
		}

		Vec3d forward = delta.normalize();
		double stepLength = Math.min(INFANTRY_APPROACH_STEP, Math.sqrt(delta.lengthSquared()));
		Vec3d[] candidates = new Vec3d[] {
			from.add(forward.multiply(stepLength)),
			from.add(forward.multiply(stepLength)).add(new Vec3d(-forward.z, 0.0D, forward.x).multiply(BREACH_SIDESTEP_DISTANCE)),
			from.add(forward.multiply(stepLength)).add(new Vec3d(forward.z, 0.0D, -forward.x).multiply(BREACH_SIDESTEP_DISTANCE)),
			from.add(new Vec3d(-forward.z, 0.0D, forward.x).multiply(BREACH_SIDESTEP_DISTANCE)),
			from.add(new Vec3d(forward.z, 0.0D, -forward.x).multiply(BREACH_SIDESTEP_DISTANCE))
		};

		for (Vec3d candidate : candidates) {
			if (tryMoveInfantryToward(hostile, world, candidate, speed)) {
				return;
			}
		}

		hostile.getNavigation().stop();
		faceTowards(hostile, target);
	}

	private static Vec3d getInfantryApproachPosition(ServerWorld world, Vec3d from, Vec3d target) {
		Vec3d delta = new Vec3d(target.x - from.x, 0.0D, target.z - from.z);
		if (delta.lengthSquared() < 0.0001D) {
			return target;
		}

		Vec3d approach = target.subtract(delta.normalize().multiply(1.6D));
		double groundedY = getGroundY(world, approach.x, approach.z);
		return new Vec3d(approach.x, groundedY, approach.z);
	}

	private static Vec3d getBreachApproachPosition(ServerWorld world, HostileEntity hostile, BlockPos target) {
		Vec3d targetCenter = Vec3d.ofCenter(target);
		Vec3d from = hostile.getPos();
		Vec3d delta = new Vec3d(targetCenter.x - from.x, 0.0D, targetCenter.z - from.z);
		if (delta.lengthSquared() < 0.0001D) {
			return targetCenter;
		}

		Vec3d forward = delta.normalize();
		Vec3d right = new Vec3d(-forward.z, 0.0D, forward.x);
		int slot = Math.floorMod(hostile.getUuid().hashCode(), 3) - 1;
		Vec3d baseApproach = targetCenter.subtract(forward.multiply(1.8D)).add(right.multiply(slot * BREACH_SLOT_RADIUS));
		double groundedY = getGroundY(world, baseApproach.x, baseApproach.z);
		return new Vec3d(baseApproach.x, groundedY, baseApproach.z);
	}

	private static boolean tryMoveInfantryToward(HostileEntity hostile, ServerWorld world, Vec3d candidate, double speed) {
		Vec3d from = hostile.getPos();
		double currentY = getGroundY(world, from.x, from.z);
		double nextY = getGroundY(world, candidate.x, candidate.z);
		if (nextY > currentY + INFANTRY_MAX_STEP_UP || isBlockedByWallTop(world, candidate.x, nextY, candidate.z)) {
			return false;
		}

		hostile.getNavigation().startMovingTo(candidate.x, nextY, candidate.z, speed);
		return true;
	}

	private static void logAssaultDecision(ServerWorld world, Entity pathLead, Vec3d pathSource, InfantryPathResult pathResult, boolean hasRam, BlockPos primaryBreachTarget, AssaultMode mode) {
		if (!DEBUG_LOGGING || world.getTime() % 20L != 0L) {
			return;
		}
		AgesOfSiegeMod.LOGGER.info(
			"[SiegeDebug] decision lead={} source={} hasRam={} reachable={} explored={} start={} frontier={} breachTarget={} -> {}",
			pathLead.getType().getUntranslatedName(),
			BlockPos.ofFloored(pathSource),
			hasRam,
			pathResult.reachable(),
			pathResult.explored(),
			pathResult.startFoot(),
			pathResult.frontierTarget(),
			primaryBreachTarget,
			mode
		);
	}

	private static boolean hasClearGroundApproach(ServerWorld world, Vec3d from, Vec3d to) {
		return findInfantryPath(world, from, to, PLAYER_CHASE_WALL_CHECK_DEPTH, 2).reachable();
	}

	private static boolean hasClearInfantryAssaultLane(ServerWorld world, Vec3d from, Vec3d to) {
		return findInfantryPath(world, from, to, ASSAULT_LANE_CHECK_DISTANCE, 2).reachable();
	}

	private static InfantryPathResult findInfantryPath(ServerWorld world, Vec3d from, Vec3d to, double maxDistance, int clearanceHeight) {
		Vec3d delta = new Vec3d(to.x - from.x, 0.0D, to.z - from.z);
		if (delta.lengthSquared() < 0.001D) {
			return new InfantryPathResult(true, BlockPos.ofFloored(from), 0, null);
		}

		double distance = Math.min(Math.sqrt(delta.lengthSquared()), maxDistance);
		if (distance < LANE_CHECK_INITIAL_SKIP) {
			return new InfantryPathResult(true, BlockPos.ofFloored(from), 0, null);
		}

		Vec3d direction = delta.normalize();
		Vec3d bfsStart = from.add(direction.multiply(LANE_CHECK_INITIAL_SKIP));
		BlockPos start = getInfantryFootPos(world, bfsStart.x, bfsStart.z);
		if (!canOccupyInfantryCell(world, start, clearanceHeight)) {
			return new InfantryPathResult(false, start, 0, findBlockingWallInColumn(world, start, clearanceHeight));
		}

		ArrayDeque<PathNode> queue = new ArrayDeque<>();
		Set<Long> visited = new HashSet<>();
		queue.add(new PathNode(start, 0));
		visited.add(packXZ(start.getX(), start.getZ()));
		int explored = 0;
		BlockPos bestFrontier = null;
		double bestFrontierScore = Double.MAX_VALUE;

		while (!queue.isEmpty() && explored < INFANTRY_PATH_SEARCH_LIMIT) {
			PathNode node = queue.removeFirst();
			BlockPos current = node.pos();
			explored++;
			if (Vec3d.ofBottomCenter(current).squaredDistanceTo(to.x, current.getY(), to.z) <= OBJECTIVE_ATTACK_RANGE * OBJECTIVE_ATTACK_RANGE) {
				return new InfantryPathResult(true, start, explored, bestFrontier);
			}

			for (int[] offset : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nextX = current.getX() + offset[0];
				int nextZ = current.getZ() + offset[1];
				long packed = packXZ(nextX, nextZ);
				if (!visited.add(packed)) {
					continue;
				}

				BlockPos next = getInfantryFootPos(world, nextX + 0.5D, nextZ + 0.5D);
				if (Math.abs(next.getY() - current.getY()) > 1) {
					continue;
				}
				if (Vec3d.ofBottomCenter(next).squaredDistanceTo(from.x, next.getY(), from.z) > distance * distance) {
					continue;
				}
				if (!canOccupyInfantryCell(world, next, clearanceHeight)) {
					bestFrontier = pickBetterFrontier(world, to, bestFrontier, bestFrontierScore, findBlockingWallInColumn(world, next, clearanceHeight), node.steps() + 1);
					if (bestFrontier != null) {
						bestFrontierScore = frontierScore(bestFrontier, to, node.steps() + 1);
					}
					continue;
				}
				if (!canTraverseInfantryStep(world, current, next, clearanceHeight)) {
					bestFrontier = pickBetterFrontier(world, to, bestFrontier, bestFrontierScore, findBlockingWallBetween(world, current, next, clearanceHeight), node.steps() + 1);
					if (bestFrontier != null) {
						bestFrontierScore = frontierScore(bestFrontier, to, node.steps() + 1);
					}
					continue;
				}
				queue.addLast(new PathNode(next, node.steps() + 1));
			}
		}

		return new InfantryPathResult(false, start, explored, bestFrontier);
	}

	private static BlockPos pickBetterFrontier(ServerWorld world, Vec3d objective, BlockPos currentBest, double currentBestScore, BlockPos candidate, int steps) {
		if (candidate == null) {
			return currentBest;
		}
		double candidateScore = frontierScore(candidate, objective, steps);
		return candidateScore < currentBestScore ? candidate : currentBest;
	}

	private static double frontierScore(BlockPos frontier, Vec3d objective, int steps) {
		return steps * 10.0D + Vec3d.ofCenter(frontier).squaredDistanceTo(objective);
	}

	private static BlockPos findBlockingWallInColumn(ServerWorld world, BlockPos footPos, int clearanceHeight) {
		for (int yOffset = 0; yOffset < Math.max(3, clearanceHeight + 1); yOffset++) {
			BlockPos candidate = footPos.up(yOffset);
			if (WallTier.from(world.getBlockState(candidate)) != WallTier.NONE) {
				return candidate.toImmutable();
			}
		}
		return null;
	}

	private static BlockPos findBlockingWallBetween(ServerWorld world, BlockPos from, BlockPos to, int clearanceHeight) {
		Vec3d fromCenter = Vec3d.ofBottomCenter(from);
		Vec3d toCenter = Vec3d.ofBottomCenter(to);
		for (double t : new double[] {0.25D, 0.5D, 0.75D}) {
			double sampleX = MathHelper.lerp(t, fromCenter.x, toCenter.x);
			double sampleZ = MathHelper.lerp(t, fromCenter.z, toCenter.z);
			BlockPos sample = getInfantryFootPos(world, sampleX, sampleZ);
			BlockPos wall = findBlockingWallInColumn(world, sample, clearanceHeight);
			if (wall != null) {
				return wall;
			}
		}
		return null;
	}

	private static BlockPos getInfantryFootPos(ServerWorld world, double x, double z) {
		double footY = getGroundY(world, x, z);
		return BlockPos.ofFloored(x, footY, z);
	}

	private static boolean canOccupyInfantryCell(ServerWorld world, BlockPos footPos, int clearanceHeight) {
		BlockPos support = footPos.down();
		if (world.getBlockState(support).isAir() || WallTier.from(world.getBlockState(support)) != WallTier.NONE) {
			return false;
		}
		for (int yOffset = 0; yOffset < clearanceHeight; yOffset++) {
			if (isMovementObstacle(world, footPos.up(yOffset))) {
				return false;
			}
		}
		return true;
	}

	private static boolean canTraverseInfantryStep(ServerWorld world, BlockPos from, BlockPos to, int clearanceHeight) {
		Vec3d fromCenter = Vec3d.ofBottomCenter(from);
		Vec3d toCenter = Vec3d.ofBottomCenter(to);
		for (double t : new double[] {0.25D, 0.5D, 0.75D}) {
			double sampleX = MathHelper.lerp(t, fromCenter.x, toCenter.x);
			double sampleZ = MathHelper.lerp(t, fromCenter.z, toCenter.z);
			BlockPos sample = getInfantryFootPos(world, sampleX, sampleZ);
			if (Math.abs(sample.getY() - from.getY()) > 1 && Math.abs(sample.getY() - to.getY()) > 1) {
				return false;
			}
			if (!canOccupyInfantryCell(world, sample, clearanceHeight)) {
				return false;
			}
		}
		return true;
	}

	private static long packXZ(int x, int z) {
		return (((long) x) << 32) ^ (z & 0xffffffffL);
	}

	private static boolean isMovementObstacle(ServerWorld world, BlockPos pos) {
		var state = world.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		return state.blocksMovement() || WallTier.from(state) != WallTier.NONE;
	}

	private static SiegeBaseState stateFrom(ServerWorld world) {
		return SiegeBaseState.get(world.getServer());
	}

	private static BlockPos getLaneBase(ServerWorld world, BlockPos surface) {
		BlockPos cursor = surface;
		while (cursor.getY() > world.getBottomY() && world.getBlockState(cursor).isAir() && world.getBlockState(cursor.down()).isAir()) {
			cursor = cursor.down();
		}

		if (!world.getBlockState(cursor).isAir()) {
			return cursor.up().toImmutable();
		}
		return cursor.toImmutable();
	}
}
