package com.stamperl.agesofsiege.block;

import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.workbench.ArmyWorkBenchService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class ArmyWorkBenchBlock extends BlockWithEntity implements BlockEntityProvider {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<ArmyWorkBenchPart> PART = EnumProperty.of("part", ArmyWorkBenchPart.class);

	public ArmyWorkBenchBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState()
			.with(FACING, Direction.NORTH)
			.with(PART, ArmyWorkBenchPart.MAIN));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
		BlockPos pos = ctx.getBlockPos();
		BlockPos extensionPos = pos.offset(extensionOffset(facing));
		World world = ctx.getWorld();
		if (!world.getBlockState(extensionPos).canReplace(ctx)) {
			return null;
		}
		return this.getDefaultState()
			.with(FACING, facing)
			.with(PART, ArmyWorkBenchPart.MAIN);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, ItemStack itemStack) {
		if (world.isClient) {
			return;
		}
		BlockPos extensionPos = pos.offset(extensionOffset(state.get(FACING)));
		world.setBlockState(extensionPos, state.with(PART, ArmyWorkBenchPart.EXTENSION), Block.NOTIFY_ALL);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		BlockPos mainPos = resolveMainPos(pos, state);
		BlockEntity blockEntity = world.getBlockEntity(mainPos);
		if (!(blockEntity instanceof ArmyWorkBenchBlockEntity bench) || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.CONSUME;
		}
		ItemStack held = player.getStackInHand(hand);
		if (held.isOf(ModItems.ARCHER_TOKEN)) {
			com.stamperl.agesofsiege.workbench.ArmyWorkBenchService.rejectArcherToken(serverPlayer);
			ArmyWorkBenchService.open(serverPlayer, mainPos, bench);
			return ActionResult.CONSUME;
		}
		if (held.isOf(ModItems.SOLDIER_TOKEN) && !bench.hasToken()) {
			bench.tryInsert(held, serverPlayer);
		}
		ArmyWorkBenchService.open(serverPlayer, mainPos, bench);
		return ActionResult.CONSUME;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(PART) == ArmyWorkBenchPart.MAIN ? new ArmyWorkBenchBlockEntity(pos, state) : null;
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (state.isOf(newState.getBlock())) {
			super.onStateReplaced(state, world, pos, newState, moved);
			return;
		}

		BlockPos mainPos = resolveMainPos(pos, state);
		BlockPos extensionPos = mainPos.offset(extensionOffset(state.get(FACING)));
		if (world.getBlockEntity(mainPos) instanceof ArmyWorkBenchBlockEntity bench) {
			bench.dropContents();
		}
		if (world.getBlockState(mainPos).isOf(this)) {
			world.removeBlock(mainPos, false);
		}
		if (world.getBlockState(extensionPos).isOf(this)) {
			world.removeBlock(extensionPos, false);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	public BlockState getStateForNeighborUpdate(
		BlockState state,
		Direction direction,
		BlockState neighborState,
		net.minecraft.world.WorldAccess world,
		BlockPos pos,
		BlockPos neighborPos
	) {
		BlockPos expectedOther = state.get(PART) == ArmyWorkBenchPart.MAIN
			? pos.offset(extensionOffset(state.get(FACING)))
			: pos.offset(extensionOffset(state.get(FACING)).getOpposite());
		if (neighborPos.equals(expectedOther) && !neighborState.isOf(this)) {
			return net.minecraft.block.Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	public static BlockPos resolveMainPos(BlockPos pos, BlockState state) {
		if (state.get(PART) == ArmyWorkBenchPart.MAIN) {
			return pos;
		}
		return pos.offset(extensionOffset(state.get(FACING)).getOpposite());
	}

	private static Direction extensionOffset(Direction facing) {
		return facing.rotateYClockwise();
	}
}
