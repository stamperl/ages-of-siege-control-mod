package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.siege.runtime.BattleLane;

import java.util.List;
import java.util.Objects;

public record SiegeFormationDefinition(
	String id,
	String displayName,
	List<SiegeFormationLaneDefinition> lanes
) {
	public SiegeFormationDefinition {
		Objects.requireNonNull(id, "id");
		id = id.trim();
		displayName = displayName == null ? "" : displayName.trim();
		lanes = lanes == null ? List.of() : List.copyOf(lanes);
	}

	public SiegeFormationLaneDefinition laneOrFallback(BattleLane requestedLane) {
		if (requestedLane != null) {
			for (SiegeFormationLaneDefinition lane : lanes) {
				if (lane.lane() == requestedLane) {
					return lane;
				}
			}
		}
		for (SiegeFormationLaneDefinition lane : lanes) {
			if (lane.lane() == BattleLane.CENTER) {
				return lane;
			}
		}
		for (SiegeFormationLaneDefinition lane : lanes) {
			if (lane.lane() == BattleLane.FRONT) {
				return lane;
			}
		}
		return lanes.isEmpty() ? new SiegeFormationLaneDefinition(BattleLane.CENTER, 0.0D, 3.0D, 3.0D, 3.0D, 4, List.of()) : lanes.get(0);
	}
}
