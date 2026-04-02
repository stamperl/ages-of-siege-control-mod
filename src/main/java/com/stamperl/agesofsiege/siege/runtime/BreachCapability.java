package com.stamperl.agesofsiege.siege.runtime;

import java.util.Locale;

public enum BreachCapability {
	NONE,
	FALLBACK,
	PRIMARY;

	public static BreachCapability parse(String value) {
		if (value == null || value.isBlank()) {
			return NONE;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "primary", "breacher" -> PRIMARY;
			case "fallback", "backup" -> FALLBACK;
			default -> NONE;
		};
	}
}
