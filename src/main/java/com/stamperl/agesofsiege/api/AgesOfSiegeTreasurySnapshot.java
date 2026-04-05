package com.stamperl.agesofsiege.api;

import java.util.Collections;
import java.util.Map;

public record AgesOfSiegeTreasurySnapshot(
	long balance,
	Map<String, Long> depositedCoins
) {
	public static final AgesOfSiegeTreasurySnapshot DEFAULT = new AgesOfSiegeTreasurySnapshot(0L, Map.of());

	public AgesOfSiegeTreasurySnapshot {
		depositedCoins = depositedCoins == null ? Map.of() : Collections.unmodifiableMap(depositedCoins);
	}
}
