package com.siickzz.ktsacademy.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.siickzz.ktsacademy.messages.PrivateMsgManager;
import com.siickzz.ktsacademy.mixin.CommandNodeAccessor;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.argument.EntityArgumentType;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class MsgCommand {
    private MsgCommand() {}

    private static final Logger LOGGER = LogManager.getLogger("KTSAcademy-MsgDebug");

    private static final Set<CommandNode<ServerCommandSource>> WRAPPED_NODES =
            Collections.newSetFromMap(new IdentityHashMap<>());

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
            String[] aliases = {"msg", "w", "tell", "whisper", "m", "pm"};
            for (String alias : aliases) {
                try {
                    dispatcher.register(literal(alias)
                            .then(argument("player", StringArgumentType.word()).suggests(PLAYER_SUGGESTIONS)
                            .then(argument("message", StringArgumentType.greedyString()).executes(MsgCommand::sendMessage))));
                } catch (Exception ignored) {
                    // Un autre mod a déjà enregistré cet alias.
                }
            }

            wrapExistingCommands(dispatcher);
        });
    }

    public static void wrapExistingCommands(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher)
    {
        wrapExistingCommand(dispatcher, "msg");
        wrapExistingCommand(dispatcher, "tell");
        wrapExistingCommand(dispatcher, "w");
        wrapExistingCommand(dispatcher, "whisper");
        wrapExistingCommand(dispatcher, "message");
    }

    private static void wrapExistingCommand(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher, String name)
    {
        CommandNode<ServerCommandSource> node = dispatcher.getRoot().getChild(name);

        if (node == null)
            return;
        wrapExecutableNodes(node);
    }

    private static void wrapExecutableNodes(CommandNode<ServerCommandSource> node)
    {
        if (node == null)
            return;
        if (node.getCommand() != null && WRAPPED_NODES.add(node)) {
            CommandNodeAccessor<ServerCommandSource> accessor = (CommandNodeAccessor<ServerCommandSource>) node;
            Command<ServerCommandSource> original = accessor.getCommand();

            if (original != null) {
                accessor.setCommand(ctx -> {
                    int result = original.run(ctx);
                    recordConversationFromContext(ctx);
                    return result;
                });
            }
        }

        CommandNode<ServerCommandSource> redirect = node.getRedirect();
        if (redirect != null)
            wrapExecutableNodes(redirect);
        for (CommandNode<ServerCommandSource> child : node.getChildren())
            wrapExecutableNodes(child);
    }

    private static void recordConversationFromContext(CommandContext<ServerCommandSource> ctx)
    {
        ServerPlayerEntity sender;

        try {
            sender = ctx.getSource().getPlayerOrThrow();
        } catch (CommandSyntaxException e) {
            LOGGER.info("[reply-debug] sender not a player; input='{}'", ctx.getInput());
            return;
        }

        Collection<ServerPlayerEntity> targets = resolveTargets(ctx);
        if (targets.isEmpty()) {
            LOGGER.info("[reply-debug] no targets resolved; sender='{}' input='{}'", sender.getGameProfile().getName(), ctx.getInput());
            return;
        }

        for (ServerPlayerEntity target : targets) {
            if (!target.equals(sender)) {
                PrivateMsgManager.recordConversation(sender.getUuid(), target.getUuid());
                LOGGER.info("[reply-debug] conversation recorded; sender='{}' target='{}' input='{}'",
                        sender.getGameProfile().getName(), target.getGameProfile().getName(), ctx.getInput());
                break;
            }
        }
    }

    private static Collection<ServerPlayerEntity> resolveTargets(CommandContext<ServerCommandSource> ctx)
    {
        try {
            return EntityArgumentType.getPlayers(ctx, "targets");
        } catch (Exception ignored) {
            // ignore
        }
        try {
            return EntityArgumentType.getPlayers(ctx, "target");
        } catch (Exception ignored) {
            // ignore
        }
        try {
            return EntityArgumentType.getPlayers(ctx, "player");
        } catch (Exception ignored) {
            // ignore
        }

        Collection<ServerPlayerEntity> byName = resolveTargetsByName(ctx);
        if (!byName.isEmpty()) {
            return byName;
        }

        Collection<ServerPlayerEntity> byInput = resolveTargetsByInput(ctx);
        if (!byInput.isEmpty()) {
            return byInput;
        }

        return java.util.List.of();
    }

    private static Collection<ServerPlayerEntity> resolveTargetsByName(CommandContext<ServerCommandSource> ctx)
    {
        String[] names = {"player", "target", "targets", "recipient"};
        for (String name : names) {
            try {
                String targetName = StringArgumentType.getString(ctx, name);
                ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
                if (target != null) {
                    return java.util.List.of(target);
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return java.util.List.of();
    }

    private static Collection<ServerPlayerEntity> resolveTargetsByInput(CommandContext<ServerCommandSource> ctx)
    {
        String input = ctx.getInput();
        if (input == null || input.isBlank()) {
            return java.util.List.of();
        }

        String trimmed = input.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }

        String[] parts = trimmed.split("\\s+", 3);
        if (parts.length < 2) {
            return java.util.List.of();
        }

        String targetName = parts[1];
        ServerPlayerEntity target = ctx.getSource().getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            return java.util.List.of();
        }

        return java.util.List.of(target);
    }

    public static int sendMessage(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException
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

        PrivateMsgManager.recordConversation(sender.getUuid(), target.getUuid());

        Text toSender = Text.literal("§d[MSG] §fVous → §e" + target.getGameProfile().getName() + "§7: §f" + message);
        Text toTarget = Text.literal("§d[MSG] §e" + sender.getGameProfile().getName() + "§7 → §fVous§7: §f" + message);

        sender.sendMessage(toSender, false);
        target.sendMessage(toTarget, false);
        return 1;
    }
}
