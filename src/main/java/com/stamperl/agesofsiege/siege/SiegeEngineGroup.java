package com.stamperl.agesofsiege.siege;

import java.util.List;
import java.util.Objects;

public record SiegeEngineGroup(
	String id,
	String engineType,
	String entityType,
	int count,
	List<String> tags
) {
	public SiegeEngineGroup {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(engineType, "engineType");
		id = id.trim();
		engineType = engineType.trim();
		entityType = entityType == null ? "" : entityType.trim();
		tags = tags == null ? List.of() : List.copyOf(tags);
	}
}
