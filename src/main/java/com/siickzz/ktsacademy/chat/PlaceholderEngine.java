package com.siickzz.ktsacademy.chat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

interface PlaceholderEngine {
    Text apply(Text text, ServerPlayerEntity player);
}

