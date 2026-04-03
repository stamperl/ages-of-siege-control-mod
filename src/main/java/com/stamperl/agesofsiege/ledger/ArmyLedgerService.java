package com.stamperl.agesofsiege.ledger;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.defense.DefenderSpawnerService;
import com.stamperl.agesofsiege.defense.DefenderTokenData;
import com.stamperl.agesofsiege.report.SiegeWarReportService;
import com.stamperl.agesofsiege.siege.SiegeBattleCatalog;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.SiegeDirector;
import com.stamperl.agesofsiege.siege.runtime.SiegeBattlePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import com.stamperl.agesofsiege.siege.service.SiegeRewardService;
import com.stamperl.agesofsiege.state.PlacedDefender;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.BlockTags;
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
	public static final Identifier LOCK_SIEGE_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_lock_siege");
	public static final Identifier START_SIEGE_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_start_siege");
	public static final Identifier CANCEL_SIEGE_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_cancel_siege");
	public static final Identifier REOPEN_REPORT_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_ledger_reopen_report");
	private static final List<HighlightState> ACTIVE_HIGHLIGHTS = new ArrayList<>();
	private static final SiegeRewardService REWARDS = new SiegeRewardService();

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
		ServerPlayNetworking.registerGlobalReceiver(LOCK_SIEGE_PACKET, (server, player, handler, buf, responseSender) -> {
			String siegeId = buf.readString(64);
			server.execute(() -> {
				lockSiege(player, siegeId);
				openLedger(player);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(START_SIEGE_PACKET, (server, player, handler, buf, responseSender) ->
			server.execute(() -> {
				startSiege(player);
				openLedger(player);
			}));
		ServerPlayNetworking.registerGlobalReceiver(CANCEL_SIEGE_PACKET, (server, player, handler, buf, responseSender) ->
			server.execute(() -> {
				cancelStagedSiege(player);
				openLedger(player);
			}));
		ServerPlayNetworking.registerGlobalReceiver(REOPEN_REPORT_PACKET, (server, player, handler, buf, responseSender) ->
			server.execute(() -> SiegeWarReportService.tryOpenPendingReportFromLedger(player)));
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
		ServerWorld baseWorld = state.getBaseWorld(player.getServer());
		List<ArmyLedgerSnapshot.DefenderEntry> defenders = buildDefenderEntries(player, state);
		List<ArmyLedgerSnapshot.AttackerEntry> attackers = buildAttackerEntries(player, state);
		List<ArmyLedgerSnapshot.SiegeEntry> sieges = buildSiegeEntries(state);
		SiegeCatalog.SiegeDefinition selectedSiege = resolveSelectedSiege(state);
		String phase = state.getActiveSession() == null ? "at peace" : state.getActiveSession().getPhase().name().toLowerCase(Locale.ROOT);
		boolean objectivePresent = isObjectivePresent(baseWorld, state);
		boolean hasRally = state.getRallyPoint() != null;
		boolean rallyPresent = isRallyPresent(baseWorld, state);
		boolean siegeLocked = state.getActiveSession() != null && state.getActiveSession().getPhase() == SiegePhase.STAGED;
		boolean canStartSiege = siegeLocked && objectivePresent;
		boolean canLockSiege = state.hasBase()
			&& objectivePresent
			&& rallyPresent
			&& state.getActiveSession() == null
			&& selectedSiege != null
			&& selectedSiege.isUnlocked(state);
		String siegeStatus = buildSiegeStatus(state, selectedSiege, siegeLocked, objectivePresent, rallyPresent);
		return new ArmyLedgerSnapshot(
			state.hasBase(),
			state.getBasePos(),
			hasRally,
			hasRally ? state.getRallyPoint() : BlockPos.ORIGIN,
			state.getDimensionId(),
			state.getClaimedBy(),
			state.getObjectiveHealth(),
			state.getMaxObjectiveHealth(),
			phase,
			state.getAgeLevel(),
			state.getAgeName(),
			state.getCompletedSieges(),
			state.getCurrentAgeRegularWins(),
			state.getRegularWinsPerAge(),
			state.getNextAgeSiegeRequirement(),
			selectedSiege == null ? "" : selectedSiege.id(),
			hasPendingReportFor(player, state),
			siegeLocked,
			canLockSiege,
			canStartSiege,
			siegeStatus,
			sieges,
			defenders,
			attackers
		);
	}

	private static boolean hasPendingReportFor(ServerPlayerEntity player, SiegeBaseState state) {
		return SiegeWarReportService.canReopenPendingReport(player, state);
	}

	private static List<ArmyLedgerSnapshot.DefenderEntry> buildDefenderEntries(ServerPlayerEntity player, SiegeBaseState state) {
		List<ArmyLedgerSnapshot.DefenderEntry> defenders = new ArrayList<>();
		for (PlacedDefender defender : state.getPlacedDefenders()) {
			ServerWorld defenderWorld = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(defender.dimensionId())));
			Entity entity = defenderWorld == null ? null : defenderWorld.getEntity(defender.entityUuid());
			LivingEntity living = entity instanceof LivingEntity candidate ? candidate : null;
			BlockPos currentPos = living == null ? defender.homePost() : living.getBlockPos();
			float health = living == null ? 0.0F : living.getHealth();
			float maxHealth = living == null ? 0.0F : living.getMaxHealth();
			String defenderName = defender.defenderName() != null
				? defender.defenderName()
				: DefenderTokenData.displayName(defender.tokenData(), defender.role());
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
		return defenders;
	}

	private static List<ArmyLedgerSnapshot.SiegeEntry> buildSiegeEntries(SiegeBaseState state) {
		List<ArmyLedgerSnapshot.SiegeEntry> sieges = new ArrayList<>();
		for (SiegeCatalog.SiegeDefinition definition : SiegeCatalog.allForState(state)) {
			BattlePreview preview = resolveBattlePreview(state, definition);
			sieges.add(new ArmyLedgerSnapshot.SiegeEntry(
				definition.id(),
				definition.displayName(),
				definition.description(),
				definition.ageLevel(),
				definition.requiredRegularWins(),
				preview.waveSize(),
				definition.ageDefining(),
				definition.isUnlocked(state),
				definition.isReplay(state),
				preview.ramCount(),
				definition.routeColumn(),
				definition.routeRow(),
				preview.enemySummary(),
				preview.weaponSummary(),
				preview.threatSummary(),
				preview.breachSummary(),
				definition.warSuppliesReward(),
				preview.profileId(),
				preview.battleGroups(),
				buildRewardPreviewEntries(state, definition, false),
				buildRewardPreviewEntries(state, definition, true)
			));
		}
		return sieges;
	}

	private static List<ArmyLedgerSnapshot.RewardPreviewEntry> buildRewardPreviewEntries(SiegeBaseState state, SiegeCatalog.SiegeDefinition definition, boolean rerun) {
		long progressionSeed = (((long) state.getAgeLevel()) << 32)
			^ state.getCompletedSieges()
			^ state.getCurrentAgeRegularWins()
			^ state.getCompletedSiegeIds().hashCode();
		long previewSeed = REWARDS.previewSeed(definition.ageLevel(), definition.id(), rerun, progressionSeed);
		return REWARDS.buildRewardPreview(definition.ageLevel(), definition.id(), rerun, previewSeed).stream()
			.map(ArmyLedgerSnapshot.RewardPreviewEntry::fromService)
			.toList();
	}

	private static BattlePreview resolveBattlePreview(SiegeBaseState state, SiegeCatalog.SiegeDefinition definition) {
		SiegeBattlePlan plan = SiegeBattleCatalog.resolve(state, definition);
		if (plan.isEmpty()) {
			return legacyBattlePreview(definition);
		}
		List<ArmyLedgerSnapshot.BattleGroupEntry> groups = new ArrayList<>();
		for (SiegeBattlePlan.UnitGroup unitGroup : plan.unitGroups()) {
			groups.add(new ArmyLedgerSnapshot.BattleGroupEntry(
				unitGroup.unitKind(),
				previewGroupLabel(unitGroup.role().name(), unitGroup.unitKind()),
				Math.max(0, unitGroup.count()),
				false
			));
		}
		for (SiegeBattlePlan.EngineGroup engineGroup : plan.engineGroups()) {
			groups.add(new ArmyLedgerSnapshot.BattleGroupEntry(
				engineGroup.engineKind(),
				previewGroupLabel(engineGroup.engineKind(), engineGroup.engineKind()),
				Math.max(0, engineGroup.count()),
				true
			));
		}
		if (groups.isEmpty()) {
			groups = legacyBattleGroups(definition);
		}
		return new BattlePreview(
			plan.profileId(),
			Math.max(1, plan.waveSize()),
			Math.max(0, plan.ramCount()),
			firstNonBlank(plan.enemySummary(), definition.enemySummary()),
			firstNonBlank(plan.weaponSummary(), definition.weaponSummary()),
			firstNonBlank(plan.threatSummary(), definition.threatSummary()),
			plan.breachSummary(),
			groups
		);
	}

	private static BattlePreview legacyBattlePreview(SiegeCatalog.SiegeDefinition definition) {
		return new BattlePreview(
			definition.id(),
			Math.max(1, definition.waveSize()),
			Math.max(0, definition.ramCount()),
			definition.enemySummary(),
			definition.weaponSummary(),
			definition.threatSummary(),
			definition.ramCount() > 0 ? "ram only" : "fallback",
			legacyBattleGroups(definition)
		);
	}

	private static List<ArmyLedgerSnapshot.BattleGroupEntry> legacyBattleGroups(SiegeCatalog.SiegeDefinition definition) {
		List<ArmyLedgerSnapshot.BattleGroupEntry> groups = new ArrayList<>();
		if (definition.waveSize() > 0) {
			groups.add(new ArmyLedgerSnapshot.BattleGroupEntry(
				"assault",
				definition.minorRaid() ? "Raid Force" : "Main Force",
				definition.waveSize(),
				false
			));
		}
		if (definition.ramCount() > 0) {
			groups.add(new ArmyLedgerSnapshot.BattleGroupEntry(
				"engine",
				definition.ramCount() > 1 ? "Siege Engines" : "Siege Engine",
				definition.ramCount(),
				true
			));
		}
		return groups;
	}

	private static String previewGroupLabel(String preferred, String fallback) {
		String label = firstNonBlank(preferred, fallback);
		if (label == null || label.isBlank()) {
			return "Group";
		}
		String cleaned = label.replace('_', ' ').trim();
		if (cleaned.isEmpty()) {
			return "Group";
		}
		String[] parts = cleaned.split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			String lower = part.toLowerCase(Locale.ROOT);
			builder.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
		}
		return builder.isEmpty() ? "Group" : builder.toString();
	}

	private static String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}

	private record BattlePreview(
		String profileId,
		int waveSize,
		int ramCount,
		String enemySummary,
		String weaponSummary,
		String threatSummary,
		String breachSummary,
		List<ArmyLedgerSnapshot.BattleGroupEntry> battleGroups
	) {
		private BattlePreview {
			profileId = profileId == null ? "" : profileId.trim();
			enemySummary = enemySummary == null ? "" : enemySummary.trim();
			weaponSummary = weaponSummary == null ? "" : weaponSummary.trim();
			threatSummary = threatSummary == null ? "" : threatSummary.trim();
			breachSummary = breachSummary == null ? "" : breachSummary.trim();
			battleGroups = battleGroups == null ? List.of() : List.copyOf(battleGroups);
		}
	}

	private static List<ArmyLedgerSnapshot.AttackerEntry> buildAttackerEntries(ServerPlayerEntity player, SiegeBaseState state) {
		List<ArmyLedgerSnapshot.AttackerEntry> attackers = new ArrayList<>();
		if (state.getActiveSession() == null) {
			return attackers;
		}
		ServerWorld world = state.getBaseWorld(player.getServer());
		if (world == null) {
			return attackers;
		}
		List<UUID> engineIds = state.getActiveSession().getEngineIds();
		for (UUID attackerId : state.getActiveSession().getAttackerIds()) {
			Entity entity = world.getEntity(attackerId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			UnitRole role = state.getActiveSession().getRoleAssignments().getOrDefault(
				attackerId,
				engineIds.contains(attackerId) ? UnitRole.RAM : UnitRole.ESCORT
			);
			attackers.add(new ArmyLedgerSnapshot.AttackerEntry(
				attackerId,
				living.getBlockPos(),
				role,
				engineIds.contains(attackerId),
				true
			));
		}
		return attackers;
	}

	private static String buildSiegeStatus(
		SiegeBaseState state,
		SiegeCatalog.SiegeDefinition selectedSiege,
		boolean siegeLocked,
		boolean objectivePresent,
		boolean rallyPresent
	) {
		if (!state.hasBase()) {
			return "Place a Settlement Standard to unlock campaign planning.";
		}
		if (!objectivePresent) {
			return "Replace the Settlement Standard before locking or starting a siege.";
		}
		if (state.getRallyPoint() == null || !rallyPresent) {
			return "Place a Raid Rally Banner to define the attacker formation point.";
		}
		if (state.getActiveSession() != null && state.getActiveSession().getPhase() != SiegePhase.STAGED) {
			return "A siege is already active. Finish it before planning another.";
		}
		if (siegeLocked) {
			return "Formation locked. The wave is staged at the rally point and waiting for Start Siege.";
		}
		if (selectedSiege == null) {
			return "No siege operation is currently selected.";
		}
		if (!selectedSiege.isUnlocked(state)) {
			if (selectedSiege.ageLevel() > state.getAgeLevel()) {
				return "Clear the current age siege to unlock the next age route.";
			}
			if (selectedSiege.ageDefining()) {
				return "Clear every raid in this age route to unlock the age siege.";
			}
			return "That siege is not unlocked yet.";
		}
		if (selectedSiege.isReplay(state)) {
			return "Replay selected. Rewards still drop, but age progress will not advance.";
		}
		return "Select Lock Siege when your defenses are in place.";
	}

	private static SiegeCatalog.SiegeDefinition resolveSelectedSiege(SiegeBaseState state) {
		SiegeCatalog.SiegeDefinition selected = SiegeCatalog.resolveForState(state, state.getSelectedSiegeId());
		if (selected != null) {
			return selected;
		}
		return SiegeCatalog.resolveForState(state, SiegeCatalog.highestUnlocked(state).id());
	}

	private static void lockSiege(ServerPlayerEntity player, String siegeId) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!canManageSieges(player, state)) {
			return;
		}
		SiegeCatalog.SiegeDefinition definition = SiegeCatalog.resolveForState(state, siegeId);
		if (definition == null) {
			player.sendMessage(Text.literal("That siege plan could not be found.").formatted(Formatting.RED), true);
			return;
		}
		if (!definition.isUnlocked(state)) {
			player.sendMessage(Text.literal("That siege is not unlocked yet.").formatted(Formatting.RED), true);
			return;
		}
		state.setSelectedSiegeId(definition.id());
		if (!SiegeDirector.lockSiegeFromLedger(player.getServer(), state, player, definition)) {
			player.sendMessage(Text.literal("Could not lock that siege. Check the settlement banner, rally banner, and current siege state.").formatted(Formatting.RED), true);
			return;
		}
		player.sendMessage(Text.literal(definition.displayName() + " locked. The enemy formation is waiting at the rally point.").formatted(Formatting.GOLD), true);
	}

	private static void startSiege(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!canManageSieges(player, state)) {
			return;
		}
		ServerWorld baseWorld = state.getBaseWorld(player.getServer());
		if (!isObjectivePresent(baseWorld, state)) {
			player.sendMessage(Text.literal("Replace the Settlement Standard before starting the locked siege.").formatted(Formatting.RED), true);
			return;
		}
		if (!SiegeDirector.startLockedSiege(player.getServer(), state)) {
			player.sendMessage(Text.literal("There is no locked siege ready to start.").formatted(Formatting.RED), true);
			return;
		}
		player.sendMessage(Text.literal("Siege started. Hold the line.").formatted(Formatting.GOLD), true);
	}

	private static void cancelStagedSiege(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		if (!canManageSieges(player, state)) {
			return;
		}
		if (!SiegeDirector.cancelLockedSiege(player.getServer(), state)) {
			player.sendMessage(Text.literal("There is no locked siege to stand down.").formatted(Formatting.RED), true);
			return;
		}
		player.sendMessage(Text.literal("Locked siege stood down.").formatted(Formatting.GOLD), true);
	}

	private static void renameDefender(ServerPlayerEntity player, UUID defenderId, String requestedName) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		PlacedDefender placedDefender = state.getPlacedDefender(defenderId);
		if (!canManageDefender(player, placedDefender)) {
			return;
		}
		String trimmed = requestedName == null ? "" : requestedName.trim();
		String finalName = trimmed.isEmpty() ? DefenderTokenData.displayName(placedDefender.tokenData(), placedDefender.role()) : trimmed;
		var updatedTokenData = DefenderTokenData.withManualName(placedDefender.tokenData(), placedDefender.role(), finalName);
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
			placedDefender.homeYaw(),
			placedDefender.leashRadius(),
			placedDefender.settlementBannerPos(),
			placedDefender.settlementDimensionId(),
			placedDefender.ownerName(),
			placedDefender.ownerUuid(),
			finalName,
			updatedTokenData
		));
	}

	private static void changeRole(ServerPlayerEntity player, UUID defenderId, String requestedRole) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		PlacedDefender placedDefender = state.getPlacedDefender(defenderId);
		if (!canManageDefender(player, placedDefender)) {
			return;
		}
		DefenderRole newRole = DefenderRole.from(requestedRole);
		if (newRole == null || newRole == placedDefender.role()) {
			return;
		}
		var updatedTokenData = DefenderTokenData.withRoleAndStarterLoadout(placedDefender.tokenData(), newRole, SiegeBaseState.get(player.getServer()).getAgeLevel());
		ServerWorld defenderWorld = player.getServer().getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(placedDefender.dimensionId())));
		if (defenderWorld != null) {
			Entity entity = defenderWorld.getEntity(defenderId);
			if (entity instanceof LivingEntity living) {
				living.addCommandTag(newRole.entityTag());
				DefenderTokenData.applyToEntity(living, updatedTokenData, newRole);
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
			placedDefender.homeYaw(),
			newRole.leashRadius(),
			placedDefender.settlementBannerPos(),
			placedDefender.settlementDimensionId(),
			placedDefender.ownerName(),
			placedDefender.ownerUuid(),
			DefenderTokenData.displayName(updatedTokenData, newRole),
			updatedTokenData
		));
	}

	private static boolean canManageDefender(ServerPlayerEntity player, PlacedDefender placedDefender) {
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

	private static boolean canManageSieges(ServerPlayerEntity player, SiegeBaseState state) {
		if (!state.hasBase()) {
			player.sendMessage(Text.literal("Place a Settlement Standard first.").formatted(Formatting.RED), true);
			return false;
		}
		if (!player.hasPermissionLevel(2) && !state.getClaimedBy().equalsIgnoreCase(player.getGameProfile().getName())) {
			player.sendMessage(Text.literal("Only the settlement owner can command this siege plan.").formatted(Formatting.RED), true);
			return false;
		}
		return true;
	}

	private static boolean isObjectivePresent(ServerWorld baseWorld, SiegeBaseState state) {
		return state.hasBase()
			&& baseWorld != null
			&& baseWorld.getBlockState(state.getBasePos()).isIn(BlockTags.BANNERS);
	}

	private static boolean isRallyPresent(ServerWorld baseWorld, SiegeBaseState state) {
		return state.getRallyPoint() != null
			&& baseWorld != null
			&& (baseWorld.getBlockState(state.getRallyPoint()).isOf(net.minecraft.block.Blocks.RED_BANNER)
			|| baseWorld.getBlockState(state.getRallyPoint()).isOf(net.minecraft.block.Blocks.RED_WALL_BANNER));
	}

	private static void locateDefender(ServerPlayerEntity player, UUID defenderId) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		PlacedDefender placedDefender = state.getPlacedDefender(defenderId);
		if (!canManageDefender(player, placedDefender)) {
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
