package com.stamperl.agesofsiege.ledger;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.defense.DefenderSpawnerService;
import com.stamperl.agesofsiege.state.PlacedDefender;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ArmyLedgerService {
	public static final Identifier OPEN_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_open");
	public static final Identifier RENAME_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_rename");
	public static final Identifier ROLE_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_role");
	public static final Identifier LOCATE_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_locate");
	private static final List<HighlightState> ACTIVE_HIGHLIGHTS = new ArrayList<>();

	private ArmyLedgerService() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(RENAME_PACKET, (server, player, handler, buf, responseSender) -> {
			UUID defenderId = buf.readUuid();
			String requestedName = buf.readString(64);
			server.execute(() -> {
				renameDefender(player, defenderId, requestedName);
				openLedger(player);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(ROLE_PACKET, (server, player, handler, buf, responseSender) -> {
			UUID defenderId = buf.readUuid();
			String requestedRole = buf.readString(32);
			server.execute(() -> {
				changeRole(player, defenderId, requestedRole);
				openLedger(player);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(LOCATE_PACKET, (server, player, handler, buf, responseSender) -> {
			UUID defenderId = buf.readUuid();
			server.execute(() -> locateDefender(player, defenderId));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<HighlightState> iterator = ACTIVE_HIGHLIGHTS.iterator();
			while (iterator.hasNext()) {
				HighlightState state = iterator.next();
				state.ticksRemaining--;
				if (state.ticksRemaining > 0) {
					continue;
				}
				ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(state.dimensionId)));
				if (world != null) {
					Entity entity = world.getEntity(state.entityUuid);
					if (entity instanceof LivingEntity living && living.isAlive()) {
						living.setGlowing(false);
					}
				}
				iterator.remove();
			}
		});
	}

	public static void openLedger(ServerPlayerEntity player) {
		PacketByteBuf buf = PacketByteBufs.create();
		buildSnapshot(player).write(buf);
		ServerPlayNetworking.send(player, OPEN_PACKET, buf);
	}

	private static ArmyLedgerSnapshot buildSnapshot(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		ServerWorld world = player.getServerWorld();
		List<ArmyLedgerSnapshot.DefenderEntry> defenders = new ArrayList<>();
		for (PlacedDefender defender : state.getPlacedDefenders()) {
			Entity entity = world.getServer().getWorld(world.getRegistryKey()) == null ? null : world.getServer().getWorld(world.getRegistryKey()).getEntity(defender.entityUuid());
			if (entity == null && player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(defender.dimensionId()))) != null) {
				entity = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(defender.dimensionId()))).getEntity(defender.entityUuid());
			}
			LivingEntity living = entity instanceof LivingEntity candidate ? candidate : null;
			BlockPos currentPos = living == null ? defender.homePost() : living.getBlockPos();
			float health = living == null ? 0.0F : living.getHealth();
			float maxHealth = living == null ? 0.0F : living.getMaxHealth();
			String defenderName = defender.defenderName() != null
				? defender.defenderName()
				: defender.role().displayName() + " Guard";
			defenders.add(new ArmyLedgerSnapshot.DefenderEntry(
				defender.entityUuid(),
				living == null ? -1 : living.getId(),
				defenderName,
				defender.role(),
				defender.homePost(),
				currentPos,
				health,
				maxHealth,
				defender.role().attackPower(),
				defender.role().armorLabel(),
				living != null && living.isAlive()
			));
		}

		String phase = state.getActiveSession() == null ? "at peace" : state.getActiveSession().getPhase().name().toLowerCase(Locale.ROOT);
		return new ArmyLedgerSnapshot(
			state.hasBase(),
			state.getBasePos(),
			state.getDimensionId(),
			state.getClaimedBy(),
			state.getObjectiveHealth(),
			state.getMaxObjectiveHealth(),
			phase,
			defenders
		);
	}

	private static void renameDefender(ServerPlayerEntity player, UUID defenderId, String requestedName) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		PlacedDefender placedDefender = state.getPlacedDefender(defenderId);
		if (!canManage(player, placedDefender)) {
			return;
		}
		String trimmed = requestedName == null ? "" : requestedName.trim();
		String finalName = trimmed.isEmpty() ? placedDefender.role().displayName() + " Guard" : trimmed;
		ServerWorld defenderWorld = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(placedDefender.dimensionId())));
		if (defenderWorld != null) {
			Entity entity = defenderWorld.getEntity(defenderId);
			if (entity instanceof LivingEntity living) {
				DefenderSpawnerService.applyDisplayName(living, finalName);
			}
		}
		state.replacePlacedDefender(defenderId, new PlacedDefender(
			placedDefender.entityUuid(),
			placedDefender.dimensionId(),
			placedDefender.role(),
			placedDefender.homePost(),
			placedDefender.leashRadius(),
			placedDefender.settlementBannerPos(),
			placedDefender.settlementDimensionId(),
			placedDefender.ownerName(),
			placedDefender.ownerUuid(),
			finalName
		));
	}

	private static void changeRole(ServerPlayerEntity player, UUID defenderId, String requestedRole) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		PlacedDefender placedDefender = state.getPlacedDefender(defenderId);
		if (!canManage(player, placedDefender)) {
			return;
		}
		DefenderRole newRole = DefenderRole.from(requestedRole);
		if (newRole == null || newRole == placedDefender.role()) {
			return;
		}
		ServerWorld defenderWorld = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(placedDefender.dimensionId())));
		if (defenderWorld != null) {
			Entity entity = defenderWorld.getEntity(defenderId);
			if (entity instanceof LivingEntity living) {
				living.addCommandTag(newRole.entityTag());
				DefenderSpawnerService.applyRoleLoadout(living, newRole);
				DefenderSpawnerService.applyDisplayName(living, placedDefender.defenderName() == null ? newRole.displayName() + " Guard" : placedDefender.defenderName());
				if (living instanceof MobEntity mob) {
					mob.setTarget(null);
					mob.getNavigation().stop();
				}
			}
		}
		state.replacePlacedDefender(defenderId, new PlacedDefender(
			placedDefender.entityUuid(),
			placedDefender.dimensionId(),
			newRole,
			placedDefender.homePost(),
			newRole.leashRadius(),
			placedDefender.settlementBannerPos(),
			placedDefender.settlementDimensionId(),
			placedDefender.ownerName(),
			placedDefender.ownerUuid(),
			placedDefender.defenderName() == null ? newRole.displayName() + " Guard" : placedDefender.defenderName()
		));
	}

	private static boolean canManage(ServerPlayerEntity player, PlacedDefender placedDefender) {
		if (placedDefender == null) {
			player.sendMessage(Text.literal("That defender is no longer bound.").formatted(Formatting.RED), true);
			return false;
		}
		if (placedDefender.ownerUuid() != null && !placedDefender.ownerUuid().equals(player.getUuid()) && !player.hasPermissionLevel(2)) {
			player.sendMessage(Text.literal("Only the settlement owner can command that defender.").formatted(Formatting.RED), true);
			return false;
		}
		return true;
	}

	private static void locateDefender(ServerPlayerEntity player, UUID defenderId) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		PlacedDefender placedDefender = state.getPlacedDefender(defenderId);
		if (!canManage(player, placedDefender)) {
			return;
		}
		ServerWorld defenderWorld = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(placedDefender.dimensionId())));
		if (defenderWorld == null) {
			return;
		}
		Entity entity = defenderWorld.getEntity(defenderId);
		if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
			player.sendMessage(Text.literal("That defender could not be found in the world.").formatted(Formatting.RED), true);
			return;
		}
		living.setGlowing(true);
		ACTIVE_HIGHLIGHTS.add(new HighlightState(defenderId, placedDefender.dimensionId(), 100));
		player.sendMessage(Text.literal("Marked " + (placedDefender.defenderName() == null ? placedDefender.role().displayName() + " Guard" : placedDefender.defenderName()) + " for sighting.").formatted(Formatting.GOLD), true);
	}

	private static final class HighlightState {
		private final UUID entityUuid;
		private final String dimensionId;
		private int ticksRemaining;

		private HighlightState(UUID entityUuid, String dimensionId, int ticksRemaining) {
			this.entityUuid = entityUuid;
			this.dimensionId = dimensionId;
			this.ticksRemaining = ticksRemaining;
		}
	}
}
