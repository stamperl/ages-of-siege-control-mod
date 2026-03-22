package com.stamperl.agesofsiege.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.World;

public class SiegeRamEntity extends RavagerEntity {
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

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	protected boolean isDisallowedInPeaceful() {
		return false;
	}
}
