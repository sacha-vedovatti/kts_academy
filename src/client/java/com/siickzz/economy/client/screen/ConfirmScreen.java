package com.siickzz.economy.client.screen;

import com.siickzz.economy.screen.ConfirmScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ConfirmScreen extends HandledScreen<ConfirmScreenHandler> {
	private static final Identifier TEXTURE = Identifier.of("minecraft", "textures/gui/container/generic_54.png");
	private static final int ROWS = 1;

	public ConfirmScreen(ConfirmScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 114 + ROWS * 18;
		this.playerInventoryTitleY = this.backgroundHeight - 94;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.backgroundWidth) / 2;
		int y = (this.height - this.backgroundHeight) / 2;

		int containerHeight = 17 + ROWS * 18;
		context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, containerHeight);
		context.drawTexture(TEXTURE, x, y + containerHeight, 0, 126, this.backgroundWidth, 96);
	}
}
