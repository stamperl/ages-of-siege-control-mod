package com.stamperl.agesofsiege.report;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SiegeBattleStats {
	private final Map<String, Integer> spawnedAttackers;
	private final Map<String, Integer> attackerLosses;
	private final Map<String, Integer> spawnedDefenders;
	private final Map<String, Integer> defenderLosses;
	private final Map<UUID, String> attackerEntityKeys;
	private final Map<UUID, String> defenderEntityKeys;
	private final List<NbtCompound> fallenDefenderTokens;
	private final float playerDamageDealt;
	private final float playerDamageTaken;
	private final int playerKills;

	public SiegeBattleStats(
		Map<String, Integer> spawnedAttackers,
		Map<String, Integer> attackerLosses,
		Map<String, Integer> spawnedDefenders,
		Map<String, Integer> defenderLosses,
		Map<UUID, String> attackerEntityKeys,
		Map<UUID, String> defenderEntityKeys,
		List<NbtCompound> fallenDefenderTokens,
		float playerDamageDealt,
		float playerDamageTaken,
		int playerKills
	) {
		this.spawnedAttackers = copyIntMap(spawnedAttackers);
		this.attackerLosses = copyIntMap(attackerLosses);
		this.spawnedDefenders = copyIntMap(spawnedDefenders);
		this.defenderLosses = copyIntMap(defenderLosses);
		this.attackerEntityKeys = attackerEntityKeys == null ? Map.of() : Map.copyOf(attackerEntityKeys);
		this.defenderEntityKeys = defenderEntityKeys == null ? Map.of() : Map.copyOf(defenderEntityKeys);
		this.fallenDefenderTokens = copyCompounds(fallenDefenderTokens);
		this.playerDamageDealt = Math.max(0.0F, playerDamageDealt);
		this.playerDamageTaken = Math.max(0.0F, playerDamageTaken);
		this.playerKills = Math.max(0, playerKills);
	}

	public static SiegeBattleStats empty() {
		return new SiegeBattleStats(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), 0.0F, 0.0F, 0);
	}

	public Map<String, Integer> spawnedAttackers() {
		return spawnedAttackers;
	}

	public Map<String, Integer> attackerLosses() {
		return attackerLosses;
	}

	public Map<String, Integer> spawnedDefenders() {
		return spawnedDefenders;
	}

	public Map<String, Integer> defenderLosses() {
		return defenderLosses;
	}

	public Map<UUID, String> attackerEntityKeys() {
		return attackerEntityKeys;
	}

	public Map<UUID, String> defenderEntityKeys() {
		return defenderEntityKeys;
	}

	public List<NbtCompound> fallenDefenderTokens() {
		return fallenDefenderTokens;
	}

	public float playerDamageDealt() {
		return playerDamageDealt;
	}

	public float playerDamageTaken() {
		return playerDamageTaken;
	}

	public int playerKills() {
		return playerKills;
	}

	public SiegeBattleStats withSpawnedAttackers(Map<String, Integer> additions) {
		return new SiegeBattleStats(
			mergedIntMaps(spawnedAttackers, additions),
			attackerLosses,
			spawnedDefenders,
			defenderLosses,
			attackerEntityKeys,
			defenderEntityKeys,
			fallenDefenderTokens,
			playerDamageDealt,
			playerDamageTaken,
			playerKills
		);
	}

	public SiegeBattleStats withSpawnedDefenders(Map<String, Integer> additions, Map<UUID, String> entityKeys) {
		return new SiegeBattleStats(
			spawnedAttackers,
			attackerLosses,
			mergedIntMaps(spawnedDefenders, additions),
			defenderLosses,
			attackerEntityKeys,
			mergedUuidMap(defenderEntityKeys, entityKeys),
			fallenDefenderTokens,
			playerDamageDealt,
			playerDamageTaken,
			playerKills
		);
	}

	public SiegeBattleStats withSpawnedAttackerEntities(Map<UUID, String> entityKeys) {
		return new SiegeBattleStats(
			spawnedAttackers,
			attackerLosses,
			spawnedDefenders,
			defenderLosses,
			mergedUuidMap(attackerEntityKeys, entityKeys),
			defenderEntityKeys,
			fallenDefenderTokens,
			playerDamageDealt,
			playerDamageTaken,
			playerKills
		);
	}

	public SiegeBattleStats recordAttackerDeath(UUID entityUuid) {
		String key = attackerEntityKeys.get(entityUuid);
		if (key == null) {
			return this;
		}
		return new SiegeBattleStats(
			spawnedAttackers,
			increment(attackerLosses, key, 1),
			spawnedDefenders,
			defenderLosses,
			withoutUuid(attackerEntityKeys, entityUuid),
			defenderEntityKeys,
			fallenDefenderTokens,
			playerDamageDealt,
			playerDamageTaken,
			playerKills
		);
	}

	public SiegeBattleStats recordDefenderDeath(UUID entityUuid, NbtCompound returnedTokenData) {
		String key = defenderEntityKeys.get(entityUuid);
		if (key == null) {
			return this;
		}
		List<NbtCompound> returned = new ArrayList<>(fallenDefenderTokens);
		if (returnedTokenData != null && !returnedTokenData.isEmpty()) {
			returned.add(returnedTokenData.copy());
		}
		return new SiegeBattleStats(
			spawnedAttackers,
			attackerLosses,
			spawnedDefenders,
			increment(defenderLosses, key, 1),
			attackerEntityKeys,
			withoutUuid(defenderEntityKeys, entityUuid),
			returned,
			playerDamageDealt,
			playerDamageTaken,
			playerKills
		);
	}

	public SiegeBattleStats addPlayerDamageDealt(float amount) {
		if (amount <= 0.0F) {
			return this;
		}
		return new SiegeBattleStats(
			spawnedAttackers,
			attackerLosses,
			spawnedDefenders,
			defenderLosses,
			attackerEntityKeys,
			defenderEntityKeys,
			fallenDefenderTokens,
			playerDamageDealt + amount,
			playerDamageTaken,
			playerKills
		);
	}

	public SiegeBattleStats addPlayerDamageTaken(float amount) {
		if (amount <= 0.0F) {
			return this;
		}
		return new SiegeBattleStats(
			spawnedAttackers,
			attackerLosses,
			spawnedDefenders,
			defenderLosses,
			attackerEntityKeys,
			defenderEntityKeys,
			fallenDefenderTokens,
			playerDamageDealt,
			playerDamageTaken + amount,
			playerKills
		);
	}

	public SiegeBattleStats addPlayerKill() {
		return new SiegeBattleStats(
			spawnedAttackers,
			attackerLosses,
			spawnedDefenders,
			defenderLosses,
			attackerEntityKeys,
			defenderEntityKeys,
			fallenDefenderTokens,
			playerDamageDealt,
			playerDamageTaken,
			playerKills + 1
		);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.put("spawnedAttackers", writeIntMap(spawnedAttackers));
		nbt.put("attackerLosses", writeIntMap(attackerLosses));
		nbt.put("spawnedDefenders", writeIntMap(spawnedDefenders));
		nbt.put("defenderLosses", writeIntMap(defenderLosses));
		nbt.put("attackerEntityKeys", writeUuidMap(attackerEntityKeys));
		nbt.put("defenderEntityKeys", writeUuidMap(defenderEntityKeys));
		nbt.put("fallenDefenderTokens", writeCompoundList(fallenDefenderTokens));
		nbt.putFloat("playerDamageDealt", playerDamageDealt);
		nbt.putFloat("playerDamageTaken", playerDamageTaken);
		nbt.putInt("playerKills", playerKills);
		return nbt;
	}

	public static SiegeBattleStats fromNbt(NbtCompound nbt) {
		if (nbt == null || nbt.isEmpty()) {
			return empty();
		}
		return new SiegeBattleStats(
			readIntMap(nbt.getList("spawnedAttackers", NbtElement.COMPOUND_TYPE)),
			readIntMap(nbt.getList("attackerLosses", NbtElement.COMPOUND_TYPE)),
			readIntMap(nbt.getList("spawnedDefenders", NbtElement.COMPOUND_TYPE)),
			readIntMap(nbt.getList("defenderLosses", NbtElement.COMPOUND_TYPE)),
			readUuidMap(nbt.getList("attackerEntityKeys", NbtElement.COMPOUND_TYPE)),
			readUuidMap(nbt.getList("defenderEntityKeys", NbtElement.COMPOUND_TYPE)),
			readCompoundList(nbt.getList("fallenDefenderTokens", NbtElement.COMPOUND_TYPE)),
			nbt.getFloat("playerDamageDealt"),
			nbt.getFloat("playerDamageTaken"),
			nbt.getInt("playerKills")
		);
	}

	private static Map<String, Integer> copyIntMap(Map<String, Integer> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		Map<String, Integer> copy = new LinkedHashMap<>();
		source.forEach((key, value) -> {
			if (key != null && !key.isBlank() && value != null && value > 0) {
				copy.put(key, value);
			}
		});
		return Map.copyOf(copy);
	}

	private static Map<String, Integer> mergedIntMaps(Map<String, Integer> base, Map<String, Integer> additions) {
		Map<String, Integer> merged = new LinkedHashMap<>(copyIntMap(base));
		if (additions != null) {
			additions.forEach((key, value) -> {
				if (key != null && !key.isBlank() && value != null && value > 0) {
					merged.merge(key, value, Integer::sum);
				}
			});
		}
		return Map.copyOf(merged);
	}

	private static Map<String, Integer> increment(Map<String, Integer> base, String key, int delta) {
		Map<String, Integer> merged = new LinkedHashMap<>(copyIntMap(base));
		merged.merge(key, delta, Integer::sum);
		return Map.copyOf(merged);
	}

	private static Map<UUID, String> mergedUuidMap(Map<UUID, String> base, Map<UUID, String> additions) {
		Map<UUID, String> merged = new LinkedHashMap<>();
		if (base != null) {
			merged.putAll(base);
		}
		if (additions != null) {
			additions.forEach((key, value) -> {
				if (key != null && value != null && !value.isBlank()) {
					merged.put(key, value);
				}
			});
		}
		return Map.copyOf(merged);
	}

	private static Map<UUID, String> withoutUuid(Map<UUID, String> base, UUID uuid) {
		Map<UUID, String> copy = new LinkedHashMap<>();
		if (base != null) {
			copy.putAll(base);
		}
		copy.remove(uuid);
		return Map.copyOf(copy);
	}

	private static List<NbtCompound> copyCompounds(List<NbtCompound> source) {
		if (source == null || source.isEmpty()) {
			return List.of();
		}
		List<NbtCompound> copy = new ArrayList<>();
		for (NbtCompound compound : source) {
			if (compound != null && !compound.isEmpty()) {
				copy.add(compound.copy());
			}
		}
		return List.copyOf(copy);
	}

	private static NbtList writeIntMap(Map<String, Integer> values) {
		NbtList list = new NbtList();
		values.forEach((key, value) -> {
			NbtCompound entry = new NbtCompound();
			entry.putString("key", key);
			entry.putInt("count", value);
			list.add(entry);
		});
		return list;
	}

	private static Map<String, Integer> readIntMap(NbtList list) {
		Map<String, Integer> values = new LinkedHashMap<>();
		for (NbtElement element : list) {
			NbtCompound entry = (NbtCompound) element;
			values.put(entry.getString("key"), entry.getInt("count"));
		}
		return Map.copyOf(values);
	}

	private static NbtList writeUuidMap(Map<UUID, String> values) {
		NbtList list = new NbtList();
		values.forEach((uuid, key) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("id", uuid);
			entry.putString("key", key);
			list.add(entry);
		});
		return list;
	}

	private static Map<UUID, String> readUuidMap(NbtList list) {
		Map<UUID, String> values = new LinkedHashMap<>();
		for (NbtElement element : list) {
			NbtCompound entry = (NbtCompound) element;
			values.put(entry.getUuid("id"), entry.getString("key"));
		}
		return Map.copyOf(values);
	}

	private static NbtList writeCompoundList(List<NbtCompound> values) {
		NbtList list = new NbtList();
		for (NbtCompound compound : values) {
			if (compound != null && !compound.isEmpty()) {
				list.add(compound.copy());
			}
		}
		return list;
	}

	private static List<NbtCompound> readCompoundList(NbtList list) {
		List<NbtCompound> values = new ArrayList<>();
		for (NbtElement element : list) {
			values.add(((NbtCompound) element).copy());
		}
		return List.copyOf(values);
	}
}
