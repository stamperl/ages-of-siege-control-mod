package com.stamperl.agesofsiege.item;

import net.minecraft.block.BannerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;

abstract class AbstractSiegeBannerItem extends Item {
	private final Block standingBanner;
	private final Block wallBanner;

	protected AbstractSiegeBannerItem(Settings settings, Block standingBanner, Block wallBanner) {
		super(settings);
		this.standingBanner = standingBanner;
		this.wallBanner = wallBanner;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!(context.getPlayer() instanceof ServerPlayerEntity player) || !(context.getWorld() instanceof ServerWorld world)) {
			return ActionResult.SUCCESS;
		}

		BlockPos clickedPos = context.getBlockPos();
		Direction side = context.getSide();
		BlockPos targetPos = world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side);
		if (side == Direction.DOWN || !world.getBlockState(targetPos).isReplaceable()) {
			return ActionResult.FAIL;
		}

		BlockState bannerState = side == Direction.UP
			? standingBanner.getDefaultState().with(BannerBlock.ROTATION, RotationPropertyHelper.fromYaw(player.getYaw()))
			: wallBanner.getDefaultState().with(HorizontalFacingBlock.FACING, side);

		if (!bannerState.canPlaceAt(world, targetPos) || !world.setBlockState(targetPos, bannerState)) {
			return ActionResult.FAIL;
		}

		afterPlaced(world, player, targetPos);
		world.playSound(null, targetPos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		context.getStack().decrement(1);
		player.sendMessage(Text.literal(getPlacedMessage()).formatted(Formatting.GREEN), true);
		return ActionResult.SUCCESS;
	}

	protected abstract void afterPlaced(ServerWorld world, ServerPlayerEntity player, BlockPos targetPos);

	protected abstract String getPlacedMessage();
}
