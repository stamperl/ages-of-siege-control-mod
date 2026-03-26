package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.siege.runtime.BattlefieldObservation;
import com.stamperl.agesofsiege.siege.runtime.SiegePhase;
import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class SiegeDebug {
	public static final boolean ENABLED = true;

	private SiegeDebug() {
	}

	public static void log(String message, Object... args) {
		if (!ENABLED) {
			return;
		}
		AgesOfSiegeMod.LOGGER.info("[SiegeDebug] " + message, args);
	}

	public static void logPhaseChange(SiegeSession session, SiegePhase nextPhase, long now, String reason) {
		if (!ENABLED) {
			return;
		}
		log(
			"phase {} -> {} at tick={} reason={} attackers={} engines={}",
			session.getPhase(),
			nextPhase,
			now,
			reason,
			session.getAttackerIds().size(),
			session.getEngineIds().size()
		);
	}

	public static void logObservation(ServerWorld world, BattlefieldObservation observation, BlockPos objectivePos) {
		if (!ENABLED) {
			return;
		}
		log(
			"observation tick={} objective={} present={} livingAttackers={} breachers={} ranged={} escorts={} rams={} path={} openings={} wallSegments={}",
			world.getTime(),
			objectivePos,
			observation.isObjectivePresent(),
			observation.getTotalLivingAttackers(),
			observation.getLivingBreachers(),
			observation.getLivingRanged(),
			observation.getLivingEscorts(),
			observation.getLivingRamCount(),
			observation.isPathToObjectiveExists(),
			observation.getCandidateBreachOpenings().size(),
			observation.getIntactWallSegments().size()
		);
	}

	public static void logPlan(ServerWorld world, SiegePlan plan, String source) {
		if (!ENABLED || plan == null) {
			return;
		}
		log(
			"plan source={} tick={} type={} staging={} breachAnchor={} targetBlocks={} breachExit={} reachableAfter={} width={} height={} confidence={}",
			source,
			world.getTime(),
			plan.planType(),
			plan.stagingPoint(),
			plan.primaryBreachAnchor(),
			plan.targetBlocks().size(),
			plan.breachExit(),
			plan.objectiveReachableAfterBreach(),
			plan.openingWidth(),
			plan.openingHeight(),
			plan.confidence()
		);
	}
}
