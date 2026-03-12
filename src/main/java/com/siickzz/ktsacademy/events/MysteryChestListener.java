package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.mystery.MysteryChestConfig;
import com.siickzz.ktsacademy.mystery.MysteryChestManager;
import com.siickzz.ktsacademy.mystery.MysteryChestGui;
import com.siickzz.ktsacademy.mystery.MysteryChestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.inventory.SimpleInventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MysteryChestListener {
    private static final Map<UUID, PendingRoll> PENDING_ROLLS = new ConcurrentHashMap<>();
    private static volatile boolean TICK_HANDLER_REGISTERED = false;
    private static final int ANIMATION_TICKS = 30;

    private MysteryChestListener() {}

    public static void register()
    {
        MysteryChestManager.init();
        ensureTickHandlerRegistered();
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld)
                MysteryChestState.get(serverWorld).remove(pos);
            return true;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world == null || world.isClient)
                return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer))
                return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            String crateId = MysteryChestManager.crateIdFromBlockEntity(world.getBlockEntity(pos));
            if (crateId == null && world instanceof ServerWorld serverWorld)
                crateId = MysteryChestState.get(serverWorld).get(pos);
            if (crateId == null)
                return ActionResult.PASS;

            ItemStack held = serverPlayer.getStackInHand(hand);
            MysteryChestConfig.ChestConfig chest = MysteryChestManager.matchChest(crateId, world.getBlockState(pos));
            if (chest == null)
                return ActionResult.PASS;

            if (!MysteryChestManager.isValidKey(chest, held)) {
                if (chest.notifyPlayer) {
                    List<ItemStack> preview = MysteryChestManager.previewItems(chest, 54);
                    Text title = Text.literal(chest.displayName != null && !chest.displayName.isBlank() ? chest.displayName : "Mystery Box");
                    MysteryChestGui.openPreview(serverPlayer, title, preview);
                }
                return ActionResult.SUCCESS;
            }

            if (PENDING_ROLLS.containsKey(serverPlayer.getUuid())) {
                if (chest.notifyPlayer)
                    serverPlayer.sendMessage(Text.literal("§d§lMystery Box §7» §cTirage deja en cours."), false);
                return ActionResult.SUCCESS;
            }

            if (!serverPlayer.getAbilities().creativeMode)
                held.decrement(1);

            if (chest.notifyPlayer)
                serverPlayer.sendMessage(Text.literal("§7Tirage en cours..."), true);

            SimpleInventory inventory = MysteryChestGui.openAnimation(serverPlayer, Text.literal("Tirage en cours"), 3);
            List<ItemStack> displayItems = MysteryChestManager.previewItems(chest, 64);
            PENDING_ROLLS.put(serverPlayer.getUuid(), new PendingRoll(chest, ANIMATION_TICKS, inventory, displayItems));
            return ActionResult.SUCCESS;
        });
    }

    private static void ensureTickHandlerRegistered()
    {
        if (TICK_HANDLER_REGISTERED)
            return;
        synchronized (MysteryChestListener.class) {
            if (TICK_HANDLER_REGISTERED)
                return;
            ServerTickEvents.END_SERVER_TICK.register(MysteryChestListener::onEndServerTick);
            TICK_HANDLER_REGISTERED = true;
        }
    }

    private static void onEndServerTick(MinecraftServer server)
    {
        if (PENDING_ROLLS.isEmpty())
            return;

        for (var it = PENDING_ROLLS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            PendingRoll pending = entry.getValue();
            int left = pending.ticksLeft - 1;
            if (left > 0) {
                pending.ticksLeft = left;
                updateAnimationSlots(pending);
                continue;
            }

            it.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null)
                continue;

            List<MysteryChestManager.Reward> loot = MysteryChestManager.rollLoot(pending.chest);
            if (loot.isEmpty()) {
                if (pending.chest.notifyPlayer)
                    player.sendMessage(Text.literal("§d§lMystery Box §7» §cAucun loot configure."), false);
                continue;
            }
            MysteryChestManager.Reward reward = loot.get(0);
            ItemStack rewardStack = reward.stack();
            if (rewardStack == null || rewardStack.isEmpty())
                continue;
            ItemStack display = rewardStack.copy();
            showFinalReward(pending, display);

            boolean givenByCommand = false;
            MysteryChestConfig.DropEntryConfig dropEntry = reward.entry();
            if (dropEntry != null && dropEntry.giveCommand != null && !dropEntry.giveCommand.isBlank()) {
                String cmd = dropEntry.giveCommand
                    .replace("{player}", player.getName().getString())
                    .replace("{count}", Integer.toString(rewardStack.getCount()));
                player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource(), cmd);
                givenByCommand = true;
            }

            if (!givenByCommand) {
                if (!player.getInventory().insertStack(rewardStack) && pending.chest.dropOnGroundIfFull && !rewardStack.isEmpty())
                    player.dropItem(rewardStack, false);
            }
            if (pending.chest.notifyPlayer)
                player.sendMessage(Text.literal("§d§lMystery Box §7» §b" + display.getName().getString() + " §7x" + display.getCount()), false);
        }
    }

    private static void updateAnimationSlots(PendingRoll pending)
    {
        if (pending.inventory == null || pending.displayItems.isEmpty())
            return;

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < slots.length - 1; i++) {
            ItemStack next = pending.inventory.getStack(slots[i + 1]);
            pending.inventory.setStack(slots[i], next);
        }
        ItemStack random = pending.displayItems.get(pending.random.nextInt(pending.displayItems.size())).copy();
        pending.inventory.setStack(slots[slots.length - 1], random);
    }

    private static void showFinalReward(PendingRoll pending, ItemStack reward)
    {
        if (pending.inventory == null)
            return;
        for (int i = 0; i < pending.inventory.size(); i++)
            pending.inventory.setStack(i, ItemStack.EMPTY);
        pending.inventory.setStack(13, reward.copy());
    }

    private static final class PendingRoll {
        private final MysteryChestConfig.ChestConfig chest;
        private int ticksLeft;
        private final SimpleInventory inventory;
        private final List<ItemStack> displayItems;
        private final java.util.Random random = new java.util.Random();

        private PendingRoll(MysteryChestConfig.ChestConfig chest, int ticksLeft, SimpleInventory inventory, List<ItemStack> displayItems) {
            this.chest = chest;
            this.ticksLeft = ticksLeft;
            this.inventory = inventory;
            this.displayItems = displayItems != null ? displayItems : List.of();
        }
    }
}
