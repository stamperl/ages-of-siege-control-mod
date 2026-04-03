package com.stamperl.agesofsiege.defense;

import com.stamperl.agesofsiege.item.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DefenderTokenData {
	public static final String ROOT_KEY = "AgesOfSiegeDefender";
	private static final String IDENTITY_KEY = "identity";
	private static final String LOADOUT_KEY = "loadout";
	private static final String PROGRESSION_KEY = "progression";
	private static final String STATS_KEY = "stats";
	private static final String STATUS_KEY = "status";
	private static final String APPEARANCE_KEY = "appearance";
	private static final String HERO_DATA_KEY = "heroData";
	private static final int XP_PER_LEVEL = 25;
	private static final int POINTS_PER_LEVEL = 2;
	private static final int TOKEN_MAX_DAMAGE = 240;
	private static final int FALLEN_DAMAGE = 192;
	private static final String[] SOLDIER_GIVEN = {"Ald", "Bram", "Cedric", "Edwin", "Garrick", "Hugh", "Ivor", "Leof", "Martin", "Osric", "Perrin", "Rowan", "Tobin", "Wulfric", "Aric", "Baldric", "Clement", "Darian", "Eamon", "Fendrel", "Godric", "Harwin", "Jerron", "Loric", "Merek", "Owyn", "Roder", "Steffan", "Ulric"};
	private static final String[] ARCHER_GIVEN = {"Ansel", "Bennet", "Corin", "Darin", "Ellis", "Falk", "Giles", "Hal", "Jory", "Merrit", "Noll", "Robin", "Sayer", "Tarin", "Alwen", "Brice", "Caelan", "Dennet", "Eldric", "Finn", "Garran", "Hob", "Ilan", "Kester", "Larkin", "Milo", "Niven", "Orrin", "Piers", "Rafe"};
	private static final String[] FAMILY = {"Ashdown", "Barrow", "Blackthorn", "Dale", "Fenwick", "Hawke", "Miller", "Rook", "Thatcher", "Vale", "Ward", "Wren", "Brook", "Coldharbor", "Dray", "Eaves", "Fallow", "Greymark", "Hart", "Kingsley", "Marsh", "Pike", "Redmere", "Stone", "Underhill", "Westfall"};

	private DefenderTokenData() {
	}

	public static int maxDamage() {
		return TOKEN_MAX_DAMAGE;
	}

	public static NbtCompound ensureDeploymentData(ItemStack stack, DefenderRole role, int ageLevel, Random random, Iterable<String> existingNames) {
		NbtCompound data = getData(stack);
		if (data == null) {
			data = createFreshData(role, ageLevel, random, existingNames);
		}
		data = data.copy();
		ensureIdentity(data, role, ageLevel, random, existingNames);
		ensureProgression(data);
		ensureStats(data);
		ensureStatus(data, false);
		ensureLoadout(data, role, ageLevel);
		recalculateDerivedStats(data, role, ageLevel);
		increment(data.getCompound(PROGRESSION_KEY), "deployments", 1);
		data.getCompound(STATUS_KEY).putBoolean("fallen", false);
		data.getCompound(STATUS_KEY).putBoolean("active", true);
		data.getCompound(STATUS_KEY).putString("repairState", "ready");
		writeToStack(stack, data);
		return data.copy();
	}

	public static NbtCompound createFreshData(DefenderRole role, int ageLevel, Random random) {
		return createFreshData(role, ageLevel, random, List.of());
	}

	public static NbtCompound createFreshData(DefenderRole role, int ageLevel, Random random, Iterable<String> existingNames) {
		NbtCompound root = new NbtCompound();
		ensureIdentity(root, role, ageLevel, random, existingNames);
		ensureProgression(root);
		ensureStats(root);
		ensureStatus(root, false);
		root.put(APPEARANCE_KEY, new NbtCompound());
		root.put(HERO_DATA_KEY, new NbtCompound());
		root.put(LOADOUT_KEY, createStarterLoadout(role, ageLevel));
		recalculateDerivedStats(root, role, ageLevel);
		return root;
	}

	public static NbtCompound createLegacyData(DefenderRole role, String displayName) {
		NbtCompound root = createFreshData(role, 0, Random.create());
		root.getCompound(IDENTITY_KEY).putString("name", sanitizeName(displayName, role));
		return root;
	}

	public static NbtCompound getData(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(ROOT_KEY, NbtElement.COMPOUND_TYPE)) {
			return null;
		}
		return nbt.getCompound(ROOT_KEY).copy();
	}

	public static void writeToStack(ItemStack stack, NbtCompound data) {
		if (stack == null || stack.isEmpty() || data == null) {
			return;
		}
		stack.getOrCreateNbt().put(ROOT_KEY, data.copy());
		stack.setCustomName(Text.literal(displayName(data, resolveRole(data, DefenderRole.SOLDIER))));
	}

	public static ItemStack createTokenStack(DefenderRole role, NbtCompound data, boolean fallen) {
		Item item = role == DefenderRole.ARCHER ? ModItems.ARCHER_TOKEN : ModItems.SOLDIER_TOKEN;
		ItemStack stack = new ItemStack(item);
		writeToStack(stack, data);
		stack.setDamage(fallen ? FALLEN_DAMAGE : 0);
		return stack;
	}

	public static NbtCompound captureEntityState(NbtCompound tokenData, LivingEntity entity, DefenderRole fallbackRole, boolean fallen) {
		NbtCompound data = tokenData == null ? createFreshData(fallbackRole, 0, Random.create()) : tokenData.copy();
		DefenderRole role = resolveRole(data, fallbackRole);
		ensureIdentity(data, role, 0, Random.create(), List.of());
		ensureProgression(data);
		ensureStats(data);
		NbtCompound loadout = snapshotLoadout(entity);
		data.put(LOADOUT_KEY, loadout);
		NbtCompound status = data.getCompound(STATUS_KEY);
		status.putBoolean("fallen", fallen);
		status.putBoolean("active", false);
		status.putString("repairState", fallen ? "damaged" : "ready");
		if (entity.getCustomName() != null) {
			data.getCompound(IDENTITY_KEY).putString("name", entity.getCustomName().getString());
		}
		return data;
	}

	public static void applyToEntity(LivingEntity entity, NbtCompound tokenData, DefenderRole fallbackRole) {
		NbtCompound data = tokenData == null ? createFreshData(fallbackRole, 0, Random.create()) : ensureWorkbenchData(tokenData, fallbackRole);
		DefenderRole role = resolveRole(data, fallbackRole);
		entity.setCustomName(Text.literal(displayName(data, role)));
		entity.setCustomNameVisible(false);
		applyLoadout(entity, data.getCompound(LOADOUT_KEY), role, 0);

		EntityAttributeInstance maxHealth = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		NbtCompound stats = data.getCompound(STATS_KEY);
		if (maxHealth != null) {
			double base = fallbackRole == DefenderRole.ARCHER ? 14.0D : 18.0D;
			double bonus = stats.contains("bonusHealth") ? stats.getDouble("bonusHealth") : 0.0D;
			maxHealth.setBaseValue(base + bonus);
			entity.setHealth((float) maxHealth.getValue());
		}
		EntityAttributeInstance attackDamage = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (attackDamage != null) {
			double roleBase = role == DefenderRole.ARCHER ? 2.0D : 3.0D;
			double bonus = stats.contains("bonusAttack") ? stats.getDouble("bonusAttack") : 0.0D;
			attackDamage.setBaseValue(roleBase + bonus);
		}
		EntityAttributeInstance armor = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armor != null) {
			double bonus = stats.contains("bonusArmor") ? stats.getDouble("bonusArmor") : 0.0D;
			armor.setBaseValue(bonus);
		}
		EntityAttributeInstance movementSpeed = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (movementSpeed != null) {
			double base = entity.getType().create(entity.getWorld()) instanceof LivingEntity created
				? created.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
				: movementSpeed.getBaseValue();
			double bonus = stats.contains("bonusSpeed") ? stats.getDouble("bonusSpeed") : 0.0D;
			movementSpeed.setBaseValue(base + bonus);
		}
	}

	public static NbtCompound withRoleAndStarterLoadout(NbtCompound tokenData, DefenderRole role, int ageLevel) {
		NbtCompound data = tokenData == null ? createFreshData(role, ageLevel, Random.create()) : tokenData.copy();
		ensureIdentity(data, role, ageLevel, Random.create(), List.of());
		data.getCompound(IDENTITY_KEY).putString("role", role.id());
		data.put(LOADOUT_KEY, createStarterLoadout(role, ageLevel));
		recalculateDerivedStats(data, role, ageLevel);
		return data;
	}

	public static NbtCompound withManualName(NbtCompound tokenData, DefenderRole role, String name) {
		NbtCompound data = tokenData == null ? createFreshData(role, 0, Random.create()) : tokenData.copy();
		data.getCompound(IDENTITY_KEY).putString("name", sanitizeName(name, role));
		return data;
	}

	public static NbtCompound addKill(NbtCompound tokenData, DefenderRole role) {
		NbtCompound data = tokenData == null ? createFreshData(role, 0, Random.create()) : ensureWorkbenchData(tokenData, role);
		NbtCompound progression = data.getCompound(PROGRESSION_KEY);
		increment(progression, "kills", 1);
		addXp(progression, 5);
		recalculateDerivedStats(data, role, 0);
		return data;
	}

	public static NbtCompound addVictory(NbtCompound tokenData, DefenderRole role) {
		NbtCompound data = tokenData == null ? createFreshData(role, 0, Random.create()) : ensureWorkbenchData(tokenData, role);
		NbtCompound progression = data.getCompound(PROGRESSION_KEY);
		increment(progression, "wins", 1);
		addXp(progression, 10);
		recalculateDerivedStats(data, role, 0);
		return data;
	}

	public static NbtCompound ensureWorkbenchData(ItemStack stack, DefenderRole role) {
		NbtCompound data = getData(stack);
		NbtCompound ensured = ensureWorkbenchData(data, role);
		writeToStack(stack, ensured);
		return ensured.copy();
	}

	public static NbtCompound ensureWorkbenchData(NbtCompound tokenData, DefenderRole fallbackRole) {
		NbtCompound data = tokenData == null ? createFreshData(fallbackRole, 0, Random.create()) : tokenData.copy();
		DefenderRole role = resolveRole(data, fallbackRole);
		ensureIdentity(data, role, 0, Random.create(), List.of());
		ensureProgression(data);
		ensureStats(data);
		ensureStatus(data, false);
		ensureLoadout(data, role, 0);
		recalculateDerivedStats(data, role, 0);
		return data;
	}

	public static boolean canUseWorkbench(ItemStack stack) {
		return stack != null
			&& !stack.isEmpty()
			&& stack.isOf(ModItems.SOLDIER_TOKEN)
			&& !requiresRepair(stack);
	}

	public static int level(NbtCompound tokenData, DefenderRole fallbackRole) {
		return ensureWorkbenchData(tokenData, fallbackRole).getCompound(PROGRESSION_KEY).getInt("level");
	}

	public static int xp(NbtCompound tokenData, DefenderRole fallbackRole) {
		return ensureWorkbenchData(tokenData, fallbackRole).getCompound(PROGRESSION_KEY).getInt("xp");
	}

	public static int xpToNextLevel(NbtCompound tokenData, DefenderRole fallbackRole) {
		return ensureWorkbenchData(tokenData, fallbackRole).getCompound(PROGRESSION_KEY).getInt("xpToNextLevel");
	}

	public static int availablePoints(NbtCompound tokenData, DefenderRole fallbackRole) {
		return ensureWorkbenchData(tokenData, fallbackRole).getCompound(PROGRESSION_KEY).getInt("availablePoints");
	}

	public static int statValue(NbtCompound tokenData, DefenderRole fallbackRole, WorkbenchStat stat) {
		if (stat == null) {
			return 0;
		}
		return ensureWorkbenchData(tokenData, fallbackRole).getCompound(STATS_KEY).getInt(stat.id());
	}

	public static WorkbenchArmorTier equippedArmorTier(NbtCompound tokenData, DefenderRole fallbackRole) {
		NbtCompound data = ensureWorkbenchData(tokenData, fallbackRole);
		return WorkbenchArmorTier.from(data.getCompound(LOADOUT_KEY).getString("armorTier"));
	}

	public static boolean isArmorTierUnlocked(NbtCompound tokenData, DefenderRole fallbackRole, WorkbenchArmorTier tier) {
		return tier != null && tier.isUnlockedAt(level(tokenData, fallbackRole));
	}

	public static NbtCompound spendPoint(NbtCompound tokenData, DefenderRole fallbackRole, WorkbenchStat stat) {
		if (stat == null) {
			return ensureWorkbenchData(tokenData, fallbackRole);
		}
		NbtCompound data = ensureWorkbenchData(tokenData, fallbackRole);
		NbtCompound progression = data.getCompound(PROGRESSION_KEY);
		int availablePoints = progression.getInt("availablePoints");
		if (availablePoints <= 0) {
			return data;
		}
		NbtCompound stats = data.getCompound(STATS_KEY);
		stats.putInt(stat.id(), stats.getInt(stat.id()) + 1);
		progression.putInt("availablePoints", availablePoints - 1);
		recalculateDerivedStats(data, resolveRole(data, fallbackRole), 0);
		return data;
	}

	public static NbtCompound equipArmorTier(NbtCompound tokenData, DefenderRole fallbackRole, WorkbenchArmorTier tier) {
		if (tier == null) {
			return ensureWorkbenchData(tokenData, fallbackRole);
		}
		NbtCompound data = ensureWorkbenchData(tokenData, fallbackRole);
		if (!isArmorTierUnlocked(data, fallbackRole, tier)) {
			return data;
		}
		NbtCompound loadout = data.getCompound(LOADOUT_KEY);
		loadout.putString("armorTier", tier.id());
		applyArmorTier(loadout, resolveRole(data, fallbackRole), tier);
		recalculateDerivedStats(data, resolveRole(data, fallbackRole), 0);
		return data;
	}

	public static String armorLabel(NbtCompound tokenData, DefenderRole fallbackRole) {
		return equippedArmorTier(tokenData, fallbackRole).displayName();
	}

	public static DefenderRole resolveRole(NbtCompound tokenData, DefenderRole fallbackRole) {
		if (tokenData == null) {
			return fallbackRole;
		}
		NbtCompound identity = tokenData.getCompound(IDENTITY_KEY);
		DefenderRole resolved = DefenderRole.from(identity.getString("role"));
		return resolved == null ? fallbackRole : resolved;
	}

	public static String displayName(NbtCompound tokenData, DefenderRole fallbackRole) {
		if (tokenData == null) {
			return fallbackRole.displayName() + " Guard";
		}
		String name = tokenData.getCompound(IDENTITY_KEY).getString("name");
		return sanitizeName(name, fallbackRole);
	}

	public static boolean requiresRepair(ItemStack stack) {
		return stack != null && !stack.isEmpty() && requiresRepair(getData(stack));
	}

	public static boolean requiresRepair(NbtCompound tokenData) {
		if (tokenData == null || tokenData.isEmpty()) {
			return false;
		}
		NbtCompound status = tokenData.getCompound(STATUS_KEY);
		return status.getBoolean("fallen") || "damaged".equalsIgnoreCase(status.getString("repairState"));
	}

	public static boolean canDeploy(ItemStack stack) {
		return stack != null && !stack.isEmpty() && !requiresRepair(stack);
	}

	public static ItemStack repairToken(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		NbtCompound data = getData(stack);
		if (data == null) {
			return stack.copy();
		}
		NbtCompound repaired = data.copy();
		NbtCompound status = repaired.getCompound(STATUS_KEY);
		status.putBoolean("fallen", false);
		status.putBoolean("active", false);
		status.putString("repairState", "ready");
		ItemStack copy = stack.copy();
		writeToStack(copy, repaired);
		copy.setDamage(0);
		return copy;
	}

	private static void ensureIdentity(NbtCompound root, DefenderRole role, int ageLevel, Random random, Iterable<String> existingNames) {
		NbtCompound identity = root.contains(IDENTITY_KEY, NbtElement.COMPOUND_TYPE)
			? root.getCompound(IDENTITY_KEY)
			: new NbtCompound();
		if (!identity.containsUuid("tokenUuid")) {
			identity.putUuid("tokenUuid", UUID.randomUUID());
		}
		identity.putString("role", role.id());
		if (!identity.contains("name") || identity.getString("name").isBlank()) {
			identity.putString("name", generateName(role, random, existingNames));
		}
		if (!identity.contains("originAge")) {
			identity.putInt("originAge", Math.max(0, ageLevel));
		}
		root.put(IDENTITY_KEY, identity);
	}

	private static void ensureProgression(NbtCompound root) {
		NbtCompound progression = root.contains(PROGRESSION_KEY, NbtElement.COMPOUND_TYPE)
			? root.getCompound(PROGRESSION_KEY)
			: new NbtCompound();
		if (!progression.contains("level")) {
			progression.putInt("level", 1);
		}
		if (!progression.contains("xp")) {
			progression.putInt("xp", 0);
		}
		if (!progression.contains("xpToNextLevel")) {
			progression.putInt("xpToNextLevel", XP_PER_LEVEL);
		}
		if (!progression.contains("availablePoints")) {
			progression.putInt("availablePoints", 0);
		}
		if (!progression.contains("kills")) {
			progression.putInt("kills", 0);
		}
		if (!progression.contains("wins")) {
			progression.putInt("wins", 0);
		}
		if (!progression.contains("deployments")) {
			progression.putInt("deployments", 0);
		}
		syncProgressionFields(progression);
		root.put(PROGRESSION_KEY, progression);
	}

	private static void ensureStats(NbtCompound root) {
		NbtCompound stats = root.contains(STATS_KEY, NbtElement.COMPOUND_TYPE)
			? root.getCompound(STATS_KEY)
			: new NbtCompound();
		if (!stats.contains("bonusHealth")) {
			stats.putDouble("bonusHealth", 0.0D);
		}
		if (!stats.contains("bonusAttack")) {
			stats.putDouble("bonusAttack", 0.0D);
		}
		if (!stats.contains("bonusArmor")) {
			stats.putDouble("bonusArmor", 0.0D);
		}
		if (!stats.contains("bonusSpeed")) {
			stats.putDouble("bonusSpeed", 0.0D);
		}
		if (!stats.contains("vitality")) {
			stats.putInt("vitality", 0);
		}
		if (!stats.contains("strength")) {
			stats.putInt("strength", 0);
		}
		if (!stats.contains("discipline")) {
			stats.putInt("discipline", 0);
		}
		if (!stats.contains("agility")) {
			stats.putInt("agility", 0);
		}
		root.put(STATS_KEY, stats);
		if (!root.contains(APPEARANCE_KEY, NbtElement.COMPOUND_TYPE)) {
			root.put(APPEARANCE_KEY, new NbtCompound());
		}
		if (!root.contains(HERO_DATA_KEY, NbtElement.COMPOUND_TYPE)) {
			root.put(HERO_DATA_KEY, new NbtCompound());
		}
	}

	private static void ensureStatus(NbtCompound root, boolean fallen) {
		NbtCompound status = root.contains(STATUS_KEY, NbtElement.COMPOUND_TYPE)
			? root.getCompound(STATUS_KEY)
			: new NbtCompound();
		if (!status.contains("fallen")) {
			status.putBoolean("fallen", fallen);
		}
		if (!status.contains("active")) {
			status.putBoolean("active", false);
		}
		if (!status.contains("repairState")) {
			status.putString("repairState", fallen ? "damaged" : "ready");
		}
		root.put(STATUS_KEY, status);
	}

	private static void ensureLoadout(NbtCompound root, DefenderRole role, int ageLevel) {
		if (!root.contains(LOADOUT_KEY, NbtElement.COMPOUND_TYPE) || root.getCompound(LOADOUT_KEY).isEmpty()) {
			root.put(LOADOUT_KEY, createStarterLoadout(role, ageLevel));
			return;
		}
		NbtCompound loadout = root.getCompound(LOADOUT_KEY);
		if (!loadout.contains("armorTier")) {
			loadout.putString("armorTier", inferArmorTier(loadout).id());
		}
	}

	private static NbtCompound createStarterLoadout(DefenderRole role, int ageLevel) {
		NbtCompound loadout = new NbtCompound();
		if (role == DefenderRole.ARCHER) {
			writeStack(loadout, "mainhand", new ItemStack(Items.BOW));
			writeStack(loadout, "offhand", new ItemStack(Items.WOODEN_SWORD));
			WorkbenchArmorTier tier = ageLevel >= 3 ? WorkbenchArmorTier.CHAINMAIL : WorkbenchArmorTier.LEATHER;
			loadout.putString("armorTier", tier.id());
			applyArmorTier(loadout, role, tier);
			return loadout;
		}

		writeStack(loadout, "mainhand", new ItemStack(Items.WOODEN_SWORD));
		writeStack(loadout, "offhand", new ItemStack(Items.SHIELD));
		WorkbenchArmorTier tier = ageLevel >= 3 ? WorkbenchArmorTier.IRON : ageLevel >= 2 ? WorkbenchArmorTier.CHAINMAIL : WorkbenchArmorTier.LEATHER;
		loadout.putString("armorTier", tier.id());
		applyArmorTier(loadout, role, tier);
		return loadout;
	}

	private static NbtCompound snapshotLoadout(LivingEntity entity) {
		NbtCompound loadout = new NbtCompound();
		writeStack(loadout, "mainhand", entity.getEquippedStack(EquipmentSlot.MAINHAND).copy());
		writeStack(loadout, "offhand", entity.getEquippedStack(EquipmentSlot.OFFHAND).copy());
		writeStack(loadout, "head", entity.getEquippedStack(EquipmentSlot.HEAD).copy());
		writeStack(loadout, "chest", entity.getEquippedStack(EquipmentSlot.CHEST).copy());
		writeStack(loadout, "legs", entity.getEquippedStack(EquipmentSlot.LEGS).copy());
		writeStack(loadout, "feet", entity.getEquippedStack(EquipmentSlot.FEET).copy());
		return loadout;
	}

	private static void applyLoadout(LivingEntity entity, NbtCompound loadout, DefenderRole role, int ageLevel) {
		NbtCompound source = loadout == null || loadout.isEmpty() ? createStarterLoadout(role, ageLevel) : loadout;
		entity.equipStack(EquipmentSlot.MAINHAND, readStack(source, "mainhand"));
		entity.equipStack(EquipmentSlot.OFFHAND, readStack(source, "offhand"));
		entity.equipStack(EquipmentSlot.HEAD, readStack(source, "head"));
		entity.equipStack(EquipmentSlot.CHEST, readStack(source, "chest"));
		entity.equipStack(EquipmentSlot.LEGS, readStack(source, "legs"));
		entity.equipStack(EquipmentSlot.FEET, readStack(source, "feet"));
	}

	private static void writeStack(NbtCompound parent, String key, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		parent.put(key, stack.writeNbt(new NbtCompound()));
	}

	private static ItemStack readStack(NbtCompound parent, String key) {
		if (parent == null || !parent.contains(key, NbtElement.COMPOUND_TYPE)) {
			return ItemStack.EMPTY;
		}
		return ItemStack.fromNbt(parent.getCompound(key));
	}

	private static void increment(NbtCompound compound, String key, int amount) {
		compound.putInt(key, compound.getInt(key) + Math.max(0, amount));
	}

	private static void addXp(NbtCompound progression, int amount) {
		int previousLevel = Math.max(1, progression.getInt("level"));
		int xp = progression.getInt("xp") + Math.max(0, amount);
		progression.putInt("xp", xp);
		int nextLevel = Math.max(1, 1 + (xp / XP_PER_LEVEL));
		progression.putInt("level", nextLevel);
		if (nextLevel > previousLevel) {
			int gainedLevels = nextLevel - previousLevel;
			progression.putInt("availablePoints", progression.getInt("availablePoints") + (gainedLevels * POINTS_PER_LEVEL));
		}
		syncProgressionFields(progression);
	}

	private static void syncProgressionFields(NbtCompound progression) {
		int level = Math.max(1, progression.getInt("level"));
		progression.putInt("level", level);
		int xp = Math.max(0, progression.getInt("xp"));
		progression.putInt("xp", xp);
		int nextThreshold = level * XP_PER_LEVEL;
		progression.putInt("xpToNextLevel", Math.max(0, nextThreshold - xp));
		progression.putInt("availablePoints", Math.max(0, progression.getInt("availablePoints")));
	}

	private static void recalculateDerivedStats(NbtCompound root, DefenderRole role, int ageLevel) {
		NbtCompound stats = root.getCompound(STATS_KEY);
		NbtCompound loadout = root.getCompound(LOADOUT_KEY);
		WorkbenchArmorTier tier = WorkbenchArmorTier.from(loadout.getString("armorTier"));
		if (role == DefenderRole.SOLDIER) {
			applyArmorTier(loadout, role, tier);
		}
		stats.putDouble("bonusHealth", stats.getInt("vitality") * 2.0D);
		stats.putDouble("bonusAttack", stats.getInt("strength") * 0.75D);
		stats.putDouble("bonusArmor", armorValue(tier) + stats.getInt("discipline"));
		stats.putDouble("bonusSpeed", stats.getInt("agility") * 0.01D);
		root.put(STATS_KEY, stats);
		root.put(LOADOUT_KEY, loadout);
	}

	private static int armorValue(WorkbenchArmorTier tier) {
		return switch (tier) {
			case LEATHER -> 2;
			case CHAINMAIL -> 5;
			case IRON -> 8;
		};
	}

	private static void applyArmorTier(NbtCompound loadout, DefenderRole role, WorkbenchArmorTier tier) {
		if (role == DefenderRole.ARCHER) {
			return;
		}
		loadout.putString("armorTier", tier.id());
		writeStack(loadout, "head", tier.createArmorPiece(EquipmentSlot.HEAD));
		writeStack(loadout, "chest", tier.createArmorPiece(EquipmentSlot.CHEST));
		writeStack(loadout, "legs", tier.createArmorPiece(EquipmentSlot.LEGS));
		writeStack(loadout, "feet", tier.createArmorPiece(EquipmentSlot.FEET));
	}

	private static WorkbenchArmorTier inferArmorTier(NbtCompound loadout) {
		ItemStack chest = readStack(loadout, "chest");
		if (chest.isOf(Items.IRON_CHESTPLATE)) {
			return WorkbenchArmorTier.IRON;
		}
		if (chest.isOf(Items.CHAINMAIL_CHESTPLATE)) {
			return WorkbenchArmorTier.CHAINMAIL;
		}
		return WorkbenchArmorTier.LEATHER;
	}

	private static String generateName(DefenderRole role, Random random, Iterable<String> existingNames) {
		String[] givenPool = role == DefenderRole.ARCHER ? ARCHER_GIVEN : SOLDIER_GIVEN;
		for (int attempt = 0; attempt < 24; attempt++) {
			String given = givenPool[random.nextInt(givenPool.length)];
			String family = FAMILY[random.nextInt(FAMILY.length)];
			String candidate = given + " " + family;
			if (!containsName(existingNames, candidate)) {
				return candidate;
			}
		}
		String fallback = givenPool[random.nextInt(givenPool.length)] + " " + FAMILY[random.nextInt(FAMILY.length)];
		return fallback + " " + (2 + random.nextInt(98));
	}

	private static boolean containsName(Iterable<String> existingNames, String candidate) {
		if (existingNames == null) {
			return false;
		}
		for (String existing : existingNames) {
			if (existing != null && existing.equalsIgnoreCase(candidate)) {
				return true;
			}
		}
		return false;
	}

	private static String sanitizeName(String value, DefenderRole role) {
		if (value == null || value.isBlank()) {
			return role.displayName() + " Guard";
		}
		return value.trim();
	}

	public static ItemStack optionalRegisteredStack(String itemId, int count) {
		if (itemId == null || itemId.isBlank()) {
			return ItemStack.EMPTY;
		}
		Identifier id = Identifier.tryParse(itemId);
		if (id == null || !Registries.ITEM.containsId(id)) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(Registries.ITEM.get(id), Math.max(1, count));
	}

	public static String previewLabel(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "Unknown";
		}
		return stack.getName().getString();
	}

	public static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return Registries.ITEM.getId(stack.getItem()).toString();
	}

	public static String humanizeRole(DefenderRole role) {
		return role == null ? "Defender" : role.displayName().toLowerCase(Locale.ROOT);
	}
}
