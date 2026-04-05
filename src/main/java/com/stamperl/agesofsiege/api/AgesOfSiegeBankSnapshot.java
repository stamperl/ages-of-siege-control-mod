package com.stamperl.agesofsiege.api;

public record AgesOfSiegeBankSnapshot(
	boolean tracked,
	int x,
	int y,
	int z,
	String dimensionId,
	int protectionCap,
	int health,
	int maxHealth
) {
	public static final AgesOfSiegeBankSnapshot DEFAULT = new AgesOfSiegeBankSnapshot(false, 0, 0, 0, "minecraft:overworld", 100, 20, 20);

	public AgesOfSiegeBankSnapshot {
		dimensionId = dimensionId == null || dimensionId.isBlank() ? "minecraft:overworld" : dimensionId;
	}
}
