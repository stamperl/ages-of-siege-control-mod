package com.stamperl.agesofsiege.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SiegeRamEntity extends RavagerEntity {
	private BlockPos breachTarget;

	public SiegeRamEntity(EntityType<? extends RavagerEntity> entityType, World world) {
		super(entityType, world);
		this.setPathfindingPenalty(PathNodeType.DAMAGE_OTHER, -1.0F);
		this.setPathfindingPenalty(PathNodeType.DANGER_OTHER, -1.0F);
		this.ignoreCameraFrustum = true;
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0D)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0D)
			.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0D);
	}

	@Override
	protected void initGoals() {
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		this.setTarget(null);
		this.getNavigation().stop();
		this.setVelocity(0.0D, 0.0D, 0.0D);
	}

	public BlockPos getBreachTarget() {
		return breachTarget;
	}

	public void setBreachTarget(BlockPos breachTarget) {
		this.breachTarget = breachTarget != null ? breachTarget.toImmutable() : null;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (breachTarget != null) {
			nbt.putInt("BreachTargetX", breachTarget.getX());
			nbt.putInt("BreachTargetY", breachTarget.getY());
			nbt.putInt("BreachTargetZ", breachTarget.getZ());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("BreachTargetX") && nbt.contains("BreachTargetY") && nbt.contains("BreachTargetZ")) {
			breachTarget = new BlockPos(nbt.getInt("BreachTargetX"), nbt.getInt("BreachTargetY"), nbt.getInt("BreachTargetZ"));
		} else {
			breachTarget = null;
		}
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}
}
