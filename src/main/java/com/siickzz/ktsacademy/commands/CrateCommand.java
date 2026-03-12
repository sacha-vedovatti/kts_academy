package com.siickzz.ktsacademy.commands;

import com.mojang.brigadier.context.CommandContext;
import com.siickzz.ktsacademy.mystery.MysteryChestManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class CrateCommand {
    private CrateCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("crate")
                    .then(literal("chest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> give(ctx, "trial")))
                    .then(literal("enderchest").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> give(ctx, "ominous")))
                    .then(literal("key").requires(src -> src.hasPermissionLevel(2))
                        .then(literal("trial").executes(ctx -> giveKey(ctx, "trial")))
                        .then(literal("ominous").executes(ctx -> giveKey(ctx, "ominous")))
                    )
                    .then(
                        literal("give")
                            .requires(src -> src.hasPermissionLevel(2))
                            .then(literal("trial").executes(ctx -> give(ctx, "trial")))
                            .then(literal("ominous").executes(ctx -> give(ctx, "ominous")))
                            .then(literal("chest").executes(ctx -> give(ctx, "trial")))
                            .then(literal("enderchest").executes(ctx -> give(ctx, "ominous")))
                            .then(literal("key").then(literal("trial").executes(ctx -> giveKey(ctx, "trial"))))
                            .then(literal("key").then(literal("ominous").executes(ctx -> giveKey(ctx, "ominous"))))
                    )
            );
        });
    }

    private static int give(CommandContext<ServerCommandSource> ctx, String chestId) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ItemStack stack = MysteryChestManager.createChestItem(chestId);

        if (stack.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("§d§lKTS Academy §7» §cCoffre introuvable: " + chestId), false);
            return 0;
        }
        if (!player.getInventory().insertStack(stack))
            player.dropItem(stack, false);
        ctx.getSource().sendFeedback(() -> Text.literal("§d§lKTS Academy §7» §aYou received a §f" + chestId + " §achest."), false);
        return 1;
    }

    private static int giveKey(CommandContext<ServerCommandSource> ctx, String chestId) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ItemStack stack = MysteryChestManager.createKeyItem(chestId);

        if (stack.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("§d§lKTS Academy §7» §cClé introuvable: " + chestId), false);
            return 0;
        }
        if (!player.getInventory().insertStack(stack))
            player.dropItem(stack, false);
        ctx.getSource().sendFeedback(() -> Text.literal("§d§lKTS Academy §7» §aYou received a §f" + chestId + " §akey."), false);
        return 1;
    }
}
