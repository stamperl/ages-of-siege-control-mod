package com.stamperl.agesofsiege.api;

public record AgesOfSiegeAgeSnapshot(
	int level,
	String name,
	int completedSieges
) {
	public static final AgesOfSiegeAgeSnapshot DEFAULT = new AgesOfSiegeAgeSnapshot(0, "", 0);

	public AgesOfSiegeAgeSnapshot {
		name = name == null ? "" : name;
	}
}
