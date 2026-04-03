package com.stamperl.agesofsiege.workbench;

import com.stamperl.agesofsiege.AgesOfSiegeMod;
import com.stamperl.agesofsiege.block.ArmyWorkBenchBlock;
import com.stamperl.agesofsiege.block.ArmyWorkBenchBlockEntity;
import com.stamperl.agesofsiege.defense.DefenderRole;
import com.stamperl.agesofsiege.defense.DefenderTokenData;
import com.stamperl.agesofsiege.defense.WorkbenchArmorTier;
import com.stamperl.agesofsiege.defense.WorkbenchStat;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ArmyWorkBenchService {
	public static final Identifier OPEN_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_work_bench_open");
	public static final Identifier SPEND_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_work_bench_spend");
	public static final Identifier EQUIP_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_work_bench_equip");
	public static final Identifier EJECT_PACKET = new Identifier(AgesOfSiegeMod.MOD_ID, "army_work_bench_eject");

	private ArmyWorkBenchService() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(SPEND_PACKET, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			WorkbenchStat stat = WorkbenchStat.from(buf.readString(32));
			server.execute(() -> {
				ArmyWorkBenchBlockEntity bench = resolveBench(player.getWorld(), pos);
				if (bench == null || stat == null) {
					return;
				}
				bench.spendPoint(stat);
				open(player, bench.getPos(), bench);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(EQUIP_PACKET, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			WorkbenchArmorTier tier = WorkbenchArmorTier.from(buf.readString(32));
			server.execute(() -> {
				ArmyWorkBenchBlockEntity bench = resolveBench(player.getWorld(), pos);
				if (bench == null) {
					return;
				}
				bench.equipArmor(tier);
				open(player, bench.getPos(), bench);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(EJECT_PACKET, (server, player, handler, buf, responseSender) -> {
			BlockPos pos = buf.readBlockPos();
			server.execute(() -> {
				ArmyWorkBenchBlockEntity bench = resolveBench(player.getWorld(), pos);
				if (bench == null) {
					return;
				}
				ItemStack stack = bench.ejectToken();
				if (!stack.isEmpty() && !player.getInventory().insertStack(stack)) {
					player.dropItem(stack, false);
				}
				open(player, pos, bench);
			});
		});
	}

	public static void open(ServerPlayerEntity player, BlockPos pos, ArmyWorkBenchBlockEntity bench) {
		PacketByteBuf buf = PacketByteBufs.create();
		buildSnapshot(pos, bench).write(buf);
		ServerPlayNetworking.send(player, OPEN_PACKET, buf);
	}

	private static ArmyWorkBenchSnapshot buildSnapshot(BlockPos pos, ArmyWorkBenchBlockEntity bench) {
		ItemStack tokenStack = bench == null ? ItemStack.EMPTY : bench.getToken();
		if (tokenStack.isEmpty()) {
			return new ArmyWorkBenchSnapshot(
				pos,
				false,
				ItemStack.EMPTY,
				"No Soldier Token Inserted",
				1,
				0,
				25,
				0,
				18.0F,
				18.0F,
				3.0D,
				2.0D,
				0.25D,
				0,
				0,
				0,
				0,
				WorkbenchArmorTier.LEATHER.id(),
				WorkbenchArmorTier.LEATHER.displayName(),
				true,
				false,
				false,
				"Insert a Soldier Token to tune armor and spend level points."
			);
		}

		NbtCompound data = DefenderTokenData.ensureWorkbenchData(tokenStack, DefenderRole.SOLDIER);
		double bonusHealth = data.getCompound("stats").getDouble("bonusHealth");
		double bonusAttack = data.getCompound("stats").getDouble("bonusAttack");
		double bonusArmor = data.getCompound("stats").getDouble("bonusArmor");
		double bonusSpeed = data.getCompound("stats").getDouble("bonusSpeed");
		int level = DefenderTokenData.level(data, DefenderRole.SOLDIER);
		return new ArmyWorkBenchSnapshot(
			pos,
			true,
			tokenStack,
			DefenderTokenData.displayName(data, DefenderRole.SOLDIER),
			level,
			DefenderTokenData.xp(data, DefenderRole.SOLDIER),
			DefenderTokenData.xpToNextLevel(data, DefenderRole.SOLDIER),
			DefenderTokenData.availablePoints(data, DefenderRole.SOLDIER),
			(float) (18.0D + bonusHealth),
			(float) (18.0D + bonusHealth),
			3.0D + bonusAttack,
			bonusArmor,
			0.25D + bonusSpeed,
			DefenderTokenData.statValue(data, DefenderRole.SOLDIER, WorkbenchStat.VITALITY),
			DefenderTokenData.statValue(data, DefenderRole.SOLDIER, WorkbenchStat.STRENGTH),
			DefenderTokenData.statValue(data, DefenderRole.SOLDIER, WorkbenchStat.DISCIPLINE),
			DefenderTokenData.statValue(data, DefenderRole.SOLDIER, WorkbenchStat.AGILITY),
			DefenderTokenData.equippedArmorTier(data, DefenderRole.SOLDIER).id(),
			DefenderTokenData.armorLabel(data, DefenderRole.SOLDIER),
			true,
			WorkbenchArmorTier.CHAINMAIL.isUnlockedAt(level),
			WorkbenchArmorTier.IRON.isUnlockedAt(level),
			"Leather unlocks at 1, chainmail at 3, and iron at 5."
		);
	}

	private static ArmyWorkBenchBlockEntity resolveBench(World world, BlockPos pos) {
		if (world == null || pos == null) {
			return null;
		}
		if (!(world.getBlockState(pos).getBlock() instanceof ArmyWorkBenchBlock)) {
			return null;
		}
		BlockPos mainPos = ArmyWorkBenchBlock.resolveMainPos(pos, world.getBlockState(pos));
		if (!(world.getBlockEntity(mainPos) instanceof ArmyWorkBenchBlockEntity bench)) {
			return null;
		}
		return bench;
	}

	public static void rejectArcherToken(ServerPlayerEntity player) {
		player.sendMessage(Text.literal("Archers are not supported by the Army Work Bench yet.").formatted(Formatting.RED), true);
	}
}
