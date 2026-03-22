package com.stamperl.agesofsiege.block;

import com.stamperl.agesofsiege.state.SiegeBaseState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SettlementStandardBlock extends Block {
	public SettlementStandardBlock(Settings settings) {
		super(settings);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);

		if (!(world instanceof ServerWorld serverWorld) || !(placer instanceof ServerPlayerEntity player)) {
			return;
		}

		SiegeBaseState siegeState = SiegeBaseState.get(serverWorld.getServer());
		siegeState.setBase(pos, world.getRegistryKey().getValue().toString(), player.getGameProfile().getName());
		player.sendMessage(Text.literal("Settlement standard placed. This structure is now your siege objective.")
			.formatted(Formatting.GREEN), true);
	}

	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld serverWorld) {
			SiegeBaseState siegeState = SiegeBaseState.get(serverWorld.getServer());
			siegeState.handleObjectiveDestroyed(serverWorld, pos);
		}

		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
