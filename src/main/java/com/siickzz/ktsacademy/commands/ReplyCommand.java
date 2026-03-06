package com.siickzz.ktsacademy.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.siickzz.ktsacademy.messages.PrivateMsgManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ReplyCommand {
    private ReplyCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /reply est la commande principale (pas de conflit attendu)
            dispatcher.register(literal("reply")
                    .then(argument("message", StringArgumentType.greedyString())
                            .executes(ReplyCommand::reply)));
            // /r peut être pris par EssentialCommands — on essaie quand même
            try {
                dispatcher.register(literal("r")
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(ReplyCommand::reply)));
            } catch (Exception ignored) {
                // /r est déjà enregistré par un autre mod
            }
        });
    }

    public static int reply(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
        String message = StringArgumentType.getString(ctx, "message");
        UUID targetUuid = PrivateMsgManager.getLastConversation(sender.getUuid());

        if (targetUuid == null) {
            sender.sendMessage(Text.literal("§cAucune conversation récente à laquelle répondre."), false);
            return 0;
        }

        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null) {
            sender.sendMessage(Text.literal("§cCe joueur n'est plus connecté."), false);
            PrivateMsgManager.remove(sender.getUuid());
            return 0;
        }

        PrivateMsgManager.setLastConversation(sender.getUuid(), target.getUuid());
        PrivateMsgManager.setLastConversation(target.getUuid(), sender.getUuid());

        Text toSender = Text.literal("§d[MSG] §fVous → §e" + target.getGameProfile().getName() + "§7: §f" + message);
        Text toTarget = Text.literal("§d[MSG] §e" + sender.getGameProfile().getName() + "§7 → §fVous§7: §f" + message);

        sender.sendMessage(toSender, false);
        target.sendMessage(toTarget, false);
        return 1;
    }
}
