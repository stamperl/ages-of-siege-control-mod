package com.stamperl.agesofsiege.item;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationPropertyHelper;

public class SettlementStandardItem extends Item {
	public SettlementStandardItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(context.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}

		BlockPos clickedPos = context.getBlockPos();
		Direction side = context.getSide();
		BlockPos targetPos = world.getBlockState(clickedPos).canReplace(context) ? clickedPos : clickedPos.offset(side);
		if (side == Direction.DOWN || !world.getBlockState(targetPos).isReplaceable()) {
			return ActionResult.FAIL;
		}

		BlockState bannerState;
		if (side == Direction.UP) {
			bannerState = Blocks.WHITE_BANNER.getDefaultState()
				.with(net.minecraft.block.BannerBlock.ROTATION, RotationPropertyHelper.fromYaw(player.getYaw()));
		} else {
			bannerState = Blocks.WHITE_WALL_BANNER.getDefaultState()
				.with(HorizontalFacingBlock.FACING, side);
		}

		if (!bannerState.canPlaceAt(world, targetPos)) {
			return ActionResult.FAIL;
		}

		if (!world.setBlockState(targetPos, bannerState)) {
			return ActionResult.FAIL;
		}

		SiegeBaseState siegeState = SiegeBaseState.get(world.getServer());
		siegeState.setBase(targetPos, world.getRegistryKey().getValue().toString(), player.getGameProfile().getName());

		world.playSound(null, targetPos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		context.getStack().decrement(1);
		player.sendMessage(Text.literal("Settlement banner placed. Defend it when the siege comes.")
			.formatted(Formatting.GREEN), true);
		return ActionResult.SUCCESS;
	}
}
