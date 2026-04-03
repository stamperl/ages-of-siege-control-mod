package com.stamperl.agesofsiege.block;

import net.minecraft.util.StringIdentifiable;

public enum ArmyWorkBenchPart implements StringIdentifiable {
	MAIN("main"),
	EXTENSION("extension");

	private final String id;

	ArmyWorkBenchPart(String id) {
		this.id = id;
	}

	@Override
	public String asString() {
		return id;
	}
}
