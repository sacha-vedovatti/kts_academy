package com.siickzz.ktsacademy.mystery;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class MysteryChestGui {
    private MysteryChestGui() {}

    public static SimpleInventory openPreview(ServerPlayerEntity player, Text title, List<ItemStack> items)
    {
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(items.size() / 9.0)));
        SimpleInventory inventory = new SimpleInventory(rows * 9);

        for (int i = 0; i < items.size() && i < inventory.size(); i++)
            inventory.setStack(i, items.get(i));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, playerEntity) -> new ReadOnlyContainer(handlerType(rows), syncId, playerInv, inventory, rows), title));
        return inventory;
    }

    public static SimpleInventory openAnimation(ServerPlayerEntity player, Text title, int rows)
    {
        SimpleInventory inventory = new SimpleInventory(rows * 9);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, playerEntity) -> new ReadOnlyContainer(handlerType(rows), syncId, playerInv, inventory, rows), title));
        return inventory;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> handlerType(int rows)
    {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    private static final class ReadOnlyContainer extends GenericContainerScreenHandler {
        private final SimpleInventory inv;

        private ReadOnlyContainer(ScreenHandlerType<?> type, int syncId, net.minecraft.entity.player.PlayerInventory playerInv, SimpleInventory inventory, int rows)
        {
            super(type, syncId, playerInv, inventory, rows);
            this.inv = inventory;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {}

        @Override
        public void onClosed(PlayerEntity player)
        {
            super.onClosed(player);
            for (int i = 0; i < this.inv.size(); i++)
                this.inv.setStack(i, ItemStack.EMPTY);
        }
    }
}
