package com.stamperl.agesofsiege;

import com.stamperl.agesofsiege.command.ModCommands;
import com.stamperl.agesofsiege.item.ModItems;
import com.stamperl.agesofsiege.siege.SiegeManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgesOfSiegeMod implements ModInitializer {
	public static final String MOD_ID = "ages_of_siege";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.register();
		ModCommands.register();
		SiegeManager.register();
		LOGGER.info("Ages Of Siege control layer loaded.");
	}
}
