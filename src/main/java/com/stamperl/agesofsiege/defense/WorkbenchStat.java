package com.stamperl.agesofsiege.defense;

public enum WorkbenchStat {
	VITALITY("vitality", "Vitality"),
	STRENGTH("strength", "Strength"),
	DISCIPLINE("discipline", "Discipline"),
	AGILITY("agility", "Agility");

	private final String id;
	private final String displayName;

	WorkbenchStat(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public static WorkbenchStat from(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (WorkbenchStat stat : values()) {
			if (stat.id.equalsIgnoreCase(value) || stat.name().equalsIgnoreCase(value)) {
				return stat;
			}
		}
		return null;
	}
}
