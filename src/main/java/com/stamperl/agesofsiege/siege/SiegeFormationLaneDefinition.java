package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.siege.runtime.BattleLane;

import java.util.List;
import java.util.Objects;

public record SiegeFormationLaneDefinition(
	BattleLane lane,
	double lateralOffset,
	double depthOffset,
	double lateralStep,
	double depthStep,
	int rowWidth,
	List<String> preferredTags
) {
	public SiegeFormationLaneDefinition {
		Objects.requireNonNull(lane, "lane");
		rowWidth = Math.max(1, rowWidth);
		preferredTags = preferredTags == null ? List.of() : List.copyOf(preferredTags);
	}
}
