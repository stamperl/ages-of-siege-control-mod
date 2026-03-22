package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SiegeManager {
	private static final int SPAWN_COUNT = 6;
	private static final int MIN_SPAWN_RADIUS = 28;
	private static final int MAX_SPAWN_RADIUS = 36;
	private static final double PLAYER_AGGRO_RANGE = 10.0D;
	private static final double OBJECTIVE_ATTACK_RANGE = 2.25D;

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
		if (!world.getBlockState(objectivePos).isOf(com.stamperl.agesofsiege.block.ModBlocks.SETTLEMENT_STANDARD)) {
			state.handleObjectiveDestroyed(world, objectivePos);
			return;
		}

		if (state.isSiegePending()) {
			tickCountdown(server, state);
			return;
		}

		if (!state.isSiegeActive()) {
			return;
		}

		List<UUID> livingAttackers = new ArrayList<>();
		for (UUID attackerId : state.getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof HostileEntity hostile) || !hostile.isAlive()) {
				continue;
			}

			livingAttackers.add(attackerId);
			updateAttacker(hostile, world, objectivePos);
		}

		state.replaceAttackers(livingAttackers);

		if (livingAttackers.isEmpty()) {
			state.endSiege(false, true);
			dropVictoryReward(world, objectivePos);
			server.getPlayerManager().broadcast(Text.literal("The siege wave has been defeated."), false);
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

	private static void updateAttacker(HostileEntity hostile, ServerWorld world, BlockPos objectivePos) {
		PlayerEntity playerTarget = world.getClosestPlayer(
			hostile.getX(),
			hostile.getY(),
			hostile.getZ(),
			PLAYER_AGGRO_RANGE,
			candidate -> candidate.isAlive() && !candidate.isSpectator()
		);

		if (playerTarget != null) {
			hostile.setTarget(playerTarget);
			return;
		}

		hostile.setTarget(null);
		Vec3d objectiveCenter = Vec3d.ofCenter(objectivePos);
		if (hostile.squaredDistanceTo(objectiveCenter) > OBJECTIVE_ATTACK_RANGE * OBJECTIVE_ATTACK_RANGE) {
			hostile.getNavigation().startMovingTo(objectiveCenter.x, objectiveCenter.y, objectiveCenter.z, 1.0D);
			return;
		}

		hostile.getNavigation().stop();
		hostile.swingHand(hostile.getActiveHand());

		if (hostile.age % 20 == 0) {
			world.breakBlock(objectivePos, false, hostile);
		}
	}

	public static boolean startSiege(MinecraftServer server, SiegeBaseState state) {
		if (!state.hasBase() || state.isSiegeActive() || state.isSiegePending()) {
			return false;
		}

		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			return false;
		}

		state.beginCountdown(server, 10);
		return true;
	}

	private static void spawnWave(MinecraftServer server, SiegeBaseState state) {
		ServerWorld world = state.getBaseWorld(server);
		if (world == null) {
			state.endSiege(true, false);
			return;
		}

		BlockPos basePos = state.getBasePos();
		List<UUID> spawnedAttackers = new ArrayList<>();
		for (int i = 0; i < SPAWN_COUNT; i++) {
			BlockPos spawnPos = findSpawnPosition(world, basePos, i);
			ZombieEntity zombie = EntityType.ZOMBIE.create(world);
			if (zombie == null) {
				continue;
			}

			zombie.refreshPositionAndAngles(
				spawnPos.getX() + 0.5D,
				spawnPos.getY(),
				spawnPos.getZ() + 0.5D,
				world.random.nextFloat() * 360.0F,
				0.0F
			);
			zombie.setCanPickUpLoot(false);
			zombie.setPersistent();
			world.spawnEntity(zombie);
			spawnedAttackers.add(zombie.getUuid());
		}

		if (spawnedAttackers.isEmpty()) {
			state.endSiege(true, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}

		state.startSiege(server, spawnedAttackers, SPAWN_COUNT);
	}

	private static BlockPos findSpawnPosition(ServerWorld world, BlockPos basePos, int index) {
		double angle = (Math.PI * 2.0D / SPAWN_COUNT) * index + world.random.nextDouble() * 0.35D;
		int radius = MathHelper.nextInt(world.random, MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
		int x = basePos.getX() + MathHelper.floor(Math.cos(angle) * radius);
		int z = basePos.getZ() + MathHelper.floor(Math.sin(angle) * radius);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, basePos.getY(), z));
		return top.up();
	}

	private static void dropVictoryReward(ServerWorld world, BlockPos objectivePos) {
		ItemEntity ironReward = new ItemEntity(
			world,
			objectivePos.getX() + 0.5D,
			objectivePos.getY() + 1.0D,
			objectivePos.getZ() + 0.5D,
			new ItemStack(Items.IRON_INGOT, 8)
		);
		ItemEntity foodReward = new ItemEntity(
			world,
			objectivePos.getX() + 0.5D,
			objectivePos.getY() + 1.0D,
			objectivePos.getZ() + 0.5D,
			new ItemStack(Items.BREAD, 4)
		);
		world.spawnEntity(ironReward);
		world.spawnEntity(foodReward);
	}
}
