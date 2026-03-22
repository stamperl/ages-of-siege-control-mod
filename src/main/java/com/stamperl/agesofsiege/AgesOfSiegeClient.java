package com.stamperl.agesofsiege;

import com.stamperl.agesofsiege.entity.ModEntities;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.RavagerEntityRenderer;

public class AgesOfSiegeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SIEGE_RAM, AgesOfSiegeClient::createRamRenderer);
	}

	private static RavagerEntityRenderer createRamRenderer(EntityRendererFactory.Context context) {
		return new RavagerEntityRenderer(context);
	}
}
