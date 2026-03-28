package com.stamperl.agesofsiege.defense;

import com.stamperl.agesofsiege.state.PlacedDefender;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Locale;
import java.util.Optional;

public final class DefenderSpawnerService {
	private static final Identifier GUARD_ENTITY_ID = new Identifier("guardvillagers", "guard");
	private static final String DEFENDER_TAG = "ages_of_siege_defender";
	private static final String BOUND_DEFENDER_TAG = "ages_of_siege_bound_defender";

	public int spawnDefenders(ServerPlayerEntity player, String roleName, int count) {
		Optional<EntityType<?>> guardType = Registries.ENTITY_TYPE.getOrEmpty(GUARD_ENTITY_ID);
		if (guardType.isEmpty()) {
			player.sendMessage(
				Text.literal("Guard Villagers was not found. Expected entity id: " + GUARD_ENTITY_ID),
				false
			);
			return 0;
		}

		DefenderRole role = DefenderRole.from(roleName);
		if (role == null) {
			player.sendMessage(Text.literal("Unknown defender role: " + roleName + ". Use soldier or archer."), false);
			return 0;
		}

		ServerWorld world = player.getServerWorld();
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			Entity entity = guardType.get().create(world);
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}

			BlockPos spawnPos = formationPosition(world, player.getBlockPos(), i);
			living.refreshPositionAndAngles(
				spawnPos.getX() + 0.5D,
				spawnPos.getY(),
				spawnPos.getZ() + 0.5D,
				player.getYaw(),
				0.0F
			);
			applyDisplayName(living, role.displayName() + " Guard");
			living.addCommandTag(DEFENDER_TAG);
			living.addCommandTag(role.entityTag());
			if (living instanceof MobEntity mob) {
				mob.setPersistent();
				mob.setCanPickUpLoot(false);
			}
			applyRoleLoadout(living, role);
			world.spawnEntity(living);
			spawned++;
		}

		player.sendMessage(
			Text.literal("Spawned " + spawned + " " + role.displayName().toLowerCase(Locale.ROOT) + " defender(s)."),
			false
		);
		return spawned;
	}

	public boolean spawnPlacedDefender(
		ServerPlayerEntity player,
		SiegeBaseState state,
		DefenderRole role,
		ItemUsageContext context,
		ItemStack stack
	) {
		Optional<EntityType<?>> guardType = Registries.ENTITY_TYPE.getOrEmpty(GUARD_ENTITY_ID);
		if (guardType.isEmpty()) {
			player.sendMessage(Text.literal("Guard Villagers was not found. Expected entity id: " + GUARD_ENTITY_ID).formatted(Formatting.RED), true);
			return false;
		}
		if (!state.hasBase()) {
			player.sendMessage(Text.literal("Place a Settlement Standard before assigning defenders.").formatted(Formatting.RED), true);
			return false;
		}
		if (!state.getDimensionId().equals(player.getWorld().getRegistryKey().getValue().toString())) {
			player.sendMessage(Text.literal("Defender tokens only work in the claimed settlement dimension.").formatted(Formatting.RED), true);
			return false;
		}

		BlockPos targetPos = resolvePlacement(context);
		if (targetPos == null) {
			player.sendMessage(Text.literal("Aim at a valid wall post or platform to place a defender.").formatted(Formatting.RED), true);
			return false;
		}
		if (!canPlaceDefender(player.getServerWorld(), targetPos)) {
			player.sendMessage(Text.literal("That post is blocked or has no support.").formatted(Formatting.RED), true);
			return false;
		}
		if (state.hasDefenderAt(player.getWorld().getRegistryKey().getValue().toString(), targetPos)) {
			player.sendMessage(Text.literal("A defender is already assigned to that post.").formatted(Formatting.RED), true);
			return false;
		}

		Entity entity = guardType.get().create(player.getServerWorld());
		if (!(entity instanceof LivingEntity living)) {
			player.sendMessage(Text.literal("Guard Villagers could not create a valid defender entity.").formatted(Formatting.RED), true);
			return false;
		}

		living.refreshPositionAndAngles(
			targetPos.getX() + 0.5D,
			targetPos.getY(),
			targetPos.getZ() + 0.5D,
			player.getYaw(),
			0.0F
		);
		String defenderName = role.displayName() + " Guard";
		applyDisplayName(living, defenderName);
		living.addCommandTag(DEFENDER_TAG);
		living.addCommandTag(BOUND_DEFENDER_TAG);
		living.addCommandTag(role.entityTag());
		if (living instanceof MobEntity mob) {
			mob.setPersistent();
			mob.setCanPickUpLoot(false);
		}
		applyRoleLoadout(living, role);
		player.getServerWorld().spawnEntity(living);

		state.addPlacedDefender(new PlacedDefender(
			living.getUuid(),
			player.getWorld().getRegistryKey().getValue().toString(),
			role,
			targetPos.toImmutable(),
			role.leashRadius(),
			state.getBasePos(),
			state.getDimensionId(),
			player.getGameProfile().getName(),
			player.getUuid(),
			defenderName
		));

		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		player.sendMessage(
			Text.literal(role.displayName() + " Token placed at " + targetPos.toShortString() + ".")
				.formatted(Formatting.GREEN),
			true
		);
		return true;
	}

	public static void applyDisplayName(LivingEntity defender, String displayName) {
		defender.setCustomName(Text.literal(displayName));
		defender.setCustomNameVisible(false);
	}

	public static void applyRoleLoadout(LivingEntity defender, DefenderRole role) {
		if (role == DefenderRole.ARCHER) {
			defender.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			defender.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 64));
			defender.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
			defender.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			defender.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
			defender.equipStack(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
			return;
		}

		defender.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		defender.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		defender.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		defender.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		defender.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		defender.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
	}

	private BlockPos formationPosition(ServerWorld world, BlockPos center, int index) {
		int row = index / 4;
		int column = index % 4;
		double x = center.getX() + (column - 1.5D) * 2.0D;
		double z = center.getZ() + 3.0D + (row * 2.5D);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(new Vec3d(x, center.getY(), z)));
		return top.up();
	}

	private BlockPos resolvePlacement(ItemUsageContext context) {
		BlockPos clickedPos = context.getBlockPos();
		Direction side = context.getSide();
		BlockPos targetPos = context.getWorld().getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
		if (side == Direction.DOWN) {
			return null;
		}
		return targetPos;
	}

	private boolean canPlaceDefender(ServerWorld world, BlockPos targetPos) {
		if (!world.getBlockState(targetPos).isReplaceable()) {
			return false;
		}
		if (!world.getBlockState(targetPos.up()).isReplaceable()) {
			return false;
		}
		return !world.getBlockState(targetPos.down()).isAir();
	}
}
