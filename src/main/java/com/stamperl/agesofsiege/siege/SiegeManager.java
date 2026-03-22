package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SkeletonEntity;
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
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SiegeManager {
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
		if (!world.getBlockState(objectivePos).isOf(Blocks.WHITE_BANNER)) {
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
			SiegeBaseState.get(world.getServer()).damageObjective(world, 1);
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
		int waveSize = getWaveSize(state);
		List<UUID> spawnedAttackers = new ArrayList<>();
		for (int i = 0; i < waveSize; i++) {
			BlockPos spawnPos = findSpawnPosition(world, basePos, i, waveSize);
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

		if (spawnedAttackers.isEmpty()) {
			state.endSiege(true, false);
			server.getPlayerManager().broadcast(Text.literal("The siege could not assemble an attacking wave."), false);
			return;
		}

		state.startSiege(server, spawnedAttackers, waveSize);
		sendAgeProgressMessage(server, state);
	}

	private static BlockPos findSpawnPosition(ServerWorld world, BlockPos basePos, int index, int waveSize) {
		double angle = (Math.PI * 2.0D / waveSize) * index + world.random.nextDouble() * 0.35D;
		int radius = MathHelper.nextInt(world.random, MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
		int x = basePos.getX() + MathHelper.floor(Math.cos(angle) * radius);
		int z = basePos.getZ() + MathHelper.floor(Math.sin(angle) * radius);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, basePos.getY(), z));
		return top.up();
	}

	private static void dropVictoryReward(ServerWorld world, BlockPos objectivePos) {
		SiegeBaseState state = SiegeBaseState.get(world.getServer());
		spawnReward(world, objectivePos, new ItemStack(Items.BREAD, 4));
		spawnReward(world, objectivePos, new ItemStack(Items.IRON_INGOT, 6 + (state.getAgeLevel() * 2)));

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
		return 6 + (state.getAgeLevel() * 2) + Math.min(state.getCompletedSieges(), 4);
	}

	private static HostileEntity createAttacker(ServerWorld world, SiegeBaseState state, int index) {
		if (state.getAgeLevel() >= 2 && index % 3 == 0) {
			return EntityType.SKELETON.create(world);
		}

		return EntityType.ZOMBIE.create(world);
	}

	private static void equipAttacker(HostileEntity attacker, SiegeBaseState state) {
		if (attacker instanceof SkeletonEntity skeleton) {
			skeleton.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		}

		if (state.getAgeLevel() >= 1) {
			attacker.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		}

		if (state.getAgeLevel() >= 2) {
			attacker.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
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
}
