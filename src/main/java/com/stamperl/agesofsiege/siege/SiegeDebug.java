package com.stamperl.agesofsiege.siege;

import com.stamperl.agesofsiege.siege.runtime.SiegePlan;
import com.stamperl.agesofsiege.siege.runtime.SiegeSession;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class SiegeDebug {
	private static boolean pathRenderEnabled = false;

	private SiegeDebug() {
	}

	public static boolean isPathRenderEnabled() {
		return pathRenderEnabled;
	}

	public static void setPathRenderEnabled(boolean enabled) {
		pathRenderEnabled = enabled;
	}

	public static void renderPlan(ServerWorld world, SiegeSession session) {
		if (!pathRenderEnabled || session == null) {
			return;
		}

		SiegePlan plan = session.getCurrentPlan();
		if (plan == null) {
			return;
		}

		BlockPos rally = session.getRallyPos();
		BlockPos objective = session.getObjectivePos();
		if (rally != null && plan.stagingPoint() != null) {
			drawLine(world, Vec3d.ofCenter(rally), Vec3d.ofCenter(plan.stagingPoint()), ParticleTypes.END_ROD);
		}
		if (plan.stagingPoint() != null && plan.primaryBreachAnchor() != null) {
			drawLine(world, Vec3d.ofCenter(plan.stagingPoint()), Vec3d.ofCenter(plan.primaryBreachAnchor()), ParticleTypes.WAX_ON);
		}
		if (plan.breachExit() != null && objective != null) {
			drawLine(world, Vec3d.ofCenter(plan.breachExit()), Vec3d.ofCenter(objective), ParticleTypes.HAPPY_VILLAGER);
		}
		if (rally != null) {
			drawMarker(world, rally, new DustParticleEffect(new Vector3f(0.2F, 0.6F, 1.0F), 1.1F));
		}
		if (plan.stagingPoint() != null) {
			drawMarker(world, plan.stagingPoint(), new DustParticleEffect(new Vector3f(1.0F, 0.9F, 0.2F), 1.1F));
		}
		if (plan.primaryBreachAnchor() != null) {
			drawMarker(world, plan.primaryBreachAnchor(), new DustParticleEffect(new Vector3f(1.0F, 0.2F, 0.2F), 1.2F));
		}
		if (plan.breachExit() != null) {
			drawMarker(world, plan.breachExit(), new DustParticleEffect(new Vector3f(0.2F, 1.0F, 0.3F), 1.0F));
		}
		if (objective != null) {
			drawMarker(world, objective, new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 1.2F));
		}
		for (BlockPos target : plan.targetBlocks()) {
			world.spawnParticles(ParticleTypes.FLAME, target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 1, 0.05D, 0.05D, 0.05D, 0.0D);
		}
	}

	private static void drawLine(ServerWorld world, Vec3d from, Vec3d to, net.minecraft.particle.ParticleEffect particle) {
		Vec3d delta = to.subtract(from);
		double length = Math.max(0.25D, delta.length());
		int steps = Math.max(2, (int) Math.ceil(length * 2.0D));
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3d point = from.lerp(to, t);
			world.spawnParticles(particle, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private static void drawMarker(ServerWorld world, BlockPos pos, DustParticleEffect particle) {
		double x = pos.getX() + 0.5D;
		double y = pos.getY() + 1.0D;
		double z = pos.getZ() + 0.5D;
		world.spawnParticles(particle, x, y, z, 6, 0.18D, 0.35D, 0.18D, 0.0D);
	}
}
