package com.stamperl.agesofsiege;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class AntiqueAtlasCompat {
	private static final String ATLAS_SCREEN_CLASS = "folk.sisby.antique_atlas.gui.AtlasScreen";
	private static Class<?> atlasScreenClass;
	private static Constructor<?> atlasConstructor;
	private static Method setTargetPositionMethod;
	private static Method prepareToOpenMethod;
	private static boolean lookupAttempted;

	private AntiqueAtlasCompat() {
	}

	public static boolean isAvailable() {
		lookup();
		return atlasConstructor != null;
	}

	public static boolean open(BlockPos targetPos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return false;
		}
		lookup();
		if (atlasConstructor == null) {
			notify(client, "Antique Atlas is not available in this instance.");
			return false;
		}
		try {
			Object atlas = atlasConstructor.newInstance();
			if (targetPos != null && setTargetPositionMethod != null) {
				setTargetPositionMethod.invoke(atlas, targetPos);
			}
			if (prepareToOpenMethod != null) {
				prepareToOpenMethod.invoke(atlas);
			}
			if (atlas instanceof Screen screen) {
				client.setScreen(screen);
				return true;
			}
		} catch (ReflectiveOperationException exception) {
			AgesOfSiegeMod.LOGGER.warn("Failed to open Antique Atlas", exception);
		}
		notify(client, "Could not open Antique Atlas.");
		return false;
	}

	private static void lookup() {
		if (lookupAttempted) {
			return;
		}
		lookupAttempted = true;
		try {
			atlasScreenClass = Class.forName(ATLAS_SCREEN_CLASS);
			atlasConstructor = atlasScreenClass.getConstructor();
			setTargetPositionMethod = resolveTargetPositionMethod(atlasScreenClass);
			prepareToOpenMethod = atlasScreenClass.getMethod("prepareToOpen");
		} catch (ReflectiveOperationException exception) {
			AgesOfSiegeMod.LOGGER.info("Antique Atlas compat not active: {}", exception.getMessage());
			atlasScreenClass = null;
			atlasConstructor = null;
			setTargetPositionMethod = null;
			prepareToOpenMethod = null;
		}
	}

	private static Method resolveTargetPositionMethod(Class<?> atlasClass) {
		try {
			return atlasClass.getMethod("setTargetPosition", BlockPos.class);
		} catch (NoSuchMethodException ignored) {
		}
		for (Method method : atlasClass.getMethods()) {
			if (!method.getName().equals("setTargetPosition") || method.getParameterCount() != 1) {
				continue;
			}
			Class<?> parameterType = method.getParameterTypes()[0];
			if (parameterType.isAssignableFrom(BlockPos.class) || parameterType.isInstance(BlockPos.ORIGIN)) {
				return method;
			}
		}
		AgesOfSiegeMod.LOGGER.info("Antique Atlas compat: opening without target-position support.");
		return null;
	}

	private static void notify(MinecraftClient client, String message) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(message), false);
		}
	}
}
