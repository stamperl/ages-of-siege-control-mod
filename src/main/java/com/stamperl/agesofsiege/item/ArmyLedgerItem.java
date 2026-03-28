package com.stamperl.agesofsiege.item;

import com.stamperl.agesofsiege.ledger.ArmyLedgerService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class ArmyLedgerItem extends Item {
	public ArmyLedgerItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity player) {
			ArmyLedgerService.openLedger(player);
		}
		return TypedActionResult.success(stack, world.isClient());
	}
}
