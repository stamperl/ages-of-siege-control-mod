package com.stamperl.agesofsiege.report;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.defense.DefenderTokenData;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.service.SiegeRewardService;
import com.stamperl.agesofsiege.state.PlacedDefender;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SiegeWarReportService {
	public static final Identifier OPEN_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "siege_war_report_open");
	public static final Identifier CLAIM_REWARDS_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "siege_war_report_claim_rewards");
	public static final Identifier ACK_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "siege_war_report_ack");
	private static final SiegeRewardService REWARDS = new SiegeRewardService();

	private SiegeWarReportService() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(CLAIM_REWARDS_PACKET, (server, player, handler, buf, responseSender) ->
			server.execute(() -> claimRewards(player)));
		ServerPlayNetworking.registerGlobalReceiver(ACK_PACKET, (server, player, handler, buf, responseSender) ->
			server.execute(() -> acknowledge(player)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> openPendingReportIfAny(handler.getPlayer())));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			recordDamage(entity, source, amount);
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(SiegeWarReportService::recordDeath);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
			if (entity instanceof ServerPlayerEntity player) {
				recordPlayerKill(player, killedEntity);
				return;
			}
			if (entity instanceof LivingEntity living) {
				recordDefenderKill(living, killedEntity);
			}
		});
	}

	public static void finalizeVictory(MinecraftServer server, SiegeBaseState state, SiegeSession session, SiegeCatalog.SiegeDefinition definition) {
		awardSurvivingDefenderVictories(server, state);
		boolean rerun = definition != null && state.hasCompletedSiege(definition.id());
		List<ItemStack> rewardBundle = REWARDS.buildVictoryRewards(
			session.getSessionAgeLevel(),
			definition == null ? state.getSelectedSiegeId() : definition.id(),
			new SiegeRewardService.RewardContext(rerun, returnedDefenderTokens(statsDefenderTokens(session)), server.getOverworld().getRandom().nextLong())
		);
		ServerPlayerEntity owner = resolveOwner(server, session, state);
		SiegeResultReport report = buildReport(
			server,
			state,
			session,
			definition,
			true,
			buildVictorySummary(definition),
			rewardBundle
		);
		state.setPendingReport(report);
		if (owner != null) {
			openReport(owner, report);
		}
	}

	public static void finalizeDefeat(MinecraftServer server, SiegeBaseState state, SiegeSession session, SiegeCatalog.SiegeDefinition definition, String summary) {
		ServerPlayerEntity owner = resolveOwner(server, session, state);
		List<ItemStack> returnedTokens = returnedDefenderTokens(statsDefenderTokens(session));
		SiegeResultReport report = buildReport(server, state, session, definition, false, summary, returnedTokens);
		state.setPendingReport(report);
		AgesOfSiegeMod.LOGGER.info(
			"Defeat report {} created for siege '{}' with {} claimable rewards.",
			report.reportId(),
			report.siegeId(),
			report.claimableRewards().size()
		);
		if (owner != null) {
			openReport(owner, report);
			AgesOfSiegeMod.LOGGER.info(
				"Defeat report {} opened for player '{}'.",
				report.reportId(),
				owner.getGameProfile().getName()
			);
		} else {
			AgesOfSiegeMod.LOGGER.warn(
				"Defeat report {} could not auto-open because no eligible online owner was resolved.",
				report.reportId()
			);
		}
	}

	public static void openPendingReportIfAny(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		SiegeResultReport report = state.getPendingReport();
		if (!canReopenPendingReport(player, state, report)) {
			return;
		}
		if ((!report.acknowledged() && !report.claimableRewards().isEmpty()) || (report.victory() && report.hasClaimableRewards()) || (!report.victory() && !report.acknowledged())) {
			openReport(player, report);
		}
	}

	public static boolean canReopenPendingReport(ServerPlayerEntity player, SiegeBaseState state) {
		return canReopenPendingReport(player, state, state.getPendingReport());
	}

	public static void tryOpenPendingReportFromLedger(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		SiegeResultReport report = state.getPendingReport();
		if (report == null) {
			player.sendMessage(Text.literal("No pending report to reopen.").formatted(Formatting.RED), true);
			return;
		}
		if (!canReopenPendingReport(player, state, report)) {
			player.sendMessage(Text.literal("That report belongs to a different player.").formatted(Formatting.RED), true);
			return;
		}
		if ((!report.hasClaimableRewards() && report.rewardsClaimed()) || (!report.victory() && report.acknowledged() && !report.hasClaimableRewards())) {
			player.sendMessage(Text.literal("This report has already been claimed and cleared.").formatted(Formatting.RED), true);
			return;
		}
		openReport(player, report);
	}

	private static void openReport(ServerPlayerEntity player, SiegeResultReport report) {
		PacketByteBuf buf = PacketByteBufs.create();
		SiegeWarReportSnapshot.fromReport(report).write(buf);
		ServerPlayNetworking.send(player, OPEN_PACKET, buf);
	}

	private static void acknowledge(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		SiegeResultReport report = state.getPendingReport();
		if (!canReopenPendingReport(player, state, report)) {
			return;
		}
		if (report.hasClaimableRewards()) {
			return;
		}
		state.clearPendingReport();
	}

	private static void claimRewards(ServerPlayerEntity player) {
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		SiegeResultReport report = state.getPendingReport();
		if (!canReopenPendingReport(player, state, report) || !report.hasClaimableRewards()) {
			return;
		}
		REWARDS.claimRewards(player, report.claimableRewards());
		SiegeResultReport updated = report.withClaimedRewards();
		if (report.victory()) {
			state.clearPendingReport();
		} else {
			state.setPendingReport(updated);
		}
		openReport(player, updated);
	}

	private static SiegeResultReport buildReport(
		MinecraftServer server,
		SiegeBaseState state,
		SiegeSession session,
		SiegeCatalog.SiegeDefinition definition,
		boolean victory,
		String summary,
		List<ItemStack> claimableRewards
	) {
		SiegeBattleStats stats = session.getBattleStats();
		return new SiegeResultReport(
			state.nextReportId(),
			session.getOwnerPlayerUuid(),
			session.getOwnerPlayerName(),
			definition == null ? state.getSelectedSiegeId() : definition.id(),
			definition == null ? humanize(definition == null ? state.getSelectedSiegeId() : definition.id()) : definition.displayName(),
			definition == null ? state.getAgeName() : humanizeAge(definition.ageLevel()),
			victory,
			server.getOverworld().getTime(),
			summary,
			toLossEntries(stats.attackerLosses()),
			toLossEntries(stats.defenderLosses()),
			stats.playerDamageDealt(),
			stats.playerDamageTaken(),
			stats.playerKills(),
			claimableRewards,
			false,
			false
		);
	}

	private static List<SiegeResultReport.LossEntry> toLossEntries(Map<String, Integer> counts) {
		List<SiegeResultReport.LossEntry> losses = new ArrayList<>();
		if (counts != null) {
			counts.forEach((key, count) -> {
				if (count != null && count > 0) {
					losses.add(new SiegeResultReport.LossEntry(key, humanize(key), count));
				}
			});
		}
		losses.sort(Comparator.comparingInt(SiegeResultReport.LossEntry::count).reversed().thenComparing(SiegeResultReport.LossEntry::label));
		return List.copyOf(losses);
	}

	private static String buildVictorySummary(SiegeCatalog.SiegeDefinition definition) {
		if (definition != null && definition.ageDefining()) {
			return "Age siege won. Claim your spoils from the war report.";
		}
		return "Siege repelled. Claim your spoils from the war report.";
	}

	private static ServerPlayerEntity resolveOwner(MinecraftServer server, SiegeSession session, SiegeBaseState state) {
		if (session.getOwnerPlayerUuid() != null) {
			ServerPlayerEntity byUuid = server.getPlayerManager().getPlayer(session.getOwnerPlayerUuid());
			if (byUuid != null) {
				return byUuid;
			}
		}
		if (!session.getOwnerPlayerName().isBlank()) {
			ServerPlayerEntity byName = server.getPlayerManager().getPlayer(session.getOwnerPlayerName());
			if (byName != null) {
				return byName;
			}
		}
		if (!state.getClaimedBy().isBlank()) {
			return server.getPlayerManager().getPlayer(state.getClaimedBy());
		}
		return null;
	}

	private static boolean canReopenPendingReport(ServerPlayerEntity player, SiegeBaseState state, SiegeResultReport report) {
		if (report == null) {
			return false;
		}
		if (report.ownerUuid() != null && report.ownerUuid().equals(player.getUuid())) {
			return true;
		}
		if (!report.ownerName().isBlank() && report.ownerName().equalsIgnoreCase(player.getGameProfile().getName())) {
			return true;
		}
		if (player.hasPermissionLevel(2)) {
			return true;
		}
		if (!state.getClaimedBy().isBlank() && state.getClaimedBy().equalsIgnoreCase(player.getGameProfile().getName())) {
			return true;
		}
		return report.ownerUuid() == null && report.ownerName().isBlank();
	}

	private static void recordDamage(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0.0F || entity.getServer() == null) {
			return;
		}
		SiegeBaseState state = SiegeBaseState.get(entity.getServer());
		SiegeSession session = state.getActiveSession();
		if (session == null) {
			return;
		}
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity && isSiegeActor(entity.getUuid(), session)) {
			state.updateBattleStats(stats -> stats.addPlayerDamageDealt(amount));
		}
		if (entity instanceof ServerPlayerEntity && attacker != null && isSiegeActor(attacker.getUuid(), session)) {
			state.updateBattleStats(stats -> stats.addPlayerDamageTaken(amount));
		}
	}

	private static void recordDeath(LivingEntity entity, DamageSource source) {
		if (entity.getServer() == null) {
			return;
		}
		SiegeBaseState state = SiegeBaseState.get(entity.getServer());
		SiegeSession session = state.getActiveSession();
		if (session == null) {
			return;
		}
		UUID entityId = entity.getUuid();
		if (isSiegeActor(entityId, session)) {
			clearNearbyDrops(entity);
			state.updateBattleStats(stats -> stats.recordAttackerDeath(entityId));
			return;
		}
		PlacedDefender placedDefender = state.getPlacedDefender(entityId);
		if (placedDefender != null) {
			clearNearbyDrops(entity);
			var returnedTokenData = DefenderTokenData.captureEntityState(placedDefender.tokenData(), entity, placedDefender.role(), true);
			state.updateBattleStats(stats -> stats.recordDefenderDeath(entityId, returnedTokenData));
			state.removePlacedDefender(entityId);
		}
	}

	private static void clearNearbyDrops(LivingEntity entity) {
		Box box = entity.getBoundingBox().expand(1.75D, 1.0D, 1.75D);
		for (ItemEntity itemEntity : entity.getWorld().getEntitiesByClass(ItemEntity.class, box, candidate -> candidate.age <= 5)) {
			itemEntity.discard();
		}
	}

	private static void recordPlayerKill(ServerPlayerEntity player, Entity killedEntity) {
		if (player.getServer() == null || killedEntity == null) {
			return;
		}
		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		SiegeSession session = state.getActiveSession();
		if (session == null || !isSiegeActor(killedEntity.getUuid(), session)) {
			return;
		}
		state.updateBattleStats(SiegeBattleStats::addPlayerKill);
	}

	private static void recordDefenderKill(LivingEntity killer, Entity killedEntity) {
		if (killer.getServer() == null || killedEntity == null) {
			return;
		}
		SiegeBaseState state = SiegeBaseState.get(killer.getServer());
		SiegeSession session = state.getActiveSession();
		if (session == null || !isSiegeActor(killedEntity.getUuid(), session)) {
			return;
		}
		PlacedDefender placedDefender = state.getPlacedDefender(killer.getUuid());
		if (placedDefender == null) {
			return;
		}
		state.replacePlacedDefender(
			killer.getUuid(),
			new PlacedDefender(
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
				placedDefender.defenderName(),
				DefenderTokenData.addKill(placedDefender.tokenData(), placedDefender.role())
			)
		);
	}

	private static List<NbtCompound> statsDefenderTokens(SiegeSession session) {
		return session == null ? List.of() : session.getBattleStats().fallenDefenderTokens();
	}

	private static List<ItemStack> returnedDefenderTokens(List<NbtCompound> payloads) {
		List<ItemStack> returned = new ArrayList<>();
		for (NbtCompound payload : payloads) {
			DefenderRole role = DefenderTokenData.resolveRole(payload, DefenderRole.SOLDIER);
			returned.add(DefenderTokenData.createTokenStack(role, payload, true));
		}
		return List.copyOf(returned);
	}

	private static void awardSurvivingDefenderVictories(MinecraftServer server, SiegeBaseState state) {
		for (PlacedDefender placedDefender : state.getPlacedDefenders()) {
			var world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(placedDefender.dimensionId())));
			if (world == null) {
				continue;
			}
			Entity entity = world.getEntity(placedDefender.entityUuid());
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			state.replacePlacedDefender(
				placedDefender.entityUuid(),
				new PlacedDefender(
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
					placedDefender.defenderName(),
					DefenderTokenData.addVictory(placedDefender.tokenData(), placedDefender.role())
				)
			);
		}
	}

	private static boolean isSiegeActor(UUID entityUuid, SiegeSession session) {
		return session.getAttackerIds().contains(entityUuid) || session.getEngineIds().contains(entityUuid);
	}

	private static String humanizeAge(int ageLevel) {
		return switch (Math.max(0, ageLevel)) {
			case 0 -> "Homestead";
			case 1 -> "Fortified";
			case 2 -> "Ironkeep";
			case 3 -> "Early Industry";
			default -> "Age " + ageLevel;
		};
	}

	private static String humanize(String key) {
		if (key == null || key.isBlank()) {
			return "Unknown";
		}
		String[] parts = key.replace('_', ' ').trim().split("\\s+");
		StringBuilder builder = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			String lower = part.toLowerCase(Locale.ROOT);
			builder.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
		}
		return builder.isEmpty() ? "Unknown" : builder.toString();
	}

	public static String defenderKey(PlacedDefender defender) {
		if (defender == null) {
			return "defender";
		}
		DefenderRole role = defender.role();
		return role == null ? "defender" : role.name().toLowerCase(Locale.ROOT);
	}
}
