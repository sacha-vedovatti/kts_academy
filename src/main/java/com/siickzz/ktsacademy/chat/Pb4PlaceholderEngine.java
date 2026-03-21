package com.siickzz.ktsacademy.chat;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

final class Pb4PlaceholderEngine implements PlaceholderEngine {
    @Override
    public Text apply(Text text, ServerPlayerEntity player) {
        if (text == null) {
            return Text.empty();
        }
        if (player == null) {
            return text;
        }
        return Placeholders.parseText(text, PlaceholderContext.of(player));
    }
}

