package com.stamperl.agesofsiege;

import com.stamperl.agesofsiege.command.ModCommands;
import com.stamperl.agesofsiege.defense.DefenderRuntimeService;
import com.stamperl.agesofsiege.entity.ModEntities;
import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.ledger.ArmyLedgerService;
import com.stamperl.agesofsiege.siege.BattleFormationCatalog;
import com.stamperl.agesofsiege.siege.BattleUnitCatalog;
import com.stamperl.agesofsiege.siege.SiegeDirector;
import com.stamperl.agesofsiege.siege.SiegeCatalog;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgesOfSiegeMod implements ModInitializer {
	public static final String MOD_ID = "ages_of_siege";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();
		BattleUnitCatalog.initialize();
		BattleFormationCatalog.initialize();
		SiegeCatalog.initialize();
		ArmyLedgerService.registerServer();
		ModCommands.register();
		DefenderRuntimeService.register();
		SiegeDirector.register();
		LOGGER.info("Ages Of Siege control layer loaded.");
	}
}
