package com.stamperl.agesofsiege;

import com.stamperl.agesofsiege.entity.ModEntities;
import com.stamperl.agesofsiege.entity.SiegeRamEntity;
import com.stamperl.agesofsiege.ledger.ArmyLedgerScreen;
import com.stamperl.agesofsiege.ledger.ArmyLedgerService;
import com.stamperl.agesofsiege.ledger.ArmyLedgerSnapshot;
import com.stamperl.agesofsiege.report.SiegeWarReportScreen;
import com.stamperl.agesofsiege.report.SiegeWarReportService;
import com.stamperl.agesofsiege.report.SiegeWarReportSnapshot;
import com.stamperl.agesofsiege.workbench.ArmyWorkBenchScreen;
import com.stamperl.agesofsiege.workbench.ArmyWorkBenchService;
import com.stamperl.agesofsiege.workbench.ArmyWorkBenchSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.RavagerEntityRenderer;

public class AgesOfSiegeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SIEGE_RAM, AgesOfSiegeClient::createRamRenderer);
		ClientPlayNetworking.registerGlobalReceiver(ArmyLedgerService.OPEN_PACKET, (client, handler, buf, responseSender) -> {
			ArmyLedgerSnapshot snapshot = ArmyLedgerSnapshot.read(buf);
			client.execute(() -> client.setScreen(new ArmyLedgerScreen(snapshot)));
		});
		ClientPlayNetworking.registerGlobalReceiver(ArmyWorkBenchService.OPEN_PACKET, (client, handler, buf, responseSender) -> {
			ArmyWorkBenchSnapshot snapshot = ArmyWorkBenchSnapshot.read(buf);
			client.execute(() -> client.setScreen(new ArmyWorkBenchScreen(snapshot)));
		});
		ClientPlayNetworking.registerGlobalReceiver(SiegeWarReportService.OPEN_PACKET, (client, handler, buf, responseSender) -> {
			SiegeWarReportSnapshot snapshot = SiegeWarReportSnapshot.read(buf);
			client.execute(() -> client.setScreen(new SiegeWarReportScreen(snapshot)));
		});
	}

	private static RavagerEntityRenderer createRamRenderer(EntityRendererFactory.Context context) {
		return new RavagerEntityRenderer(context);
	}
}
