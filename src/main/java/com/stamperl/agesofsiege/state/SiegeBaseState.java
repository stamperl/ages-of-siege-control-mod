package com.stamperl.agesofsiege.state;

import com.stamperl.agesofsiege.siege.SiegeCatalog;
import com.stamperl.agesofsiege.siege.WallTier;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegePlanType;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import com.stamperl.agesofsiege.siege.runtime.UnitRole;
import com.stamperl.agesofsiege.siege.service.ObjectiveService;
import com.stamperl.agesofsiege.siege.service.WallDamageService;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.function.UnaryOperator;

public class SiegeBaseState extends PersistentState {
	private static final String STATE_KEY = "ages_of_siege_base";
	private static final int MAX_OBJECTIVE_HEALTH = 30;
	private static final int REGULAR_WINS_PER_AGE = 5;
	private static final String DEFAULT_SIEGE_ID = "homestead_watch";
	private static final String[] AGE_NAMES = {"Homestead", "Fortified", "Ironkeep", "Early Industry"};

	private boolean hasBase;
	private BlockPos basePos = BlockPos.ORIGIN;
	private String dimensionId = "minecraft:overworld";
	private String claimedBy = "unknown";
	private int ageLevel;
	private int completedSieges;
	private int currentAgeRegularWins;
	private boolean siegeFailed;
	private BlockPos rallyPoint;
	private BlockPos assaultOrigin;
	private String selectedSiegeId = DEFAULT_SIEGE_ID;
	private int objectiveHealth = MAX_OBJECTIVE_HEALTH;
	private final Map<Long, Integer> wallHealth = new HashMap<>();
	private final List<PlacedDefender> placedDefenders = new ArrayList<>();
	private SiegeSession activeSession;

	// Temporary compatibility shims until the old SiegeManager is replaced by SiegeDirector.
	private transient boolean assaultModePrimedCompat;
	private transient int rushTicksCompat;
	private transient int deploymentHoldTicksCompat;
	private transient int breachedWallBlocksCompat;
	private static final ObjectiveService OBJECTIVE_SERVICE = new ObjectiveService();
	private static final WallDamageService WALL_DAMAGE_SERVICE = new WallDamageService();

	public static SiegeBaseState get(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		PersistentStateManager manager = overworld.getPersistentStateManager();
		return manager.getOrCreate(SiegeBaseState::fromNbt, SiegeBaseState::new, STATE_KEY);
	}

	private static SiegeBaseState fromNbt(NbtCompound nbt) {
		SiegeBaseState state = new SiegeBaseState();
		state.hasBase = nbt.getBoolean("hasBase");
		state.basePos = new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
		state.dimensionId = nbt.getString("dimension");
		state.claimedBy = nbt.getString("claimedBy");
		state.ageLevel = nbt.getInt("ageLevel");
		state.completedSieges = nbt.getInt("completedSieges");
		state.currentAgeRegularWins = nbt.contains("currentAgeRegularWins") ? nbt.getInt("currentAgeRegularWins") : 0;
		state.siegeFailed = nbt.getBoolean("siegeFailed");
		if (nbt.contains("rallyPointX")) {
			state.rallyPoint = new BlockPos(
				nbt.getInt("rallyPointX"),
				nbt.getInt("rallyPointY"),
				nbt.getInt("rallyPointZ")
			);
		}
		if (nbt.contains("assaultOriginX")) {
			state.assaultOrigin = new BlockPos(
				nbt.getInt("assaultOriginX"),
				nbt.getInt("assaultOriginY"),
				nbt.getInt("assaultOriginZ")
			);
		}
		state.selectedSiegeId = nbt.contains("selectedSiegeId") ? nbt.getString("selectedSiegeId") : DEFAULT_SIEGE_ID;
		state.objectiveHealth = nbt.contains("objectiveHealth") ? nbt.getInt("objectiveHealth") : MAX_OBJECTIVE_HEALTH;
		NbtList wallList = nbt.getList("wallHealth", NbtElement.COMPOUND_TYPE);
		for (NbtElement element : wallList) {
			NbtCompound entry = (NbtCompound) element;
			state.wallHealth.put(entry.getLong("pos"), entry.getInt("hp"));
		}
		NbtList defenderList = nbt.getList("placedDefenders", NbtElement.COMPOUND_TYPE);
		for (NbtElement element : defenderList) {
			state.placedDefenders.add(PlacedDefender.fromNbt((NbtCompound) element));
		}

		if (nbt.contains("activeSession", NbtElement.COMPOUND_TYPE)) {
			state.activeSession = SiegeSession.fromNbt(nbt.getCompound("activeSession"));
		} else {
			state.activeSession = migrateLegacySession(nbt, state);
		}

		state.assaultModePrimedCompat = nbt.getBoolean("assaultModePrimed");
		state.rushTicksCompat = nbt.getInt("rushTicks");
		state.deploymentHoldTicksCompat = nbt.getInt("deploymentHoldTicks");
		state.breachedWallBlocksCompat = nbt.getInt("breachedWallBlocks");
		return state;
	}

	private static SiegeSession migrateLegacySession(NbtCompound nbt, SiegeBaseState state) {
		boolean legacyPending = nbt.getBoolean("siegePending");
		boolean legacyActive = nbt.getBoolean("siegeActive");
		if (!legacyPending && !legacyActive) {
			return null;
		}

		List<UUID> attackerIds = readLegacyUuidList(nbt.getList("attackers", NbtElement.STRING_TYPE));
		List<UUID> ramIds = readLegacyUuidList(nbt.getList("rams", NbtElement.STRING_TYPE));
		BlockPos legacyPrimaryBreachTarget = null;
		if (nbt.contains("primaryBreachTargetX")) {
			legacyPrimaryBreachTarget = new BlockPos(
				nbt.getInt("primaryBreachTargetX"),
				nbt.getInt("primaryBreachTargetY"),
				nbt.getInt("primaryBreachTargetZ")
			);
		}
		SiegePlan legacyPlan = legacyPrimaryBreachTarget == null
			? null
			: new SiegePlan(
				SiegePlanType.BREACH_REQUIRED,
				Vec3d.ZERO,
				state.assaultOrigin == null ? state.basePos : state.assaultOrigin,
				legacyPrimaryBreachTarget,
				List.of(legacyPrimaryBreachTarget),
				null,
				false,
				0,
				0,
				0.0F,
				0L
			);
		return new SiegeSession(
			legacyPending ? SiegePhase.COUNTDOWN : SiegePhase.FORM_UP,
			state.ageLevel,
			state.completedSieges,
			state.basePos,
			state.rallyPoint,
			state.assaultOrigin,
			0L,
			0L,
			nbt.getInt("countdownTicks"),
			attackerIds,
			ramIds,
			Map.of(),
			legacyPlan,
			null,
			0L,
			0L,
			null
		);
	}

	private static List<UUID> readLegacyUuidList(NbtList list) {
		List<UUID> ids = new ArrayList<>();
		for (NbtElement element : list) {
			ids.add(UUID.fromString(element.asString()));
		}
		return List.copyOf(ids);
	}

	public void setBase(BlockPos pos, String dimensionId, String claimedBy) {
		this.hasBase = true;
		this.basePos = pos.toImmutable();
		this.dimensionId = dimensionId;
		this.claimedBy = claimedBy;
		this.ageLevel = Math.max(this.ageLevel, 0);
		this.siegeFailed = false;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
		this.activeSession = null;
		resetCompatRuntime();
		markDirty();
	}

	public void clearBase() {
		this.hasBase = false;
		this.basePos = BlockPos.ORIGIN;
		this.dimensionId = "minecraft:overworld";
		this.claimedBy = "unknown";
		this.siegeFailed = false;
		this.rallyPoint = null;
		this.assaultOrigin = null;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
		this.wallHealth.clear();
		this.activeSession = null;
		resetCompatRuntime();
		markDirty();
	}

	public boolean hasBase() {
		return hasBase;
	}

	public BlockPos getBasePos() {
		return basePos;
	}

	public boolean isSiegeActive() {
		if (activeSession == null) {
			return false;
		}
		return switch (activeSession.getPhase()) {
			case FORM_UP, ADVANCE, BREACH, RUSH, CLEANUP -> true;
			default -> false;
		};
	}

	public boolean isSiegePending() {
		return activeSession != null && activeSession.getPhase() == SiegePhase.COUNTDOWN;
	}

	public boolean isBreachOpen() {
		return activeSession != null && activeSession.getPhase() == SiegePhase.RUSH;
	}

	public void setBreachOpen(boolean breachOpen) {
		if (activeSession == null) {
			return;
		}
		if (breachOpen && activeSession.getPhase() != SiegePhase.RUSH) {
			setSessionPhase(SiegePhase.RUSH);
		} else if (!breachOpen && activeSession.getPhase() == SiegePhase.RUSH) {
			setSessionPhase(SiegePhase.BREACH);
		}
	}

	public boolean isAssaultModePrimed() {
		return assaultModePrimedCompat;
	}

	public void setAssaultModePrimed(boolean assaultModePrimed) {
		if (this.assaultModePrimedCompat == assaultModePrimed) {
			return;
		}
		this.assaultModePrimedCompat = assaultModePrimed;
		markDirty();
	}

	public int getRushTicks() {
		return rushTicksCompat;
	}

	public void setRushTicks(int rushTicks) {
		if (this.rushTicksCompat == rushTicks) {
			return;
		}
		this.rushTicksCompat = rushTicks;
		markDirty();
	}

	public int getBreachedWallBlocks() {
		return breachedWallBlocksCompat;
	}

	public BlockPos getAssaultOrigin() {
		return assaultOrigin;
	}

	public BlockPos getRallyPoint() {
		return rallyPoint;
	}

	public void setRallyPoint(BlockPos rallyPoint) {
		BlockPos next = rallyPoint == null ? null : rallyPoint.toImmutable();
		if (this.rallyPoint != null && this.rallyPoint.equals(next)) {
			return;
		}
		if (this.rallyPoint == null && next == null) {
			return;
		}
		this.rallyPoint = next;
		markDirty();
	}

	public void setAssaultOrigin(BlockPos assaultOrigin) {
		BlockPos next = assaultOrigin == null ? null : assaultOrigin.toImmutable();
		if (this.assaultOrigin != null && this.assaultOrigin.equals(next)) {
			return;
		}
		if (this.assaultOrigin == null && next == null) {
			return;
		}
		this.assaultOrigin = next;
		updateSession(session -> new SiegeSession(
			session.getPhase(),
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			next,
			session.getStartedGameTime(),
			session.getPhaseStartedGameTime(),
			session.getCountdownEndGameTime(),
			session.getAttackerIds(),
			session.getEngineIds(),
			session.getRoleAssignments(),
			session.getCurrentPlan(),
			session.getLastObservation(),
			session.getLastObservationTick(),
			session.getLastPlanTick(),
			session.getFallbackReason()
		));
	}

	public BlockPos getPrimaryBreachTarget() {
		return activeSession != null && activeSession.getCurrentPlan() != null
			? activeSession.getCurrentPlan().primaryBreachAnchor()
			: null;
	}

	public void setPrimaryBreachTarget(BlockPos primaryBreachTarget) {
		if (activeSession == null) {
			return;
		}
		BlockPos next = primaryBreachTarget == null ? null : primaryBreachTarget.toImmutable();
		SiegePlan currentPlan = activeSession.getCurrentPlan();
		SiegePlan nextPlan;
		if (next == null) {
			nextPlan = null;
		} else if (currentPlan == null) {
			nextPlan = new SiegePlan(
				SiegePlanType.BREACH_REQUIRED,
				Vec3d.ZERO,
				activeSession.getSpawnCenter() == null ? basePos : activeSession.getSpawnCenter(),
				next,
				List.of(next),
				null,
				false,
				0,
				0,
				0.0F,
				0L
			);
		} else {
			nextPlan = new SiegePlan(
				currentPlan.planType(),
				currentPlan.approachVector(),
				currentPlan.stagingPoint(),
				next,
				currentPlan.targetBlocks(),
				currentPlan.breachExit(),
				currentPlan.objectiveReachableAfterBreach(),
				currentPlan.openingWidth(),
				currentPlan.openingHeight(),
				currentPlan.confidence(),
				currentPlan.expiresAtTick()
			);
		}
		updateSession(session -> new SiegeSession(
			session.getPhase(),
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			session.getSpawnCenter(),
			session.getStartedGameTime(),
			session.getPhaseStartedGameTime(),
			session.getCountdownEndGameTime(),
			session.getAttackerIds(),
			session.getEngineIds(),
			session.getRoleAssignments(),
			nextPlan,
			session.getLastObservation(),
			session.getLastObservationTick(),
			session.getLastPlanTick(),
			session.getFallbackReason()
		));
	}

	public int getObjectiveHealth() {
		return objectiveHealth;
	}

	public int getDeploymentHoldTicks() {
		return deploymentHoldTicksCompat;
	}

	public void setDeploymentHoldTicks(int deploymentHoldTicks) {
		if (this.deploymentHoldTicksCompat == deploymentHoldTicks) {
			return;
		}
		this.deploymentHoldTicksCompat = Math.max(0, deploymentHoldTicks);
		markDirty();
	}

	public int tickDeploymentHold() {
		if (deploymentHoldTicksCompat <= 0) {
			return 0;
		}
		deploymentHoldTicksCompat--;
		markDirty();
		return deploymentHoldTicksCompat;
	}

	public int getMaxObjectiveHealth() {
		return MAX_OBJECTIVE_HEALTH;
	}

	public int getAgeLevel() {
		return ageLevel;
	}

	public int getCompletedSieges() {
		return completedSieges;
	}

	public int getCurrentAgeRegularWins() {
		return currentAgeRegularWins;
	}

	public int getRegularWinsPerAge() {
		return REGULAR_WINS_PER_AGE;
	}

	public void setTestingAgeLevel(int ageLevel) {
		int clampedAge = MathHelper.clamp(ageLevel, 0, AGE_NAMES.length - 1);
		this.ageLevel = clampedAge;
		this.completedSieges = clampedAge * (REGULAR_WINS_PER_AGE + 1);
		this.currentAgeRegularWins = 0;
		this.siegeFailed = false;
		this.activeSession = null;
		this.selectedSiegeId = SiegeCatalog.defaultSiegeForAge(clampedAge).id();
		resetCompatRuntime();
		markDirty();
	}

	public String getAgeName() {
		return AGE_NAMES[MathHelper.clamp(ageLevel, 0, AGE_NAMES.length - 1)];
	}

	public int getNextAgeSiegeRequirement() {
		if (ageLevel >= AGE_NAMES.length - 1) {
			return -1;
		}
		return REGULAR_WINS_PER_AGE;
	}

	public List<UUID> getAttackerIds() {
		return activeSession == null ? List.of() : activeSession.getAttackerIds();
	}

	public void replaceAttackers(List<UUID> attackerIds) {
		updateSession(session -> new SiegeSession(
			session.getPhase(),
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			session.getSpawnCenter(),
			session.getStartedGameTime(),
			session.getPhaseStartedGameTime(),
			session.getCountdownEndGameTime(),
			attackerIds,
			session.getEngineIds(),
			session.getRoleAssignments(),
			session.getCurrentPlan(),
			session.getLastObservation(),
			session.getLastObservationTick(),
			session.getLastPlanTick(),
			session.getFallbackReason()
		));
	}

	public List<UUID> getRamIds() {
		return activeSession == null ? List.of() : activeSession.getEngineIds();
	}

	public void replaceRams(List<UUID> ramIds) {
		updateSession(session -> new SiegeSession(
			session.getPhase(),
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			session.getSpawnCenter(),
			session.getStartedGameTime(),
			session.getPhaseStartedGameTime(),
			session.getCountdownEndGameTime(),
			session.getAttackerIds(),
			ramIds,
			session.getRoleAssignments(),
			session.getCurrentPlan(),
			session.getLastObservation(),
			session.getLastObservationTick(),
			session.getLastPlanTick(),
			session.getFallbackReason()
		));
	}

	public SiegeSession getActiveSession() {
		return activeSession;
	}

	public void setActiveSession(SiegeSession activeSession) {
		this.activeSession = activeSession;
		markDirty();
	}

	public void updateActiveSession(UnaryOperator<SiegeSession> updater) {
		updateSession(updater);
	}

	public ServerWorld getBaseWorld(MinecraftServer server) {
		Identifier id = Identifier.tryParse(dimensionId);
		if (id == null) {
			return null;
		}
		return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
	}

	public void beginCountdown(MinecraftServer server, int countdownSeconds) {
		this.siegeFailed = false;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
		this.wallHealth.clear();
		resetCompatRuntime();
		long now = server.getOverworld().getTime();
		this.activeSession = new SiegeSession(
			SiegePhase.COUNTDOWN,
			ageLevel,
			completedSieges,
			basePos,
			rallyPoint,
			assaultOrigin,
			now,
			now,
			now + (countdownSeconds * 20L),
			List.of(),
			List.of(),
			Map.of(),
			null,
			null,
			0L,
			0L,
			null
		);
		server.getPlayerManager().broadcast(
			Text.literal("A siege is approaching. Defend the Settlement Standard."),
			false
		);
		markDirty();
	}

	public void prepareStagedSiege(MinecraftServer server, String siegeId, int siegeAgeLevel) {
		this.siegeFailed = false;
		this.selectedSiegeId = siegeId == null || siegeId.isBlank() ? DEFAULT_SIEGE_ID : siegeId;
		this.objectiveHealth = MAX_OBJECTIVE_HEALTH;
		this.wallHealth.clear();
		resetCompatRuntime();
		long now = server.getOverworld().getTime();
		this.activeSession = new SiegeSession(
			SiegePhase.STAGED,
			siegeAgeLevel,
			completedSieges,
			basePos,
			rallyPoint,
			assaultOrigin,
			now,
			now,
			0L,
			List.of(),
			List.of(),
			Map.of(),
			null,
			null,
			0L,
			0L,
			null
		);
		markDirty();
	}

	public int tickCountdown() {
		if (!isSiegePending() || activeSession == null) {
			return 0;
		}
		long remaining = Math.max(0L, activeSession.getCountdownEndGameTime() - activeSession.getPhaseStartedGameTime());
		long nextCountdownEnd = Math.max(activeSession.getPhaseStartedGameTime(), activeSession.getCountdownEndGameTime() - 1L);
		activeSession = new SiegeSession(
			activeSession.getPhase(),
			activeSession.getSessionAgeLevel(),
			activeSession.getSessionVictoryCount(),
			activeSession.getObjectivePos(),
			activeSession.getRallyPos(),
			activeSession.getSpawnCenter(),
			activeSession.getStartedGameTime(),
			activeSession.getPhaseStartedGameTime(),
			nextCountdownEnd,
			activeSession.getAttackerIds(),
			activeSession.getEngineIds(),
			activeSession.getRoleAssignments(),
			activeSession.getCurrentPlan(),
			activeSession.getLastObservation(),
			activeSession.getLastObservationTick(),
			activeSession.getLastPlanTick(),
			activeSession.getFallbackReason()
		);
		markDirty();
		return (int) Math.max(0L, remaining - 1L);
	}

	public int getCountdownTicks() {
		if (!isSiegePending() || activeSession == null) {
			return 0;
		}
		return (int) Math.max(0L, activeSession.getCountdownEndGameTime() - activeSession.getPhaseStartedGameTime());
	}

	public void stageSiegeWave(List<UUID> attackerIds, List<UUID> ramIds, Map<UUID, UnitRole> roleAssignments, int waveSize, BlockPos assaultOrigin) {
		this.assaultOrigin = assaultOrigin == null ? null : assaultOrigin.toImmutable();
		updateSession(session -> new SiegeSession(
			session.getPhase(),
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			this.assaultOrigin,
			session.getStartedGameTime(),
			session.getPhaseStartedGameTime(),
			session.getCountdownEndGameTime(),
			attackerIds,
			ramIds,
			roleAssignments,
			session.getCurrentPlan(),
			session.getLastObservation(),
			session.getLastObservationTick(),
			session.getLastPlanTick(),
			session.getFallbackReason()
		));
		markDirty();
	}

	public void startSiege(MinecraftServer server, List<UUID> attackerIds, List<UUID> ramIds, int waveSize, BlockPos assaultOrigin) {
		this.assaultOrigin = assaultOrigin == null ? null : assaultOrigin.toImmutable();
		long now = server.getOverworld().getTime();
		this.activeSession = new SiegeSession(
			SiegePhase.FORM_UP,
			ageLevel,
			completedSieges,
			basePos,
			rallyPoint,
			this.assaultOrigin,
			now,
			now,
			0L,
			attackerIds,
			ramIds,
			Map.of(),
			null,
			null,
			0L,
			0L,
			null
		);
		this.siegeFailed = false;
		this.assaultModePrimedCompat = false;
		this.rushTicksCompat = 0;
		this.deploymentHoldTicksCompat = 40;
		this.breachedWallBlocksCompat = 0;
		this.wallHealth.clear();
		server.getPlayerManager().broadcast(Text.literal("A siege has begun. Defend the Settlement Standard!"), false);
		markDirty();
	}

	public void activateStagedSiege(MinecraftServer server) {
		if (activeSession == null) {
			return;
		}
		long now = server.getOverworld().getTime();
		this.activeSession = new SiegeSession(
			SiegePhase.FORM_UP,
			activeSession.getSessionAgeLevel(),
			activeSession.getSessionVictoryCount(),
			activeSession.getObjectivePos(),
			activeSession.getRallyPos(),
			activeSession.getSpawnCenter(),
			activeSession.getStartedGameTime() == 0L ? now : activeSession.getStartedGameTime(),
			now,
			0L,
			activeSession.getAttackerIds(),
			activeSession.getEngineIds(),
			activeSession.getRoleAssignments(),
			activeSession.getCurrentPlan(),
			activeSession.getLastObservation(),
			activeSession.getLastObservationTick(),
			activeSession.getLastPlanTick(),
			activeSession.getFallbackReason()
		);
		this.siegeFailed = false;
		this.assaultModePrimedCompat = false;
		this.rushTicksCompat = 0;
		this.deploymentHoldTicksCompat = 40;
		this.wallHealth.clear();
		server.getPlayerManager().broadcast(Text.literal("A siege has begun. Defend the Settlement Standard!"), false);
		markDirty();
	}

	public void endSiege(boolean failed, boolean rewardProgress) {
		this.siegeFailed = failed;
		this.assaultOrigin = null;
		this.activeSession = null;
		resetCompatRuntime();
		this.wallHealth.clear();
		markDirty();
	}

	public boolean recordSiegeVictory(SiegeCatalog.SiegeDefinition definition) {
		this.completedSieges++;
		boolean advancedAge = false;
		if (definition != null && definition.ageLevel() == this.ageLevel) {
			if (definition.ageDefining()) {
				if (this.currentAgeRegularWins >= REGULAR_WINS_PER_AGE && this.ageLevel < AGE_NAMES.length - 1) {
					this.ageLevel++;
					this.currentAgeRegularWins = 0;
					this.selectedSiegeId = SiegeCatalog.defaultSiegeForAge(this.ageLevel).id();
					advancedAge = true;
				}
			} else {
				this.currentAgeRegularWins = Math.min(REGULAR_WINS_PER_AGE, this.currentAgeRegularWins + 1);
			}
		}
		markDirty();
		return advancedAge;
	}

	public String getSelectedSiegeId() {
		return selectedSiegeId;
	}

	public void setSelectedSiegeId(String selectedSiegeId) {
		String next = selectedSiegeId == null || selectedSiegeId.isBlank() ? DEFAULT_SIEGE_ID : selectedSiegeId;
		if (next.equals(this.selectedSiegeId)) {
			return;
		}
		this.selectedSiegeId = next;
		markDirty();
	}

	public void damageObjective(ServerWorld world, int amount) {
		OBJECTIVE_SERVICE.damageObjective(world, this, activeSession, amount);
	}

	public boolean damageWall(ServerWorld world, BlockPos pos, int amount) {
		return WALL_DAMAGE_SERVICE.damageWall(world, this, activeSession, pos, amount);
	}

	public void handleObjectiveDestroyed(ServerWorld world, BlockPos pos) {
		OBJECTIVE_SERVICE.handleObjectiveDestroyed(world, this, activeSession, pos);
	}

	public String describe() {
		int nextRequirement = getNextAgeSiegeRequirement();
		String nextAgeText = nextRequirement < 0
			? "max age reached"
			: nextRequirement + " victories for next age";
		String rallyText = rallyPoint == null ? "unset" : rallyPoint.toShortString();
		String phaseText = activeSession == null ? "none" : activeSession.getPhase().name();
		return String.format(
			"Current siege base: %s in %s, claimed by %s. Rally point: %s. Objective HP: %d/%d. Age: %s (%d). Completed sieges: %d. Progress: %s. Session phase: %s. Siege failed: %s.",
			basePos.toShortString(),
			dimensionId,
			claimedBy,
			rallyText,
			objectiveHealth,
			MAX_OBJECTIVE_HEALTH,
			getAgeName(),
			ageLevel,
			completedSieges,
			nextAgeText,
			phaseText,
			siegeFailed ? "yes" : "no"
		);
	}

	public String describeDefenders() {
		if (placedDefenders.isEmpty()) {
			return "Bound defenders: none.";
		}

		int archers = 0;
		int soldiers = 0;
		for (PlacedDefender defender : placedDefenders) {
			switch (defender.role()) {
				case ARCHER -> archers++;
				case SOLDIER -> soldiers++;
			}
		}
		return "Bound defenders: " + placedDefenders.size() + " total (" + archers + " archers, " + soldiers + " soldiers).";
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putBoolean("hasBase", hasBase);
		nbt.putInt("x", basePos.getX());
		nbt.putInt("y", basePos.getY());
		nbt.putInt("z", basePos.getZ());
		nbt.putString("dimension", dimensionId);
		nbt.putString("claimedBy", claimedBy);
		nbt.putInt("ageLevel", ageLevel);
		nbt.putInt("completedSieges", completedSieges);
		nbt.putInt("currentAgeRegularWins", currentAgeRegularWins);
		nbt.putBoolean("siegeFailed", siegeFailed);
		if (rallyPoint != null) {
			nbt.putInt("rallyPointX", rallyPoint.getX());
			nbt.putInt("rallyPointY", rallyPoint.getY());
			nbt.putInt("rallyPointZ", rallyPoint.getZ());
		}
		if (assaultOrigin != null) {
			nbt.putInt("assaultOriginX", assaultOrigin.getX());
			nbt.putInt("assaultOriginY", assaultOrigin.getY());
			nbt.putInt("assaultOriginZ", assaultOrigin.getZ());
		}
		nbt.putString("selectedSiegeId", selectedSiegeId == null ? DEFAULT_SIEGE_ID : selectedSiegeId);
		nbt.putInt("objectiveHealth", objectiveHealth);
		if (activeSession != null) {
			nbt.put("activeSession", activeSession.toNbt());
		}
		NbtList wallList = new NbtList();
		for (Map.Entry<Long, Integer> entry : wallHealth.entrySet()) {
			NbtCompound wallEntry = new NbtCompound();
			wallEntry.putLong("pos", entry.getKey());
			wallEntry.putInt("hp", entry.getValue());
			wallList.add(wallEntry);
		}
		nbt.put("wallHealth", wallList);
		NbtList defenderList = new NbtList();
		for (PlacedDefender defender : placedDefenders) {
			defenderList.add(defender.toNbt());
		}
		nbt.put("placedDefenders", defenderList);
		return nbt;
	}

	private void updateSession(UnaryOperator<SiegeSession> updater) {
		if (activeSession == null) {
			return;
		}
		SiegeSession updated = updater.apply(activeSession);
		if (updated == null) {
			return;
		}
		activeSession = updated;
		markDirty();
	}

	private void setSessionPhase(SiegePhase phase) {
		updateSession(session -> new SiegeSession(
			phase,
			session.getSessionAgeLevel(),
			session.getSessionVictoryCount(),
			session.getObjectivePos(),
			session.getRallyPos(),
			session.getSpawnCenter(),
			session.getStartedGameTime(),
			session.getPhaseStartedGameTime(),
			session.getCountdownEndGameTime(),
			session.getAttackerIds(),
			session.getEngineIds(),
			session.getRoleAssignments(),
			session.getCurrentPlan(),
			session.getLastObservation(),
			session.getLastObservationTick(),
			session.getLastPlanTick(),
			session.getFallbackReason()
		));
	}

	private void resetCompatRuntime() {
		this.assaultModePrimedCompat = false;
		this.rushTicksCompat = 0;
		this.deploymentHoldTicksCompat = 0;
		this.breachedWallBlocksCompat = 0;
	}

	public String getDimensionId() {
		return dimensionId;
	}

	public String getClaimedBy() {
		return claimedBy;
	}

	public void setObjectiveHealthValue(int objectiveHealth) {
		this.objectiveHealth = objectiveHealth;
	}

	public int getTrackedWallHealthOrDefault(BlockPos pos, int defaultValue) {
		return wallHealth.getOrDefault(pos.asLong(), defaultValue);
	}

	public void setTrackedWallHealth(BlockPos pos, int remaining) {
		wallHealth.put(pos.asLong(), remaining);
	}

	public void removeTrackedWallHealth(BlockPos pos) {
		wallHealth.remove(pos.asLong());
	}

	public void incrementBreachedWallBlocks() {
		breachedWallBlocksCompat++;
	}

	public List<PlacedDefender> getPlacedDefenders() {
		return List.copyOf(placedDefenders);
	}

	public boolean hasDefenderAt(String placementDimensionId, BlockPos pos) {
		for (PlacedDefender defender : placedDefenders) {
			if (defender.dimensionId().equals(placementDimensionId) && defender.homePost().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	public void addPlacedDefender(PlacedDefender defender) {
		placedDefenders.add(defender);
		markDirty();
	}

	public PlacedDefender getPlacedDefender(UUID entityUuid) {
		for (PlacedDefender defender : placedDefenders) {
			if (defender.entityUuid().equals(entityUuid)) {
				return defender;
			}
		}
		return null;
	}

	public boolean removePlacedDefender(UUID entityUuid) {
		boolean removed = placedDefenders.removeIf(defender -> defender.entityUuid().equals(entityUuid));
		if (removed) {
			markDirty();
		}
		return removed;
	}

	public boolean replacePlacedDefender(UUID entityUuid, PlacedDefender replacement) {
		for (int i = 0; i < placedDefenders.size(); i++) {
			if (placedDefenders.get(i).entityUuid().equals(entityUuid)) {
				placedDefenders.set(i, replacement);
				markDirty();
				return true;
			}
		}
		return false;
	}
}
