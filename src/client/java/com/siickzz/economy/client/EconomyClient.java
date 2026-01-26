package com.siickzz.economy.client;

import com.siickzz.economy.client.screen.ConfirmScreen;
import com.siickzz.economy.client.screen.ShopScreen;
import com.siickzz.economy.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class EconomyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.SHOP, ShopScreen::new);
		HandledScreens.register(ModScreenHandlers.CONFIRM, ConfirmScreen::new);
	}
}
