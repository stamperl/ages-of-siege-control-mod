package com.stamperl.agesofsiege.block;

import com.stamperl.agesofsiege.defense.DefenderTokenData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

public final class RepairChestBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 4;
	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(5, ItemStack.EMPTY);

	public RepairChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.REPAIR_CHEST_BLOCK_ENTITY, pos, state);
	}

	@Override
	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStack(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack stack = Inventories.splitStack(items, slot, amount);
		if (!stack.isEmpty()) {
			markDirty();
		}
		return stack;
	}

	@Override
	public ItemStack removeStack(int slot) {
		ItemStack stack = Inventories.removeStack(items, slot);
		if (!stack.isEmpty()) {
			markDirty();
		}
		return stack;
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxCountPerStack()) {
			stack.setCount(getMaxCountPerStack());
		}
		markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		items.clear();
		markDirty();
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.ages_of_siege.repair_chest");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new HopperScreenHandler(syncId, playerInventory, this);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		Inventories.readNbt(nbt, items);
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		Inventories.writeNbt(nbt, items);
	}

	@Override
	public void markDirty() {
		processRepair();
		super.markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	private void processRepair() {
		ItemStack input = items.get(INPUT_SLOT);
		ItemStack output = items.get(OUTPUT_SLOT);
		if (input.isEmpty() || !DefenderTokenData.requiresRepair(input) || !output.isEmpty()) {
			return;
		}
		items.set(OUTPUT_SLOT, DefenderTokenData.repairToken(input));
		items.set(INPUT_SLOT, ItemStack.EMPTY);
	}
}
