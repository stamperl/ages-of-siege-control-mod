package com.stamperl.agesofsiege.defense;

import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

public class DefenderRecallToolItem extends Item {
	public DefenderRecallToolItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(user instanceof ServerPlayerEntity player)) {
			return ActionResult.SUCCESS;
		}

		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		var placedDefender = state.getPlacedDefender(entity.getUuid());
		if (placedDefender == null) {
			player.sendMessage(Text.literal("That defender is not bound to a token post.").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}

		ItemStack tokenStack = switch (placedDefender.role()) {
			case ARCHER -> new ItemStack(ModItems.ARCHER_TOKEN);
			case SOLDIER -> new ItemStack(ModItems.SOLDIER_TOKEN);
		};

		state.removePlacedDefender(entity.getUuid());
		entity.discard();
		if (!player.getInventory().insertStack(tokenStack)) {
			player.dropItem(tokenStack, false);
		}

		player.sendMessage(
			Text.literal(placedDefender.role().displayName() + " recovered and token returned.")
				.formatted(Formatting.GREEN),
			true
		);
		return ActionResult.SUCCESS;
	}
}
