/*
** EPITECH PROJECT, 2026
** Economy
** File description:
** Shop command file
*/

package com.siickzz.economy.commands;

import com.siickzz.economy.economy.EconomyManager;
import com.siickzz.economy.shop.gui.ShopGui;
import com.siickzz.economy.shop.ShopItem;
import com.siickzz.economy.shop.ShopRegistry;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ShopCommand {
    private ShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("balance").executes(ShopCommand::balance));
            dispatcher.register(
                literal("money")
                    .executes(ShopCommand::balance)
                    .then(
                        literal("set")
                            .requires(src -> src.hasPermissionLevel(3))
                            .then(
                                argument("player", EntityArgumentType.player())
                                    .then(argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(ShopCommand::moneySetOther))
                            )
                            .then(
                                argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(ShopCommand::moneySetSelf)
                            )
                    )
                    .then(
                        literal("add")
                            .requires(src -> src.hasPermissionLevel(3))
                            .then(
                                argument("player", EntityArgumentType.player())
                                    .then(argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(ShopCommand::moneyAddOther))
                            )
                            .then(
                                argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(ShopCommand::moneyAddSelf)
                            )
                    )
            );
            dispatcher.register(
                literal("shop")
                    .executes(ShopCommand::open)
                    .then(literal("list").executes(ShopCommand::list))
                    .then(
                        literal("buy")
                            .then(
                                argument("item", StringArgumentType.word())
                                    .suggests((ctx, builder) -> CommandSource.suggestMatching(ShopRegistry.keys(), builder))
                                    .executes(ctx -> buy(ctx, 1))
                                    .then(
                                        argument("amount", IntegerArgumentType.integer(1, 64))
                                            .executes(ctx -> buy(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
                                    )
                            )
                    )
            );
        });
    }

    private static int balance(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        double bal = EconomyManager.get(player).getBalance();
        player.sendMessage(Text.literal("§6💰 Balance: §e" + formatMoney(bal) + " ₽"), false);
        return 1;
    }

    private static int moneySetOther(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        EconomyManager.get(target).setBalance(amount);
        EconomyManager.save();

        ctx.getSource().sendFeedback(
            () -> Text.literal("§aMoney set: §f" + target.getName().getString() + " §7= §e" + formatMoney(amount) + " ₽"),
            true
        );
        target.sendMessage(Text.literal("§aTon argent a été défini à §e" + formatMoney(amount) + " ₽"), false);
        return 1;
    }

    private static int moneyAddOther(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        EconomyManager.get(target).add(amount);
        EconomyManager.save();

        double newBal = EconomyManager.get(target).getBalance();
        ctx.getSource().sendFeedback(
            () -> Text.literal("§aMoney add: §f" + target.getName().getString() + " §7+ §e" + formatMoney(amount) + " ₽ §7(= §e" + formatMoney(newBal) + " ₽§7)"),
            true
        );
        target.sendMessage(Text.literal("§a+" + formatMoney(amount) + " ₽ §7(Nouveau solde: §e" + formatMoney(newBal) + " ₽§7)"), false);
        return 1;
    }

    private static int moneySetSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        EconomyManager.get(self).setBalance(amount);
        EconomyManager.save();
        self.sendMessage(Text.literal("§aTon argent a été défini à §e" + formatMoney(amount) + " ₽"), false);
        return 1;
    }

    private static int moneyAddSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        EconomyManager.get(self).add(amount);
        EconomyManager.save();
        double newBal = EconomyManager.get(self).getBalance();
        self.sendMessage(Text.literal("§a+" + formatMoney(amount) + " ₽ §7(Nouveau solde: §e" + formatMoney(newBal) + " ₽§7)"), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        player.sendMessage(Text.literal("§a--- Shop ---"), false);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (ShopItem item : ShopRegistry.allItemsSorted()) {
            if (!seen.add(item.displayName().toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            String suffix = item.amount() > 1 ? " §8(x" + item.amount() + ")" : "";
            player.sendMessage(Text.literal("§7- §b" + item.displayName() + suffix + "§7 : §e" + formatMoney(item.buyPrice()) + " ₽"), false);
        }
        player.sendMessage(Text.literal("§7GUI: §f/shop"), false);
        player.sendMessage(Text.literal("§7Achat: §f/shop buy <item> [amount]"), false);
        return 1;
    }

    private static int open(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ShopGui.openShop(player);
        return 1;
    }

    private static int buy(CommandContext<ServerCommandSource> ctx, int amount) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String key = StringArgumentType.getString(ctx, "item");
        ShopItem shopItem = ShopRegistry.get(key);
        if (shopItem == null) {
            player.sendMessage(Text.literal("§cItem inconnu: §f" + key), false);
            return 0;
        }

        double total = shopItem.buyPrice() * amount;
        var account = EconomyManager.get(player);
        if (!account.remove(total)) {
            player.sendMessage(Text.literal("§cFonds insuffisants. Coût: §e" + formatMoney(total) + " ₽"), false);
            return 0;
        }

        int perUnit = Math.max(1, shopItem.amount());
        int totalGive = amount * perUnit;

        if (shopItem.giveCommand() != null && !shopItem.giveCommand().isBlank()) {
            String cmd = shopItem.giveCommand()
                .replace("{player}", player.getName().getString())
                .replace("{count}", Integer.toString(totalGive));
            player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), cmd);
        } else {
            int remaining = totalGive;
            while (remaining > 0) {
                int stackSize = Math.min(64, remaining);
                ItemStack stack = new ItemStack(shopItem.item(), stackSize);
                boolean inserted = player.getInventory().insertStack(stack);
                if (!inserted && !stack.isEmpty()) {
                    player.dropItem(stack, false);
                }
                remaining -= stackSize;
            }
        }

        player.sendMessage(Text.literal("§aAchat réussi: §f" + key + " x" + totalGive + " §7(-" + formatMoney(total) + " ₽)"), false);
        return 1;
    }

    private static String formatMoney(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
