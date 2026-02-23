package com.siickzz.ktsacademy.screen;

import com.siickzz.ktsacademy.KTSAcademy;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
	public static ScreenHandlerType<ShopScreenHandler>    SHOP;
	public static ScreenHandlerType<ConfirmScreenHandler> CONFIRM;

	private ModScreenHandlers() {}

	public static void register()
	{
		if (SHOP != null)
			return;

		SHOP = Registry.register(
			Registries.SCREEN_HANDLER,
			Identifier.of(KTSAcademy.MOD_ID, "shop"),
			new ScreenHandlerType<>(ShopScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
		);
		CONFIRM = Registry.register(
			Registries.SCREEN_HANDLER,
			Identifier.of(KTSAcademy.MOD_ID, "shop_confirm"),
			new ScreenHandlerType<>(ConfirmScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
		);
	}
}
