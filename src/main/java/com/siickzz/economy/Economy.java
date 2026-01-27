package com.siickzz.economy;

import com.siickzz.economy.commands.EconomyAdminCommand;
import com.siickzz.economy.commands.MoneyTopCommand;
import com.siickzz.economy.commands.PokeBoardCommand;
import com.siickzz.economy.commands.PokedexCommand;
import com.siickzz.economy.commands.QuestCommand;
import com.siickzz.economy.commands.ShopCommand;
import com.siickzz.economy.commands.TrashCommand;
import com.siickzz.economy.economy.EconomyManager;
import com.siickzz.economy.events.PokemonBattleWinListener;
import com.siickzz.economy.events.PokemonCaptureListener;
    import com.siickzz.economy.events.PokemonFishingListener;
import com.siickzz.economy.events.PokemonTradeListener;
import com.siickzz.economy.events.HarvestListener;
import com.siickzz.economy.events.PokedexMilestoneListener;
import com.siickzz.economy.events.OreMineListener;
import com.siickzz.economy.quests.QuestManager;
import com.siickzz.economy.shop.ShopRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Economy implements ModInitializer {
    public static final String MOD_ID = "economy";

    @Override
    public void onInitialize() {
        EconomyManager.init();
        ShopRegistry.init();
		QuestManager.init();
        PokemonBattleWinListener.register();
        PokemonCaptureListener.register();
		PokemonFishingListener.register();
		PokemonTradeListener.register();
        HarvestListener.register();
        PokedexMilestoneListener.register();
		OreMineListener.register();
        ShopCommand.register();
        EconomyAdminCommand.register();
		MoneyTopCommand.register();
        PokeBoardCommand.register();
        PokedexCommand.register();
		QuestCommand.register();
        TrashCommand.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EconomyManager.save());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QuestManager.save());
    }
}
