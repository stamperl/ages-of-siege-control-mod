package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.state.SiegeBaseState;

import java.util.Objects;

public record SiegeCampaignNode(
	String id,
	String displayName,
	String description,
	int ageLevel,
	int routeColumn,
	int routeRow,
	boolean ageDefining,
	int requiredRegularWins,
	String battleTemplateId,
	int warSuppliesReward,
	boolean minorRaid
) {
	public SiegeCampaignNode {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(battleTemplateId, "battleTemplateId");
		id = id.trim();
		displayName = displayName.trim();
		description = description.trim();
		battleTemplateId = battleTemplateId.trim();
	}

	public boolean isUnlocked(SiegeBaseState state) {
		if (state == null) {
			return false;
		}
		if (ageLevel < state.getAgeLevel()) {
			return true;
		}
		if (ageLevel > state.getAgeLevel()) {
			return false;
		}
		return state.getCurrentAgeRegularWins() >= requiredRegularWins;
	}

	public boolean isReplay(SiegeBaseState state) {
		return state != null && ageLevel < state.getAgeLevel();
	}
}
