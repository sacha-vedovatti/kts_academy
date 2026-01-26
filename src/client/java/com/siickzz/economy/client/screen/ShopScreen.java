package com.siickzz.economy.client.screen;

import com.siickzz.economy.screen.ShopScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ShopScreen extends HandledScreen<ShopScreenHandler> {
	private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/gui/container/generic_54.png");

	public ShopScreen(ShopScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 114 + ShopScreenHandler.ROWS * 18;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.backgroundWidth) / 2;
		int y = (this.height - this.backgroundHeight) / 2;

		int rows = ShopScreenHandler.ROWS;
		int containerHeight = 17 + rows * 18;
		context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, containerHeight);
		context.drawTexture(TEXTURE, x, y + containerHeight, 0, 126, this.backgroundWidth, 96);
	}
}
