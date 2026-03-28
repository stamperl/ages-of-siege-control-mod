package com.stamperl.agesofsiege.defense;

import java.util.Locale;

public enum DefenderRole {
	SOLDIER("soldier", "Soldier", 0.0D, false, "ages_of_siege_soldier", 7, "Iron Arms"),
	ARCHER("archer", "Archer", 1.0D, true, "ages_of_siege_archer", 6, "Chain Mail");

	private final String id;
	private final String displayName;
	private final double leashRadius;
	private final boolean ranged;
	private final String entityTag;
	private final int attackPower;
	private final String armorLabel;

	DefenderRole(String id, String displayName, double leashRadius, boolean ranged, String entityTag, int attackPower, String armorLabel) {
		this.id = id;
		this.displayName = displayName;
		this.leashRadius = leashRadius;
		this.ranged = ranged;
		this.entityTag = entityTag;
		this.attackPower = attackPower;
		this.armorLabel = armorLabel;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public double leashRadius() {
		return leashRadius;
	}

	public boolean isRanged() {
		return ranged;
	}

	public String entityTag() {
		return entityTag;
	}

	public int attackPower() {
		return attackPower;
	}

	public String armorLabel() {
		return armorLabel;
	}

	public String tokenItemId() {
		return id + "_token";
	}

	public static DefenderRole from(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		for (DefenderRole role : values()) {
			if (role.id.equals(normalized) || role.name().equalsIgnoreCase(value)) {
				return role;
			}
		}
		return null;
	}
}
