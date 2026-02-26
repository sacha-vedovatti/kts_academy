package com.siickzz.ktsacademy;

import com.siickzz.ktsacademy.commands.MsgCommand;
import com.siickzz.ktsacademy.commands.PokeBoardCommand;
import com.siickzz.ktsacademy.commands.PokedexCommand;
import com.siickzz.ktsacademy.commands.QuestCommand;
import com.siickzz.ktsacademy.commands.ReplyCommand;
import com.siickzz.ktsacademy.commands.TrashCommand;
import com.siickzz.ktsacademy.events.PokemonBattleWinListener;
import com.siickzz.ktsacademy.events.PokemonCaptureListener;
import com.siickzz.ktsacademy.events.PokemonFishingListener;
import com.siickzz.ktsacademy.events.PokemonTradeListener;
import com.siickzz.ktsacademy.events.HarvestListener;
import com.siickzz.ktsacademy.events.PokedexMilestoneListener;
import com.siickzz.ktsacademy.events.OreMineListener;
import com.siickzz.ktsacademy.motd.MotdListener;
import com.siickzz.ktsacademy.messages.PrivateMsgManager;
import com.siickzz.ktsacademy.quests.QuestManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class KTSAcademy implements ModInitializer {
    public static final String MOD_ID = "KTSAcademy";

    @Override
    public void onInitialize()
    {
        MotdListener.register();
        PokemonBattleWinListener.register();
        PokemonCaptureListener.register();
        PokemonFishingListener.register();
        PokemonTradeListener.register();
        HarvestListener.register();
        PokedexMilestoneListener.register();
        OreMineListener.register();
        PokeBoardCommand.register();
        PokedexCommand.register();
        QuestCommand.register();
        TrashCommand.register();
        MsgCommand.register();
        ReplyCommand.register();

        /* Shop commands disabled. */
//      ShopCommand.register();
//      EconomyAdminCommand.register();
//		MoneyTopCommand.register();
//      ServerLifecycleEvents.SERVER_STOPPING.register(server -> EconomyManager.save());

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PrivateMsgManager.remove(handler.getPlayer().getUuid())
        );
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> QuestManager.save());
    }
}
