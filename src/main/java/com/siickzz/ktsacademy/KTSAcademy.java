package com.siickzz.ktsacademy;

import com.siickzz.ktsacademy.commands.EconomyAdminCommand;
import com.siickzz.ktsacademy.commands.MoneyTopCommand;
import com.siickzz.ktsacademy.commands.PokeBoardCommand;
import com.siickzz.ktsacademy.commands.PokedexCommand;
import com.siickzz.ktsacademy.commands.QuestCommand;
import com.siickzz.ktsacademy.commands.ShopCommand;
import com.siickzz.ktsacademy.commands.TrashCommand;
import com.siickzz.ktsacademy.economy.EconomyManager;
import com.siickzz.ktsacademy.events.PokemonBattleWinListener;
import com.siickzz.ktsacademy.events.PokemonCaptureListener;
    import com.siickzz.ktsacademy.events.PokemonFishingListener;
import com.siickzz.ktsacademy.events.PokemonTradeListener;
import com.siickzz.ktsacademy.events.HarvestListener;
import com.siickzz.ktsacademy.events.PokedexMilestoneListener;
import com.siickzz.ktsacademy.events.OreMineListener;
import com.siickzz.ktsacademy.quests.QuestManager;
import com.siickzz.ktsacademy.shop.ShopRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class KTSAcademy implements ModInitializer {
    public static final String MOD_ID = "KTSAcademy";

    @Override
    public void onInitialize() {
//        PokemonBattleWinListener.register();
//        PokemonCaptureListener.register();
//		PokemonFishingListener.register();
//		PokemonTradeListener.register();
//        HarvestListener.register();
//        PokedexMilestoneListener.register();
//		OreMineListener.register();
//        ShopCommand.register();
//        EconomyAdminCommand.register();
//		MoneyTopCommand.register();
        PokeBoardCommand.register();
        PokedexCommand.register();
//		QuestCommand.register();
        TrashCommand.register();

//        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EconomyManager.save());
//		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QuestManager.save());
    }
}
