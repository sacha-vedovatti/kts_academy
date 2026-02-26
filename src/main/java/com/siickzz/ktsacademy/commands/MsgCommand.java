package com.siickzz.ktsacademy.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.siickzz.ktsacademy.messages.PrivateMsgManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MsgCommand {
    private MsgCommand() {}

    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(
                    ctx.getSource().getServer().getPlayerManager().getPlayerList()
                            .stream()
                            .map(p -> p.getGameProfile().getName()),
                    builder
            );

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("msg").then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGESTIONS)
                .then(argument("message", StringArgumentType.greedyString()).executes(MsgCommand::sendMessage))));
            dispatcher.register(literal("w").then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGESTIONS)
                .then(argument("message", StringArgumentType.greedyString()).executes(MsgCommand::sendMessage))));
        });
    }

    private static int sendMessage(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(ctx, "player");
        String message = StringArgumentType.getString(ctx, "message");
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(Text.literal("§cJoueur introuvable : §e" + targetName), false);
            return 0;
        }
        if (target.equals(sender)) {
            sender.sendMessage(Text.literal("§cVous ne pouvez pas vous envoyer un message à vous-même."), false);
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
