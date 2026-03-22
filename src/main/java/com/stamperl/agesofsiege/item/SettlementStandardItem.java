package com.stamperl.agesofsiege.item;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public class SettlementStandardItem extends Item {
	public SettlementStandardItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
			return ActionResult.SUCCESS;
		}

		SiegeBaseState state = SiegeBaseState.get(player.getServer());
		state.setBase(
			context.getBlockPos(),
			context.getWorld().getRegistryKey().getValue().toString(),
			player.getGameProfile().getName()
		);

		player.sendMessage(Text.literal("Settlement claimed. This will become the first siege target later.")
			.formatted(Formatting.GREEN), true);
		return ActionResult.SUCCESS;
	}
}
