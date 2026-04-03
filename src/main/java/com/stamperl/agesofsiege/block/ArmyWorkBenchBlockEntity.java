package com.stamperl.agesofsiege.block;

import com.stamperl.agesofsiege.defense.DefenderTokenData;
import com.stamperl.agesofsiege.defense.WorkbenchArmorTier;
import com.stamperl.agesofsiege.defense.WorkbenchStat;
import com.stamperl.agesofsiege.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;

public final class ArmyWorkBenchBlockEntity extends BlockEntity {
	private ItemStack soldierToken = ItemStack.EMPTY;

	public ArmyWorkBenchBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ARMY_WORK_BENCH_BLOCK_ENTITY, pos, state);
	}

	public boolean hasToken() {
		return !soldierToken.isEmpty();
	}

	public ItemStack getToken() {
		return soldierToken.copy();
	}

	public boolean tryInsert(ItemStack stack, ServerPlayerEntity player) {
		if (stack.isEmpty()) {
			return false;
		}
		if (!stack.isOf(ModItems.SOLDIER_TOKEN)) {
			player.sendMessage(Text.literal("The Army Work Bench only accepts Soldier Tokens.").formatted(Formatting.RED), true);
			return false;
		}
		if (!DefenderTokenData.canUseWorkbench(stack)) {
			player.sendMessage(Text.literal("That soldier token must be repaired before it can be worked at the bench.").formatted(Formatting.RED), true);
			return false;
		}
		if (hasToken()) {
			player.sendMessage(Text.literal("The Army Work Bench already holds a soldier token.").formatted(Formatting.RED), true);
			return false;
		}
		ItemStack inserted = stack.copyWithCount(1);
		DefenderTokenData.ensureWorkbenchData(inserted, com.stamperl.agesofsiege.defense.DefenderRole.SOLDIER);
		this.soldierToken = inserted;
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		markDirty();
		return true;
	}

	public ItemStack ejectToken() {
		if (soldierToken.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack extracted = soldierToken.copy();
		soldierToken = ItemStack.EMPTY;
		markDirty();
		return extracted;
	}

	public void spendPoint(WorkbenchStat stat) {
		if (soldierToken.isEmpty()) {
			return;
		}
		NbtCompound updated = DefenderTokenData.spendPoint(
			DefenderTokenData.getData(soldierToken),
			com.stamperl.agesofsiege.defense.DefenderRole.SOLDIER,
			stat
		);
		DefenderTokenData.writeToStack(soldierToken, updated);
		markDirty();
	}

	public void equipArmor(WorkbenchArmorTier tier) {
		if (soldierToken.isEmpty()) {
			return;
		}
		NbtCompound updated = DefenderTokenData.equipArmorTier(
			DefenderTokenData.getData(soldierToken),
			com.stamperl.agesofsiege.defense.DefenderRole.SOLDIER,
			tier
		);
		DefenderTokenData.writeToStack(soldierToken, updated);
		markDirty();
	}

	public void dropContents() {
		if (world == null || soldierToken.isEmpty()) {
			return;
		}
		ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), soldierToken);
		soldierToken = ItemStack.EMPTY;
		markDirty();
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		soldierToken = nbt.contains("soldierToken") ? ItemStack.fromNbt(nbt.getCompound("soldierToken")) : ItemStack.EMPTY;
		if (!soldierToken.isEmpty()) {
			DefenderTokenData.ensureWorkbenchData(soldierToken, com.stamperl.agesofsiege.defense.DefenderRole.SOLDIER);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (!soldierToken.isEmpty()) {
			nbt.put("soldierToken", soldierToken.writeNbt(new NbtCompound()));
		}
	}

	@Override
	public void markDirty() {
		super.markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}
}
